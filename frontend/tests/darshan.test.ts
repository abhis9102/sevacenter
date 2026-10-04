import { strict as assert } from "node:assert";
import { describe, it } from "node:test";

import { clock, darshanState, istMinutes, nextAarti } from "../src/lib/darshan.js";

const hours = { morningOpen: "05:30:00", morningClose: "12:30:00", eveningOpen: "16:00:00", eveningClose: "21:30:00" };
const IST = (time: string) => new Date(`2026-10-04T${time}:00+05:30`);

describe("darshan", () => {
  it("reads the temple's clock, not the browser's", () => {
    assert.equal(istMinutes(new Date("2026-10-04T00:00:00Z")), 330);
  });

  it("is open inside a session and says until when", () => {
    assert.deepEqual(darshanState(hours, null, null, IST("05:30")), { open: true, until: "12:30:00", overridden: false });
    assert.deepEqual(darshanState(hours, null, null, IST("20:00")), { open: true, until: "21:30:00", overridden: false });
  });

  it("is closed between and after sessions, with the next opening", () => {
    assert.deepEqual(darshanState(hours, null, null, IST("12:30")), { open: false, opensAt: "16:00:00", overridden: false });
    assert.deepEqual(darshanState(hours, null, null, IST("23:00")), { open: false, opensAt: "05:30:00", overridden: false });
    assert.deepEqual(darshanState(hours, null, null, IST("04:00")), { open: false, opensAt: "05:30:00", overridden: false });
  });

  it("works with only one session", () => {
    const morning = { ...hours, eveningOpen: null, eveningClose: null };
    assert.deepEqual(darshanState(morning, null, null, IST("17:00")), { open: false, opensAt: "05:30:00", overridden: false });
  });

  it("lets today's override win, and says nothing without hours or override", () => {
    assert.deepEqual(darshanState(hours, "CLOSED", "Grahan", IST("08:00")), { open: false, overridden: true, note: "Grahan" });
    assert.deepEqual(darshanState(null, "OPEN", null, IST("02:00")), { open: true, overridden: true, note: null });
    assert.equal(darshanState(null, null, null, IST("08:00")), null);
  });

  it("finds the next aarti and formats times", () => {
    const list = [{ name: "Kakad", at: "05:30:00", description: null }, { name: "Shej", at: "21:00:00", description: null }];
    assert.equal(nextAarti(list, IST("06:00"))?.name, "Shej");
    assert.equal(nextAarti(list, IST("21:00"))?.name, "Shej");
    assert.equal(nextAarti(list, IST("21:01")), null);
    assert.equal(clock("05:30:00"), "5:30 am");
    assert.equal(clock("12:05"), "12:05 pm");
    assert.equal(clock("00:15"), "12:15 am");
  });
});
