import { strict as assert } from "node:assert";
import { describe, it } from "node:test";

import { displayPhone, prefill } from "../src/lib/devotee.js";

const profile = { fullName: "Lakshmi Iyer", gotra: null, nakshatra: null, rashi: null, dateOfBirth: null, familyNames: null,
  addressLine: null, city: null, state: null, pincode: null };

describe("devotee", () => {
  it("shows Indian mobiles the way people write them", () => {
    assert.equal(displayPhone("+919822041187"), "98220 41187");
    assert.equal(displayPhone("+447700900123"), "+447700900123");
  });

  it("pre-fills forms from the verified contacts and the profile", () => {
    const seva = { profile, contacts: [{ channel: "EMAIL" as const, contact: "l@example.org" },
                                        { channel: "SMS" as const, contact: "+919822041187" }] };
    assert.deepEqual(prefill(seva), { name: "Lakshmi Iyer", phone: "98220 41187", email: "l@example.org" });
    assert.deepEqual(prefill(null), { name: "", phone: "", email: "" });
    assert.deepEqual(prefill({ profile: { ...profile, fullName: null }, contacts: [] }), { name: "", phone: "", email: "" });
  });
});
