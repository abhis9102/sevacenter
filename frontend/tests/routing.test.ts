import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { safeReturnTo } from "../src/lib/returnTo";
import { slugFromHost } from "../src/lib/tenant";
import { downstreamResponseHeaders, upstreamPath, upstreamRequestHeaders } from "../src/lib/upstream";

describe("safeReturnTo (no open redirects)", () => {
  it("keeps same-origin relative paths", () => {
    assert.equal(safeReturnTo("/staff"), "/staff");
    assert.equal(safeReturnTo("/devotees/12?created=1"), "/devotees/12?created=1");
  });
  for (const evil of [
    "https://evil.example/",
    "//evil.example",
    "/\\evil.example",
    "\\\\evil.example",
    "/\t/evil.example",
    "javascript:alert(1)",
    "evil.example",
    "",
    "/login",
    "/login?next=/x",
    "/setup",
    "/api/v1/me",
  ]) {
    it(`rejects ${JSON.stringify(evil)}`, () => {
      assert.equal(safeReturnTo(evil), "/devotees");
    });
  }
  it("rejects null", () => {
    assert.equal(safeReturnTo(null), "/devotees");
  });
});

describe("slugFromHost", () => {
  it("reads the tenant from <slug>.localhost[:port] and <slug>.sevacenter.app", () => {
    assert.equal(slugFromHost("siddheshwar.localhost:3000"), "siddheshwar");
    assert.equal(slugFromHost("Siddheshwar.LOCALHOST"), "siddheshwar");
    assert.equal(slugFromHost("shri-ram.sevacenter.app"), "shri-ram");
  });
  it("resolves no tenant for anything else", () => {
    for (const host of [
      null,
      "localhost:3000",
      "sevacenter.app",
      "a.b.localhost",
      "siddheshwar.attacker.example",
      "siddheshwar.sevacenter.app.attacker.example",
      "-bad.localhost",
      "x.localhost:abc",
      "[::1]:3000",
    ]) {
      assert.equal(slugFromHost(host), null, String(host));
    }
  });
});

describe("API proxy headers", () => {
  it("replaces a client-supplied tenant header with the Host's slug", () => {
    const incoming = new Headers({ "x-tenant-slug": "other-temple", cookie: "SC_SESSION=s", "x-xsrf-token": "t" });
    const out = upstreamRequestHeaders(incoming, "siddheshwar");
    assert.equal(out.get("x-tenant-slug"), "siddheshwar");
    assert.equal(out.get("cookie"), "SC_SESSION=s");
    assert.equal(out.get("x-xsrf-token"), "t");
  });
  it("drops a client-supplied tenant header when the Host names no tenant", () => {
    const out = upstreamRequestHeaders(new Headers({ "x-tenant-slug": "other-temple" }), null);
    assert.equal(out.get("x-tenant-slug"), null);
  });
  it("forwards only allowlisted request headers", () => {
    const out = upstreamRequestHeaders(
      new Headers({ "x-forwarded-for": "1.2.3.4", authorization: "Basic x", host: "evil", accept: "application/json" }),
      "a",
    );
    assert.equal(out.get("x-forwarded-for"), null);
    assert.equal(out.get("authorization"), null);
    assert.equal(out.get("host"), null);
    assert.equal(out.get("accept"), "application/json");
  });
  it("keeps each Set-Cookie separate on the way back", () => {
    const up = new Headers();
    up.append("set-cookie", "SC_SESSION=a; Path=/; HttpOnly");
    up.append("set-cookie", "XSRF-TOKEN=b; Path=/");
    up.set("content-type", "application/json");
    up.set("x-internal", "nope");
    const out = downstreamResponseHeaders(up);
    assert.deepEqual(out.getSetCookie(), ["SC_SESSION=a; Path=/; HttpOnly", "XSRF-TOKEN=b; Path=/"]);
    assert.equal(out.get("x-internal"), null);
  });
  it("forwards only /api/v1 paths, without traversal", () => {
    assert.equal(upstreamPath("/api/v1/devotees/12"), "/api/v1/devotees/12");
    assert.equal(upstreamPath("/api/v1/../actuator/env"), null);
    assert.equal(upstreamPath("/api/v2/x"), null);
    assert.equal(upstreamPath("/api/v1/a%2Fb"), null);
    assert.equal(upstreamPath("/api/v1"), null);
  });
});
