import { strict as assert } from "node:assert";
import { describe, it } from "node:test";

import { readFileSync } from "node:fs";

import { AUDIT_ACTIONS } from "../src/lib/audit.js";

describe("audit log filter", () => {
  it("offers exactly the actions the server records", () => {
    const java = readFileSync("../backend/src/main/java/app/sevacenter/audit/AuditAction.java", "utf8");
    const server = [...(java.match(/enum AuditAction \{([^}]*)\}/)?.[1] ?? "").matchAll(/[A-Z][A-Z_]+/g)].map((m) => m[0]);
    assert.deepEqual([...AUDIT_ACTIONS].sort(), server.sort());
  });
});
