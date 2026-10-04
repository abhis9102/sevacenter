import { strict as assert } from "node:assert";
import { describe, it } from "node:test";

import { APPS, appForPath, isActive, visibleApps } from "../src/lib/apps.js";

const ids = (me: Parameters<typeof visibleApps>[0]) => visibleApps(me).map((a) => a.id);
const hrefs = (me: Parameters<typeof visibleApps>[0]) => visibleApps(me).flatMap((a) => a.pages.map((p) => p.href));

describe("apps", () => {
  it("shows a trust admin every app and every page", () => {
    assert.deepEqual(ids({ role: "TRUST_ADMIN" }), APPS.map((a) => a.id));
    assert.equal(hrefs({ role: "TRUST_ADMIN" }).length, APPS.flatMap((a) => a.pages).length);
  });

  it("keeps money and administration away from members", () => {
    assert.deepEqual(ids({ role: "MEMBER" }), ["home", "people", "worship", "site"]);
    assert.deepEqual(hrefs({ role: "MEMBER" }), ["/dashboard", "/devotees", "/pujas", "/events", "/temple"]);
  });

  it("shows leaders donations and staff, but not payment settings or the audit log", () => {
    const pages = hrefs({ role: "LEADER" });
    assert.ok(pages.includes("/donations") && pages.includes("/staff") && pages.includes("/volunteers"));
    assert.ok(!pages.includes("/payments") && !pages.includes("/audit"));
  });

  it("hides a page whose module a trust admin closed, and the app once it has no pages left", () => {
    const leader = { role: "LEADER", moduleLimits: { DONATIONS: "NONE" as const } };
    assert.ok(!ids(leader).includes("giving"));
    const noDevotees = { role: "LEADER", moduleLimits: { DEVOTEES: "NONE" as const } };
    assert.deepEqual(visibleApps(noDevotees).find((a) => a.id === "people")?.pages.map((p) => p.href), ["/volunteers"]);
  });

  it("still shows a view-only module", () => {
    assert.ok(ids({ role: "MEMBER", moduleLimits: { PUJAS: "VIEW", EVENTS: "VIEW" } }).includes("worship"));
  });

  it("shows nothing for an unknown role", () => {
    assert.deepEqual(ids({ role: "SUPERUSER" }), []);
  });

  it("finds the app for nested pages but not for look-alike prefixes", () => {
    const apps = visibleApps({ role: "TRUST_ADMIN" });
    assert.equal(appForPath(apps, "/devotees/42")?.id, "people");
    assert.equal(appForPath(apps, "/payments")?.id, "giving");
    assert.equal(appForPath(apps, "/profile"), null);
    assert.equal(isActive("/devoteesx", "/devotees"), false);
  });
});
