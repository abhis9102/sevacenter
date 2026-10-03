import { strict as assert } from "node:assert";
import { describe, it } from "node:test";

import { istDate, upcomingEvents } from "../src/lib/dashboard.js";
import type { StaffEvent } from "../src/lib/events.js";

const ev = (id: number, status: StaffEvent["status"], startsAt: string, endsAt: string): StaffEvent => ({
  id, title: `E${id}`, description: null, startsAt, endsAt, capacity: null, seatsTaken: 0, status, registrationOpen: true,
});

describe("dashboard", () => {
  it("uses the temple's date (IST), not UTC", () => {
    // 20:00 UTC on the 3rd is already 01:30 on the 4th in India.
    assert.equal(istDate(new Date("2026-10-03T20:00:00Z")), "2026-10-04");
    assert.equal(istDate(new Date("2026-10-03T18:00:00Z")), "2026-10-03");
  });

  it("lists published events that haven't ended, soonest first", () => {
    const now = Date.parse("2026-10-04T06:00:00Z");
    const list = upcomingEvents([
      ev(1, "PUBLISHED", "2026-10-10T04:00:00Z", "2026-10-10T08:00:00Z"),
      ev(2, "DRAFT", "2026-10-05T04:00:00Z", "2026-10-05T08:00:00Z"),
      ev(3, "PUBLISHED", "2026-10-04T04:00:00Z", "2026-10-04T08:00:00Z"), // running now
      ev(4, "PUBLISHED", "2026-10-01T04:00:00Z", "2026-10-01T08:00:00Z"), // over
      ev(5, "CANCELLED", "2026-10-06T04:00:00Z", "2026-10-06T08:00:00Z"),
    ], now);
    assert.deepEqual(list.map((e) => e.id), [3, 1]);
  });
});
