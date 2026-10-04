import { strict as assert } from "node:assert";
import { describe, it } from "node:test";

import { hubStats, type Signup, type Team } from "../src/lib/sevak.js";

const s = (id: number, status: Signup["status"], teamId: number | null): Signup => ({
  id, fullName: `S${id}`, phone: null, email: null, sevaAreas: "x", availability: null, notes: null, status,
  createdAt: "2026-10-04T00:00:00Z", teamId, duty: null, registeredByStaff: false,
});
const team = (id: number, targetCount: number | null): Team => ({ id, name: `T${id}`, description: null, targetCount, shifts: [] });

describe("sevak hub", () => {
  it("counts volunteers, leaving declined offers out", () => {
    const st = hubStats([s(1, "APPROVED", 1), s(2, "APPROVED", null), s(3, "NEW", null), s(4, "DECLINED", null)], [team(1, 4)]);
    assert.deepEqual(st, { total: 3, assigned: 1, ready: 1, pending: 1, readiness: 25 });
  });

  it("caps readiness at 100% and has none without targets", () => {
    assert.equal(hubStats([s(1, "APPROVED", 1), s(2, "APPROVED", 1)], [team(1, 1)]).readiness, 100);
    assert.equal(hubStats([], [team(1, null)]).readiness, null);
  });
});
