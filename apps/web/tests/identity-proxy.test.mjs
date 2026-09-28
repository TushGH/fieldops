import assert from "node:assert/strict";
import { test } from "node:test";
import { GET, POST } from "../src/app/api/backend/[...path]/route.ts";
const params = path => ({ params: Promise.resolve({ path: path.split("/") }) });
const request = (path, options = {}) => new Request(`http://localhost:3000/api/backend/${path}`, options);

test("forwards session/CSRF/tenant headers and rotated cookies without accepting arbitrary destinations", async t => {
  const fetch = t.mock.method(globalThis, "fetch", async () => new Response(null, { status: 204, headers: { "set-cookie": "FIELDOPS_SESSION=new; Path=/; HttpOnly; SameSite=Lax" } }));
  const response = await POST(request("businesses", { method: "POST", headers: { origin: "http://localhost:3000", cookie: "unrelated=secret; FIELDOPS_SESSION=old", "x-csrf-token": "csrf", "x-tenant-id": "selected", "content-type": "application/json" }, body: '{}' }), params("businesses"));
  assert.equal(response.status, 204);
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.match(response.headers.get("set-cookie"), /FIELDOPS_SESSION=new/);
  const [url, options] = fetch.mock.calls[0].arguments;
  assert.equal(url.pathname, "/api/v1/businesses");
  assert.equal(options.headers.get("cookie"), "FIELDOPS_SESSION=old");
  assert.equal(options.headers.get("x-csrf-token"), "csrf");
  assert.equal(options.headers.get("x-tenant-id"), "selected");
  assert.equal(options.redirect, "manual");
});
test("rejects cross-origin mutation, unapproved paths, and oversized payloads before fetching", async t => {
  const fetch = t.mock.method(globalThis, "fetch", () => { throw new Error("must not forward"); });
  assert.equal((await POST(request("auth/login", { method: "POST", headers: { origin: "https://attacker.example" } }), params("auth/login"))).status, 403);
  assert.equal((await GET(request("../actuator/env"), params("../actuator/env"))).status, 404);
  assert.equal((await POST(request("businesses", { method: "POST", headers: { origin: "http://localhost:3000" }, body: 'x'.repeat(16385) }), params("businesses"))).status, 413);
  assert.equal(fetch.mock.callCount(), 0);
});
test("upstream errors do not expose network details", async t => {
  t.mock.method(globalThis, "fetch", () => { throw new Error("private host"); });
  const response = await GET(request("auth/me"), params("auth/me"));
  assert.equal(response.status, 503);
  assert.doesNotMatch(await response.text(), /private host/);
});
