import assert from "node:assert/strict";
import { test } from "node:test";
import { GET, POST } from "../src/app/api/backend/[...path]/route.ts";
const params = path => ({ params: Promise.resolve({ path: path.split("/") }) });
const request = (path, options = {}) => new Request(`http://localhost:3000/api/backend/${path}`, options);

test("uses the configured browser origin when Next resolves a different internal hostname", async t => {
  const previous = process.env.WEB_ORIGIN;
  process.env.WEB_ORIGIN = "http://127.0.0.1:3000";
  t.after(() => {
    if (previous === undefined) delete process.env.WEB_ORIGIN;
    else process.env.WEB_ORIGIN = previous;
  });
  const fetch = t.mock.method(globalThis, "fetch", async () => new Response(null, { status: 202 }));
  for (const origin of ["http://localhost:3000", "https://attacker.example", null]) {
    const headers = origin ? { origin } : {};
    assert.equal((await POST(request("auth/signup", { method: "POST", headers, body: '{}' }), params("auth/signup"))).status, 403);
  }
  assert.equal(fetch.mock.callCount(), 0);
  assert.equal((await POST(request("auth/signup", { method: "POST", headers: { origin: "http://127.0.0.1:3000" }, body: '{}' }), params("auth/signup"))).status, 202);
  assert.equal(fetch.mock.callCount(), 1);
});

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

for (const mode of ["development", "production"]) {
  test(`${mode} origin checks restrict loopback aliases to development and the same port`, async t => {
    const previousMode = process.env.NODE_ENV;
    const previousOrigin = process.env.WEB_ORIGIN;
    process.env.NODE_ENV = mode;
    process.env.WEB_ORIGIN = "http://127.0.0.1:3000";
    t.after(() => {
      if (previousMode === undefined) delete process.env.NODE_ENV;
      else process.env.NODE_ENV = previousMode;
      if (previousOrigin === undefined) delete process.env.WEB_ORIGIN;
      else process.env.WEB_ORIGIN = previousOrigin;
    });
    t.mock.method(globalThis, "fetch", async () => new Response(null, { status: 202 }));
    for (const [origin, allowed] of [
      ["http://127.0.0.1:3000", true],
      ["http://localhost:3000", mode === "development"],
      ["http://[::1]:3000", mode === "development"],
      ["http://localhost:3001", false],
      ["https://localhost:3000", false],
      ["http://localhost.attacker.example:3000", false],
      ["http://localhost:3000/path", false],
      ["null", false],
      [null, false],
    ]) {
      const headers = origin ? { origin } : {};
      const response = await POST(request("auth/signup", { method: "POST", headers, body: '{}' }), params("auth/signup"));
      assert.equal(response.status, allowed ? 202 : 403, String(origin));
    }
    process.env.WEB_ORIGIN = "https://fieldops.example";
    assert.equal((await POST(request("auth/signup", { method: "POST", headers: { origin: "https://localhost" }, body: '{}' }), params("auth/signup"))).status, 403);
  });
}
