import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { validAmount } from "../src/lib/razorpay";
import { buildCsp } from "../src/lib/securityHeaders";

describe("validAmount (mirrors the API: rupees, 2 decimals, Rs 1 - Rs 10,00,000)", () => {
  it("accepts plain and decimal rupees", () => {
    for (const ok of ["1", "501", "501.5", "501.50", "1000000"]) assert.equal(validAmount(ok), true, ok);
  });
  it("rejects zero, negatives, too many decimals, words and huge amounts", () => {
    for (const bad of ["0", "0.50", "-1", "501.555", "1e3", "abc", "", "1000000.01", "01"]) {
      assert.equal(validAmount(bad), false, bad);
    }
  });
});

describe("buildCsp for the donate page (ADR 0013)", () => {
  it("allows Razorpay Checkout only when asked", () => {
    const normal = buildCsp("n0nce", false);
    assert.match(normal, /frame-src 'none'/);
    assert.doesNotMatch(normal, /razorpay/);

    const donate = buildCsp("n0nce", false, { razorpay: true });
    assert.match(donate, /script-src 'self' 'nonce-n0nce' 'strict-dynamic' https:\/\/checkout\.razorpay\.com/);
    assert.match(donate, /frame-src https:\/\/api\.razorpay\.com https:\/\/checkout\.razorpay\.com/);
    assert.doesNotMatch(donate, /unsafe-inline|unsafe-eval/);
  });
});
