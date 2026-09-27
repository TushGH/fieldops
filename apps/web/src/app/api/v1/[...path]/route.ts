// Keep browser requests same-origin; Spring remains the session and access authority.
const routes = new Map([
  ["auth/csrf", "GET"], ["auth/me", "GET"], ["auth/login", "POST"],
  ["auth/logout", "POST"], ["onboarding", "POST"], ["businesses", "GET"],
  ["tenant", "GET"], ["tenant/context", "GET"],
]);

type Context = { params: Promise<{ path: string[] }> };

async function proxy(request: Request, context: Context) {
  const path = (await context.params).path.join("/");
  if (routes.get(path) !== request.method) {
    return Response.json({ code: "NOT_FOUND" }, { status: 404 });
  }
  // Next can normalize request.url to its internal hostname. Host is the
  // browser-facing authority; do not accept client-supplied forwarded-host.
  const requestUrl = new URL(request.url);
  const publicOrigin = `${requestUrl.protocol}//${request.headers.get("host") ?? requestUrl.host}`;
  if (request.method === "POST" && request.headers.has("origin")
      && request.headers.get("origin") !== publicOrigin) {
    return Response.json({ code: "REQUEST_REJECTED" }, { status: 403 });
  }
  const headers = new Headers();
  for (const name of ["content-type", "x-csrf-token", "x-tenant-id"]) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  const session = request.headers.get("cookie")?.split(";")
    .map(value => value.trim()).filter(value => value.startsWith("FIELDOPS_SESSION="));
  if (session?.length) headers.set("cookie", session.join("; "));
  try {
    const upstream = await fetch(new URL(`/api/v1/${path}`, process.env.API_BASE_URL ?? "http://127.0.0.1:8080"), {
      method: request.method, headers, cache: "no-store", redirect: "manual",
      body: request.method === "POST" ? await request.text() : undefined,
      signal: AbortSignal.timeout(15000),
    });
    const responseHeaders = new Headers({ "cache-control": "no-store" });
    const contentType = upstream.headers.get("content-type");
    if (contentType) responseHeaders.set("content-type", contentType);
    for (const cookie of upstream.headers.getSetCookie()) responseHeaders.append("set-cookie", cookie);
    return new Response(upstream.body, { status: upstream.status, headers: responseHeaders });
  } catch {
    return Response.json({ code: "UNAVAILABLE", message: "We could not reach FieldOps. Please try again." },
      { status: 503, headers: { "cache-control": "no-store" } });
  }
}

export const GET = proxy;
export const POST = proxy;
