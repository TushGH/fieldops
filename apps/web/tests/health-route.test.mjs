import assert from "node:assert/strict";
import { test } from "node:test";
import { GET } from "../src/app/api/health/route.ts";

test("forwards the expected health contract without leaking extra backend data", async (t) => {
  const originalUrl = process.env.API_BASE_URL;
  process.env.API_BASE_URL = "http://127.0.0.1:9876";
  t.after(() => {
    if (originalUrl === undefined) delete process.env.API_BASE_URL;
    else process.env.API_BASE_URL = originalUrl;
  });
  const fetchMock = t.mock.method(globalThis, "fetch", async () => Response.json({
    application: "fieldops-api", status: "UP", internalDetail: "must not escape",
  }));

  const response = await GET();

  assert.equal(response.status, 200);
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.deepEqual(await response.json(), { application: "fieldops-api", status: "UP" });
  const [url, options] = fetchMock.mock.calls[0].arguments;
  assert.equal(url.toString(), "http://127.0.0.1:9876/api/v1/health");
  assert.equal(options.cache, "no-store");
  assert.ok(options.signal instanceof AbortSignal);
});

for (const [name, reply] of [
  ["backend HTTP failure", () => Response.json({ status: "DOWN" }, { status: 500 })],
  ["unexpected payload", () => Response.json({ status: "UP" })],
  ["malformed JSON", () => new Response("not JSON")],
  ["connection failure", () => { throw new TypeError("private upstream connection details"); }],
  ["request timeout", () => { throw new DOMException("request timed out", "TimeoutError"); }],
]) {
  test(`returns a safe, uncached 503 for ${name}`, async (t) => {
    t.mock.method(globalThis, "fetch", async () => reply());

    const response = await GET();

    assert.equal(response.status, 503);
    assert.equal(response.headers.get("cache-control"), "no-store");
    assert.deepEqual(await response.json(), { status: "UNAVAILABLE" });
  });
}
