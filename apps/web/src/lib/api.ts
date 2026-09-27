export async function api(path: string, init: RequestInit = {}) {
  return fetch(`/api/v1/${path}`, { ...init, credentials: "same-origin", cache: "no-store" });
}

export async function submit(path: string, body?: BodyInit, contentType?: string) {
  // Tokens rotate on authentication; obtain one for each user-initiated mutation.
  const csrf = await api("auth/csrf");
  if (!csrf.ok) throw new Error("We could not reach FieldOps. Please try again.");
  const token: { headerName: string; token: string } = await csrf.json();
  return api(path, { method: "POST", body, headers: {
    [token.headerName]: token.token,
    ...(contentType ? { "Content-Type": contentType } : {}),
  } });
}
