const uuid = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
const routes: [string, RegExp][] = [
  ["GET", /^(auth\/(csrf|me)|businesses|invitations|tenant\/(context|onboarding|invitations|memberships)|tenant|platform\/access)$/],
  ["GET", new RegExp(`^invitations/${uuid}$`)],
  ["POST", /^(auth\/(login|logout|signup|signup\/complete|email-verification\/(request|confirm))|businesses|invitations\/resolve|tenant\/invitations|tenant\/onboarding\/business\/complete|platform\/businesses)$/],
  ["POST", new RegExp(`^(invitations/${uuid}/accept|tenant/invitations/${uuid}/(revoke|resend)|platform/businesses/${uuid}/owner-invitation)$`)],
  ["GET", new RegExp(`^tenant/memberships/${uuid}$`)],
  ["PUT", new RegExp(`^tenant/memberships/${uuid}/role$`)],
  ["POST", new RegExp(`^tenant/memberships/${uuid}/(deactivate|reactivate)$`)],
  ["PATCH", /^tenant$/],
];

function failure(message: string, status: number) {
  return Response.json({ message }, { status, headers: { "Cache-Control": "no-store", "Referrer-Policy": "no-referrer" } });
}

function acceptsOrigin(origin: string | null, expectedOrigin: string) {
  if (origin === expectedOrigin) return true;
  if (!origin || process.env.NODE_ENV !== "development") return false;
  try {
    const actual = new URL(origin);
    const expected = new URL(expectedOrigin);
    const loopback = new Set(["localhost", "127.0.0.1", "[::1]"]);
    // Local browser aliases may differ from Next's internal request hostname.
    return actual.origin === origin && loopback.has(actual.hostname)
      && loopback.has(expected.hostname) && actual.protocol === expected.protocol
      && actual.port === expected.port;
  } catch {
    return false;
  }
}

async function proxy(request: Request, context: { params: Promise<{ path: string[] }> }) {
  const path = (await context.params).path.join("/");
  if (!routes.some(([method, pattern]) => method === request.method && pattern.test(path))) {
    return failure("Unknown API route.", 404);
  }
  const search = new URL(request.url).searchParams;
  const pagination = new URLSearchParams();
  if (path === "tenant/memberships" && request.method === "GET") {
    for (const [key, value] of search) {
      if (!["page", "size"].includes(key) || search.getAll(key).length !== 1 || !/^\d+$/.test(value)
          || !Number.isSafeInteger(Number(value)) || Number(value) > 2147483647
          || (key === "size" && (Number(value) < 1 || Number(value) > 100))) {
        return failure("Invalid membership pagination.", 400);
      }
      pagination.set(key, value);
    }
  }
  const mutation = request.method !== "GET";
  const expectedOrigin = process.env.WEB_ORIGIN ?? new URL(request.url).origin;
  if (mutation && !acceptsOrigin(request.headers.get("origin"), expectedOrigin)) {
    return failure("Request origin rejected.", 403);
  }
  const headers = new Headers();
  for (const name of ["content-type", "x-csrf-token", "x-tenant-id"]) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  const cookie = request.headers.get("cookie")?.split(";").map(value => value.trim())
    .filter(value => value.startsWith("FIELDOPS_SESSION=")).join("; ");
  if (cookie) headers.set("cookie", cookie);
  let body: Uint8Array | undefined;
  if (mutation && request.body) {
    const reader = request.body.getReader();
    const chunks: Uint8Array[] = [];
    let size = 0;
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.length;
      if (size > 16384) { await reader.cancel(); return failure("Request is too large.", 413); }
      chunks.push(value);
    }
    body = new Uint8Array(size);
    let offset = 0;
    for (const chunk of chunks) { body.set(chunk, offset); offset += chunk.length; }
  }
  try {
    const origin = process.env.API_BASE_URL ?? "http://127.0.0.1:8080";
    const upstream = await fetch(new URL(`/api/v1/${path}${pagination.size ? `?${pagination}` : ""}`, origin), {
      method: request.method, headers, body: body as BodyInit | undefined,
      cache: "no-store", redirect: "manual", signal: AbortSignal.timeout(15000),
    });
    const outgoing = new Headers({ "Cache-Control": "no-store", "Referrer-Policy": "no-referrer" });
    for (const name of ["content-type", "retry-after"]) {
      const value = upstream.headers.get(name);
      if (value) outgoing.set(name, value);
    }
    for (const value of upstream.headers.getSetCookie()) {
      if (value.startsWith("FIELDOPS_SESSION=")) outgoing.append("set-cookie", value);
    }
    return new Response(upstream.status === 204 ? null : await upstream.arrayBuffer(), {
      status: upstream.status, headers: outgoing,
    });
  } catch {
    return failure("FieldOps is temporarily unavailable. Please try again.", 503);
  }
}
export { proxy as GET, proxy as POST, proxy as PATCH, proxy as PUT };
