import { strict as assert } from "node:assert";
import { describe, it } from "node:test";

import { readFileSync } from "node:fs";

import { SEVA_ICON_KEYS, hubStats, sevaIcon, type Signup, type Team } from "../src/lib/sevak.js";

const s = (id: number, status: Signup["status"], teamId: number | null): Signup => ({
  id, fullName: `S${id}`, phone: null, email: null, sevaAreas: "x", availability: null, notes: null, status,
  createdAt: "2026-10-04T00:00:00Z", teamId, duty: null, registeredByStaff: false,
});
const team = (id: number, targetCount: number | null): Team => ({ id, name: `T${id}`, description: null, targetCount, icon: null, shifts: [] });

describe("sevak hub", () => {
  it("counts volunteers, leaving declined offers out", () => {
    const st = hubStats([s(1, "APPROVED", 1), s(2, "APPROVED", null), s(3, "NEW", null), s(4, "DECLINED", null)], [team(1, 4)]);
    assert.deepEqual(st, { total: 3, assigned: 1, ready: 1, pending: 1, readiness: 25 });
  });

  it("caps readiness at 100% and has none without targets", () => {
    assert.equal(hubStats([s(1, "APPROVED", 1), s(2, "APPROVED", 1)], [team(1, 1)]).readiness, 100);
    assert.equal(hubStats([], [team(1, null)]).readiness, null);
  });

  it("offers exactly the icons the server accepts", () => {
    const java = readFileSync("../backend/src/main/java/app/sevacenter/sevak/SevakService.java", "utf8");
    const server = [...(java.match(/ICONS = java\.util\.Set\.of\(([^)]*)\)/)?.[1] ?? "").matchAll(/"([a-z-]+)"/g)].map((m) => m[1]);
    assert.deepEqual([...server].sort(), [...SEVA_ICON_KEYS].sort());
    assert.ok(SEVA_ICON_KEYS.length >= 30);
  });

  it("falls back to the seva icon for unknown or missing names", () => {
    assert.equal(sevaIcon("cow"), "cow");
    assert.equal(sevaIcon(null), "hands");
    assert.equal(sevaIcon("<script>"), "hands");
  });
});
