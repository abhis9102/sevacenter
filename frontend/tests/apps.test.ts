import { strict as assert } from "node:assert";
import { describe, it } from "node:test";

import { MODULES, isActive, moduleForPath, sectionsOf, visibleModules } from "../src/lib/apps.js";

const ids = (me: Parameters<typeof visibleModules>[0]) => visibleModules(me).map((m) => m.id);

describe("modules", () => {
  it("shows a trust admin every module, in bar order", () => {
    assert.deepEqual(ids({ role: "TRUST_ADMIN" }), MODULES.map((m) => m.id));
  });

  it("keeps money and administration away from members", () => {
    assert.deepEqual(ids({ role: "MEMBER" }), ["dashboard", "devotees", "pujas", "events", "mandir"]);
  });

  it("shows leaders donations, volunteers and staff, but not payment settings or the audit log", () => {
    const m = ids({ role: "LEADER" });
    assert.ok(m.includes("donations") && m.includes("staff") && m.includes("volunteers"));
    assert.ok(!m.includes("payments") && !m.includes("audit"));
  });

  it("hides a module a trust admin closed, but still shows a view-only one", () => {
    assert.ok(!ids({ role: "LEADER", moduleLimits: { DONATIONS: "NONE" } }).includes("donations"));
    assert.ok(!ids({ role: "LEADER", moduleLimits: { TEMPLE: "NONE" } }).includes("mandir"));
    assert.ok(ids({ role: "MEMBER", moduleLimits: { PUJAS: "VIEW" } }).includes("pujas"));
  });

  it("shows nothing for an unknown role", () => {
    assert.deepEqual(ids({ role: "SUPERUSER" }), []);
  });

  it("finds the module for nested pages but not for look-alike prefixes", () => {
    const all = visibleModules({ role: "TRUST_ADMIN" });
    assert.equal(moduleForPath(all, "/devotees/42")?.id, "devotees");
    assert.equal(moduleForPath(all, "/temple")?.id, "mandir");
    assert.equal(moduleForPath(all, "/profile"), null);
    assert.equal(isActive("/devoteesx", "/devotees"), false);
  });

  it("keeps related modules together: finance, administration, people, pujas and utsavs", () => {
    const groups = sectionsOf(visibleModules({ role: "TRUST_ADMIN" })).map((g) => [g.section, g.modules.map((m) => m.id)]);
    assert.deepEqual(groups, [
      ["dashboard", ["dashboard"]], ["people", ["devotees", "volunteers"]], ["finance", ["donations", "payments"]],
      ["worship", ["pujas", "events"]], ["mandir", ["mandir"]], ["admin", ["staff", "audit"]],
    ]);
    // A leader's finance section has the ledger but no payment setup; members have no finance at all.
    assert.deepEqual(sectionsOf(visibleModules({ role: "LEADER" })).find((g) => g.section === "finance")?.modules.map((m) => m.id),
      ["donations"]);
    assert.ok(!sectionsOf(visibleModules({ role: "MEMBER" })).some((g) => g.section === "finance" || g.section === "admin"));
  });
});
