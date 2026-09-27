import assert from "node:assert/strict";
import { test } from "node:test";
import { GET, POST } from "../src/app/api/v1/[...path]/route.ts";

const context = path => ({ params: Promise.resolve({ path: path.split("/") }) });

test("login forwards session and CSRF and preserves rotated HttpOnly cookies", async t => {
  const fetch = t.mock.method(globalThis, "fetch", async () => new Response(null, {
    status: 204, headers: { "set-cookie": "FIELDOPS_SESSION=rotated; Path=/; HttpOnly; SameSite=Lax; Secure" },
  }));
  const response = await POST(new Request("http://internal/api/v1/auth/login", {
    method: "POST", headers: { host: "localhost", cookie: "unrelated=private; FIELDOPS_SESSION=old", "x-csrf-token": "masked",
      "content-type": "application/x-www-form-urlencoded", authorization: "should-not-forward", origin: "http://localhost" },
    body: "email=test%40example.com&password=test-only",
  }), context("auth/login"));
  assert.equal(response.status, 204);
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.match(response.headers.get("set-cookie"), /rotated.*HttpOnly.*SameSite=Lax.*Secure/);
  const [url, options] = fetch.mock.calls[0].arguments;
  assert.equal(url.pathname, "/api/v1/auth/login");
  assert.equal(options.headers.get("cookie"), "FIELDOPS_SESSION=old");
  assert.equal(options.headers.get("x-csrf-token"), "masked");
  assert.equal(options.headers.get("authorization"), null);
  assert.equal(options.body, "email=test%40example.com&password=test-only");
  assert.equal(options.redirect, "manual");
  assert.equal(options.cache, "no-store");
});

test("proxy rejects unknown routes, wrong methods, and cross-origin mutations", async t => {
  const fetch = t.mock.method(globalThis, "fetch", async () => { throw Error("must not reach backend"); });
  assert.equal((await GET(new Request("http://localhost/api/v1/users"), context("users"))).status, 404);
  assert.equal((await GET(new Request("http://localhost/api/v1/auth/login"), context("auth/login"))).status, 404);
  assert.equal((await POST(new Request("http://localhost/api/v1/onboarding", {
    method: "POST", headers: { origin: "https://foreign.example" }, body: "{}",
  }), context("onboarding"))).status, 403);
  assert.equal(fetch.mock.callCount(), 0);
});

test("tenant selection is forwarded but user-controlled identity headers are not", async t => {
  const fetch = t.mock.method(globalThis, "fetch", async () => Response.json({ role: "BUSINESS_OWNER" }));
  const response = await GET(new Request("http://localhost/api/v1/tenant/context?userId=forged", {
    headers: { "x-tenant-id": "selected", "x-user-id": "forged", "x-role": "BUSINESS_OWNER" },
  }), context("tenant/context"));
  assert.equal(response.status, 200);
  const [url, options] = fetch.mock.calls[0].arguments;
  assert.equal(url.search, "");
  assert.equal(options.headers.get("x-tenant-id"), "selected");
  assert.equal(options.headers.get("x-user-id"), null);
  assert.equal(options.headers.get("x-role"), null);
});

test("signup conflicts and logout cookie expiration reach the browser", async t => {
  t.mock.method(globalThis, "fetch", async () => Response.json({ code: "ONBOARDING_CONFLICT" }, { status: 409 }));
  const response = await POST(new Request("http://localhost/api/v1/onboarding", { method: "POST", body: "{}" }), context("onboarding"));
  assert.equal(response.status, 409);
  assert.deepEqual(await response.json(), { code: "ONBOARDING_CONFLICT" });
  t.mock.restoreAll();
  t.mock.method(globalThis, "fetch", async () => new Response(null, { status: 204,
    headers: { "set-cookie": "FIELDOPS_SESSION=; Max-Age=0; Path=/" } }));
  const logout = await POST(new Request("http://localhost/api/v1/auth/logout", { method: "POST" }), context("auth/logout"));
  assert.match(logout.headers.get("set-cookie"), /Max-Age=0/);
});

test("upstream outages produce a safe retryable response", async t => {
  t.mock.method(globalThis, "fetch", async () => { throw Error("private host details"); });
  const response = await GET(new Request("http://localhost/api/v1/auth/csrf"), context("auth/csrf"));
  assert.equal(response.status, 503);
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.doesNotMatch(await response.text(), /private host/);
});
