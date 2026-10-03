import assert from "node:assert/strict";
import { describe, it } from "node:test";

import {
  localEquivalentSetupUrl,
  passwordProblem,
  readSetupToken,
  takeSetupTokenFromUrl,
  type UrlBar,
} from "../src/lib/setupToken";

// Same shape as a real token (43 base64url chars), deliberately low-entropy: it's a fixture.
const TOKEN = `test_setup_token-${"x".repeat(26)}`;

function bar(pathname: string, search: string, hash: string) {
  const replaced: string[] = [];
  const b: UrlBar = {
    location: { pathname, search, hash },
    history: {
      replaceState: (_d, _u, url) => {
        replaced.push(String(url));
      },
    },
  };
  return { b, replaced };
}

describe("readSetupToken", () => {
  it("reads the token from the fragment", () => {
    assert.equal(readSetupToken(`#token=${TOKEN}`), TOKEN);
    assert.equal(readSetupToken(`token=${TOKEN}`), TOKEN);
  });
  it("rejects missing, empty or malformed tokens", () => {
    assert.equal(readSetupToken(""), null);
    assert.equal(readSetupToken("#"), null);
    assert.equal(readSetupToken("#token="), null);
    assert.equal(readSetupToken("#token=short"), null);
    assert.equal(readSetupToken(`#token=${TOKEN}<script>`), null);
    assert.equal(readSetupToken(`#token=${"a".repeat(101)}`), null);
  });
});

describe("takeSetupTokenFromUrl", () => {
  it("returns the token and wipes the fragment from the address bar", () => {
    const { b, replaced } = bar("/setup", "", `#token=${TOKEN}`);
    assert.equal(takeSetupTokenFromUrl(b), TOKEN);
    assert.deepEqual(replaced, ["/setup"]);
    assert.ok(!replaced[0]?.includes(TOKEN));
  });
  it("wipes even an invalid fragment", () => {
    const { b, replaced } = bar("/setup", "", "#token=bad");
    assert.equal(takeSetupTokenFromUrl(b), null);
    assert.deepEqual(replaced, ["/setup"]);
  });
  it("leaves the URL alone when there is no fragment", () => {
    const { b, replaced } = bar("/setup", "", "");
    assert.equal(takeSetupTokenFromUrl(b), null);
    assert.deepEqual(replaced, []);
  });
});

describe("passwordProblem", () => {
  it("enforces 12..200 characters and a matching confirmation", () => {
    assert.match(passwordProblem("short", "short") ?? "", /at least 12/);
    assert.match(passwordProblem("x".repeat(201), "x".repeat(201)) ?? "", /at most 200/);
    assert.match(passwordProblem("correct horse battery", "correct horse batterY") ?? "", /match/);
    assert.equal(passwordProblem("correct horse battery", "correct horse battery"), null);
  });
});

describe("localEquivalentSetupUrl", () => {
  const link = `https://siddheshwar.sevacenter.app/setup#token=${TOKEN}`;
  it("maps the production link to the local origin", () => {
    assert.equal(
      localEquivalentSetupUrl(link, "http://siddheshwar.localhost:3000"),
      `http://siddheshwar.localhost:3000/setup#token=${TOKEN}`,
    );
  });
  it("does nothing outside *.localhost", () => {
    assert.equal(localEquivalentSetupUrl(link, "https://siddheshwar.sevacenter.app"), null);
  });
  it("only maps setup links", () => {
    assert.equal(localEquivalentSetupUrl("https://evil.example/elsewhere#x", "http://a.localhost:3000"), null);
  });
});
