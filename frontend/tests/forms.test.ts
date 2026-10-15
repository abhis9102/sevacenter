import { strict as assert } from "node:assert";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, it } from "node:test";

/**
 * A form submitted before JavaScript loads uses its HTML method; the default is GET, which puts
 * every field in the URL (history, proxy and server logs). Staging DAST caught the login form
 * doing exactly that (2026-10-15). Every form in a file with a secret field must say method="post".
 */
function files(dir: string): string[] {
  return readdirSync(dir).flatMap((n) => {
    const p = join(dir, n);
    return statSync(p).isDirectory() ? files(p) : p.endsWith(".tsx") ? [p] : [];
  });
}

describe("forms with secrets never submit as GET", () => {
  const root = join(process.cwd(), "src");
  const secret = /type=["{]?["']?password|secret|apiKey|keySecret/i;
  const withSecrets = files(root).filter((f) => secret.test(readFileSync(f, "utf8")));
  it("finds the forms to check", () => {
    assert.ok(withSecrets.some((f) => f.includes(join("app", "login"))), "login page is covered");
  });
  for (const f of withSecrets) {
    it(f.slice(root.length + 1), () => {
      const forms = readFileSync(f, "utf8").match(/<form\b[^>]*>/g) ?? [];
      for (const form of forms) {
        assert.match(form, /method="post"/, `${form} in ${f}`);
      }
    });
  }
});
