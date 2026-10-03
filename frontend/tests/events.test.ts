import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { formatIst, formatPassCode, istFromLocalInput } from "../src/lib/events";

describe("events helpers", () => {
  it("turns a datetime-local value into an IST instant, or nothing", () => {
    assert.equal(istFromLocalInput("2030-08-15T22:00"), "2030-08-15T22:00:00+05:30");
    assert.equal(istFromLocalInput(""), null);
    assert.equal(istFromLocalInput("2030-08-15"), null);
  });
  it("formats in IST whatever the instant's offset", () => {
    assert.match(formatIst("2030-08-15T16:30:00Z"), /10:00\s?pm/i);
  });
  it("shows pass codes in two readable halves", () => {
    assert.equal(formatPassCode("ABCDE23456"), "ABCDE-23456");
    assert.equal(formatPassCode("short"), "short");
  });
});
