export type User = { id: string; displayName: string; email: string; emailVerifiedAt: string | null; platformAdmin: boolean };
export type Business = { id: string; name: string; slug: string; role: string };
export type Invitation = { id: string; tenantId: string; businessName: string; email: string; role: string; status: string; expiresAt: string };
export type Readiness = { businessComplete: boolean; businessSetupRequired: boolean; businessChecklist: string[] };
export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string) { super(message); }
}
export async function api<T>(path: string, options: { method?: string; data?: unknown; tenant?: string; signal?: AbortSignal; form?: boolean } = {}): Promise<T> {
  const headers = new Headers();
  if (options.tenant) headers.set("X-Tenant-ID", options.tenant);
  const method = options.method ?? "GET";
  if (method !== "GET") {
    const csrf = await api<{ headerName: string; token: string }>("auth/csrf", { signal: options.signal });
    headers.set(csrf.headerName, csrf.token);
    headers.set("Content-Type", options.form ? "application/x-www-form-urlencoded" : "application/json");
  }
  const response = await fetch(`/api/backend/${path}`, {
    method, headers, cache: "no-store", credentials: "same-origin", signal: options.signal,
    body: options.data === undefined ? undefined : options.form ? new URLSearchParams(options.data as Record<string, string>) : JSON.stringify(options.data),
  });
  const body = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) throw new ApiError(response.status, body?.code ?? "REQUEST_FAILED", body?.message ?? "Unable to complete the request.");
  return body as T;
}
export function setIntent(value: string) {
  if (/^request-preview:[a-z0-9-]+$/.test(value) || value === "create-business" || /^invitation:[0-9a-f-]{36}$/.test(value)) sessionStorage.setItem("fieldops:intent", value);
}
export function clearAccountState() {
  Object.keys(sessionStorage).filter(key => key.startsWith("fieldops:")).forEach(key => sessionStorage.removeItem(key));
}
export function rememberUser(id: string) {
  const previous = sessionStorage.getItem("fieldops:user");
  if (previous && previous !== id) {
    Object.keys(sessionStorage).filter(key => (key.startsWith("fieldops:tenant:") || key.startsWith("fieldops:context:"))).forEach(key => sessionStorage.removeItem(key));
  }
  sessionStorage.setItem("fieldops:user", id);
}
export async function destination(user: User, signal?: AbortSignal): Promise<string> {
  rememberUser(user.id);
  if (!user.emailVerifiedAt) return "/verify-email";
  const intent = sessionStorage.getItem("fieldops:intent");
  if (intent?.startsWith("request-preview:")) return `/my/requests/new?provider=${encodeURIComponent(intent.slice(16))}`;
  if (intent === "create-business") return "/create-business";
  if (intent?.startsWith("invitation:")) return `/invitations/accept?id=${encodeURIComponent(intent.slice(11))}`;
  const businesses = await api<Business[]>("businesses", { signal });
  if (sessionStorage.getItem(`fieldops:context:${user.id}`) === "personal") return "/my";
  const remembered = sessionStorage.getItem(`fieldops:tenant:${user.id}`);
  if (businesses.some(business => business.id === remembered)) return `/app/${remembered}`;
  if (businesses.length === 0) {
    return "/my";
  }
  return "/select-business";
}
