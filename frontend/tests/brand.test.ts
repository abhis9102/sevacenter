import { strict as assert } from "node:assert";
import { afterEach, describe, it } from "node:test";

import { siteForHost } from "../src/lib/brand.js";

const saved = { ...process.env };
afterEach(() => {
  process.env = { ...saved };
});

function twoDomain() {
  process.env.SC_STAFF_BASE_DOMAINS = "stg-seva.ehack.co.in";
  process.env.SC_MANDIR_BASE_DOMAINS = "stg-mandir.ehack.co.in";
  process.env.SC_TENANT_BASE_DOMAINS = "stg-seva.ehack.co.in,stg-mandir.ehack.co.in";
}

describe("siteForHost (two-domain brand split)", () => {
  it("maps a staff host to the staff brand and slug", () => {
    twoDomain();
    const s = siteForHost("demo.stg-seva.ehack.co.in");
    assert.equal(s.brand, "staff");
    assert.equal(s.slug, "demo");
    assert.equal(s.singleDomain, false);
    assert.equal(s.mandirBase, "stg-mandir.ehack.co.in");
  });

  it("maps a mandir host to the mandir brand and slug", () => {
    twoDomain();
    const s = siteForHost("demo.stg-mandir.ehack.co.in");
    assert.equal(s.brand, "mandir");
    assert.equal(s.slug, "demo");
    assert.equal(s.staffBase, "stg-seva.ehack.co.in");
  });

  it("a bare brand domain resolves no tenant", () => {
    twoDomain();
    assert.equal(siteForHost("stg-seva.ehack.co.in").slug, null);
    assert.equal(siteForHost("stg-mandir.ehack.co.in").slug, null);
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
