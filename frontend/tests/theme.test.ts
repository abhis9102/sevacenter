import { strict as assert } from "node:assert";
import { describe, it } from "node:test";

import { parseTheme, themeCookie } from "../src/lib/theme.js";

describe("theme", () => {
  it("honours only the two literal values from the user-controlled cookie", () => {
    assert.equal(parseTheme("dark"), "dark");
    assert.equal(parseTheme("light"), "light");
    for (const bad of [undefined, null, "", "DARK", "dark\" onload=\"x", "solarized"]) {
      assert.equal(parseTheme(bad), null);
    }
  });

  it("writes a scoped, non-sensitive cookie and clears it for 'device'", () => {
    assert.equal(themeCookie("dark", true), "sc_theme=dark; Path=/; SameSite=Lax; Secure; Max-Age=31536000");
    assert.equal(themeCookie(null, false), "sc_theme=; Path=/; SameSite=Lax; Max-Age=0");
  });
});
