import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { toDevoteeInput, EMPTY_DEVOTEE } from "../src/lib/devoteeForm";
import { ApiError, describeError, NetworkError } from "../src/lib/errors";
import { buildCsp } from "../src/lib/securityHeaders";

describe("ApiError parsing", () => {
  it("reads validation field errors", () => {
    const e = new ApiError(400, { error: "validation_failed", fields: { pincode: "pincode must be 6 digits" } });
    assert.equal(e.code, "validation_failed");
    assert.deepEqual(e.fields, { pincode: "pincode must be 6 digits" });
  });
  it("reads CSV import row errors", () => {
    const e = new ApiError(400, {
      error: "invalid_rows",
      rows: [{ line: 3, field: "consentSource", message: "consent is required to record a devotee" }],
    });
    assert.deepEqual(e.rows, [{ line: 3, field: "consentSource", message: "consent is required to record a devotee" }]);
  });
  it("tolerates junk bodies", () => {
    for (const body of [null, "x", 42, [], { error: 5, fields: "nope", rows: {} }]) {
      const e = new ApiError(500, body);
      assert.equal(e.code, null);
      assert.deepEqual(e.fields, {});
      assert.deepEqual(e.rows, []);
    }
  });
});

describe("describeError", () => {
  it("maps API codes to plain language", () => {
    assert.match(describeError(new ApiError(409, { error: "last_admin" })), /at least one active trust admin/);
    assert.match(describeError(new ApiError(409, { error: "email_taken" })), /already exists/);
    assert.match(describeError(new ApiError(400, { error: "invalid_or_expired_link" })), /expired/);
  });
  it("falls back on the status code", () => {
    assert.match(describeError(new ApiError(403, null)), /role doesn't allow/);
    assert.match(describeError(new ApiError(429, null)), /Too many attempts/);
    assert.match(describeError(new ApiError(503, null)), /on our side/);
  });
  it("never echoes server text for unknown codes", () => {
    const msg = describeError(new ApiError(400, { error: "<img src=x onerror=alert(1)>" }));
    assert.ok(!msg.includes("<img"));
  });
  it("describes network failures", () => {
    assert.match(describeError(new NetworkError()), /Can't reach/);
  });
});

describe("devotee request body", () => {
  it("sends blanks as null and consent only on create", () => {
    const v = { ...EMPTY_DEVOTEE, fullName: "  Lakshmi Iyer ", phone: " ", consentSource: "IN_PERSON" };
    assert.deepEqual(toDevoteeInput(v, "create"), {
      fullName: "Lakshmi Iyer",
      phone: null,
      email: null,
      addressLine: null,
      city: null,
      state: null,
      pincode: null,
      dateOfBirth: null,
      consentSource: "IN_PERSON",
    });
    assert.ok(!("consentSource" in toDevoteeInput(v, "edit")));
  });
});

describe("CSP", () => {
  it("production policy has a nonce and no unsafe-eval / unsafe-inline", () => {
    const csp = buildCsp("abc123", false);
    assert.match(csp, /script-src 'self' 'nonce-abc123' 'strict-dynamic'/);
    assert.match(csp, /frame-ancestors 'none'/);
    assert.match(csp, /object-src 'none'/);
    assert.ok(!csp.includes("unsafe-eval"));
    assert.ok(!csp.includes("unsafe-inline"));
  });
  it("dev-only relaxations stay out of production", () => {
    const dev = buildCsp("n", true);
    assert.ok(dev.includes("'unsafe-eval'"));
  });
});
