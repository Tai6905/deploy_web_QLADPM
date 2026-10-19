import { test, afterEach } from "node:test";
import assert from "node:assert/strict";
import { loadSource } from "./load-source.mjs";
const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });
function client() {
  globalThis.document = { cookie: "" };
  globalThis.window = new EventTarget();
  return loadSource("src/api/httpClient.ts");
}
const json = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { "Content-Type": "application/json" } });

test("protected writes acquire a CSRF token before sending credentials", async () => {
  const { apiRequest } = client();
  const calls = [];
  globalThis.fetch = async (url, init) => {
    calls.push([url, init]);
    return url.endsWith("/session") ? json({ authenticated: false, csrfToken: "raw-token" }) : json({ id: 1 });
  };
  await apiRequest("/api/employees", { method: "POST", body: "{}" });
  assert.equal(calls[0][0], "/api/auth/session");
  assert.equal(calls[1][1].headers.get("X-XSRF-TOKEN"), "raw-token");
  assert.equal(calls[1][1].credentials, "include");
});
test("empty successful responses do not produce JSON parse failures", async () => {
  const { apiRequest } = client();
  globalThis.fetch = async (url) => url.endsWith("/session") ? json({ csrfToken: "token" }) : new Response(null, { status: 200 });
  assert.equal(await apiRequest("/inventory/products/1/threshold", { method: "PUT", body: "{}" }), undefined);
});
test("parallel writes share the initial token request", async () => {
  const { apiRequest } = client();
  let requests = 0;
  globalThis.fetch = async (url) => {
    if (url.endsWith("/session")) { requests++; return json({ csrfToken: "token" }); }
    return new Response(null, { status: 204 });
  };
  await Promise.all([apiRequest("/a", { method: "POST" }), apiRequest("/b", { method: "POST" })]);
  assert.equal(requests, 1);
});
test("expired sessions notify the router and preserve the server message", async () => {
  const { apiRequest } = client();
  let expired = false;
  window.addEventListener("shopflow:session-expired", () => { expired = true; });
  globalThis.fetch = async () => json({ message: "Session expired" }, 401);
  await assert.rejects(apiRequest("/orders"), (error) => error.status === 401 && error.message === "Session expired");
  assert.equal(expired, true);
});
test("wrong proxy HTML and offline backend have readable errors", async () => {
  const { apiRequest } = client();
  globalThis.fetch = async () => new Response("<!doctype html>");
  await assert.rejects(apiRequest("/products"), /proxy/);
  globalThis.fetch = async () => { throw new TypeError("fetch failed"); };
  await assert.rejects(apiRequest("/products"), (error) => error.status === 0);
});
