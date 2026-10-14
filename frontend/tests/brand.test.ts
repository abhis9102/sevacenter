import { strict as assert } from "node:assert";
import { afterEach, describe, it } from "node:test";

import { customHostname, siteForHost } from "../src/lib/brand.js";

const saved = { ...process.env };
afterEach(() => {
  process.env = { ...saved };
});

function twoDomain() {
  process.env.SC_STAFF_BASE_DOMAINS = "stg-seva.example.com";
  process.env.SC_MANDIR_BASE_DOMAINS = "stg-mandir.example.com";
  process.env.SC_TENANT_BASE_DOMAINS = "stg-seva.example.com,stg-mandir.example.com";
}

describe("siteForHost (two-domain brand split)", () => {
  it("maps a staff host to the staff brand and slug", () => {
    twoDomain();
    const s = siteForHost("demo.stg-seva.example.com");
    assert.equal(s.brand, "staff");
    assert.equal(s.slug, "demo");
    assert.equal(s.singleDomain, false);
    assert.equal(s.mandirBase, "stg-mandir.example.com");
  });

  it("maps a mandir host to the mandir brand and slug", () => {
    twoDomain();
    const s = siteForHost("demo.stg-mandir.example.com");
    assert.equal(s.brand, "mandir");
    assert.equal(s.slug, "demo");
    assert.equal(s.staffBase, "stg-seva.example.com");
  });

  it("a bare brand domain resolves no tenant", () => {
    twoDomain();
    assert.equal(siteForHost("stg-seva.example.com").slug, null);
    assert.equal(siteForHost("stg-mandir.example.com").slug, null);
  });

  it("an unknown host has no brand and no slug", () => {
    twoDomain();
    const s = siteForHost("evil.attacker.example");
    assert.equal(s.brand, null);
    assert.equal(s.slug, null);
  });

  it("single-domain (dev) disables gating but still resolves the slug", () => {
    process.env.SC_STAFF_BASE_DOMAINS = "localhost";
    process.env.SC_MANDIR_BASE_DOMAINS = "localhost";
    process.env.SC_TENANT_BASE_DOMAINS = "localhost";
    const s = siteForHost("demo.localhost:3000");
    assert.equal(s.singleDomain, true);
    assert.equal(s.brand, null);
    assert.equal(s.slug, "demo");
  });
});

describe("custom domains (ADR 0036)", () => {
  it("a temple's own domain is a public MandirCenter host with no slug", () => {
    twoDomain();
    const s = siteForHost("www.shrisiddhivinayak.org");
    assert.equal(s.custom, true);
    assert.equal(s.brand, "mandir");
    assert.equal(s.slug, null);
  });
  it("our own hosts are never custom", () => {
    twoDomain();
    for (const h of ["demo.stg-seva.example.com", "demo.stg-mandir.example.com", "stg-seva.example.com"]) {
      assert.equal(siteForHost(h).custom, false, h);
    }
  });
  it("an attacker's public domain gets no slug: the backend decides, and refuses unverified ones", () => {
    twoDomain();
    const s = siteForHost("demo.stg-mandir.attacker.org");
    assert.equal(s.slug, null);
    assert.equal(s.custom, true);
  });
  it("only plausible public hostnames count", () => {
    assert.equal(customHostname("WWW.Temple.org:443"), "www.temple.org");
    for (const h of ["localhost", "x.localhost", "127.0.0.1", "10.0.0.1:3000", "[::1]", "temple", "a b.org",
      "evil.attacker.example", "x.internal", "", null]) {
      assert.equal(customHostname(h), null, String(h));
    }
  });
});
