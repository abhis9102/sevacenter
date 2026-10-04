import { strict as assert } from "node:assert";
import { describe, it } from "node:test";

import { elongation, julianDay, moonLongitude, NAKSHATRAS, panchang, sunLongitude, tithiName } from "../src/lib/panchang.js";

const near = (actual: number, expected: number, tol: number, what: string) =>
  assert.ok(Math.abs(((actual - expected + 540) % 360) - 180) <= tol, `${what}: ${actual} vs ${expected}`);
const IST = (s: string) => new Date(`${s}+05:30`);

describe("panchang", () => {
  it("matches Meeus' worked examples for the Sun and the Moon", () => {
    near(sunLongitude(2448908.5), 199.909, 0.01, "Sun, 1992 Oct 13 (ex. 25.a)");
    near(moonLongitude(2448724.5), 133.167, 0.01, "Moon, 1992 Apr 12 (ex. 47.a)");
  });

  it("puts the 2026 eclipses at new and full moon", () => {
    near(elongation(julianDay(new Date("2026-02-17T12:01Z"))), 0, 0.3, "annular solar eclipse");
    near(elongation(julianDay(new Date("2026-08-12T17:37Z"))), 0, 0.3, "total solar eclipse");
    near(elongation(julianDay(new Date("2026-03-03T11:38Z"))), 180, 0.3, "total lunar eclipse");
  });

  it("gives Diwali 2026 as Amavasya, in Ashwin (amanta) and Kartik (purnimanta)", () => {
    const amanta = panchang(IST("2026-11-08T19:00:00"));
    assert.equal(amanta.tithi, 29);
    assert.equal(tithiName(amanta.tithi, "en"), "Amavasya");
    assert.equal(amanta.month, 6);
    assert.equal(panchang(IST("2026-11-08T19:00:00"), "PURNIMANTA").month, 7);
  });

  it("gives today, 4 Oct 2026, as Pitru Paksha: Bhadrapada Krishna (amanta), Ashwin Krishna (purnimanta)", () => {
    const p = panchang(IST("2026-10-04T09:00:00"));
    assert.equal(p.paksha, "KRISHNA");
    assert.equal(p.month, 5);
    assert.equal(p.adhik, false);
    assert.equal(panchang(IST("2026-10-04T09:00:00"), "PURNIMANTA").month, 6);
  });

  it("finds 2026's Adhik Jyeshtha", () => {
    const p = panchang(IST("2026-06-01T09:00:00"));
    assert.equal(p.month, 2);
    assert.equal(p.adhik, true);
    assert.equal(panchang(IST("2026-07-01T09:00:00")).adhik, false);
  });

  it("puts each full moon in its month's namesake nakshatra (or a neighbour)", () => {
    const at = (iso: string) => panchang(new Date(iso)).nakshatra;
    assert.ok([9, 10, 11].includes(at("2026-03-03T11:38Z")), "Phalguna purnima ~ Phalguni");
    assert.ok([1, 2, 3].includes(at("2026-11-24T14:53Z")), "Kartik purnima ~ Krittika");
    assert.ok([12, 13, 14].includes(at("2026-04-02T02:12Z")), "Chaitra purnima ~ Chitra");
  });

  it("says when the tithi and nakshatra end, within a day and in order", () => {
    const now = IST("2026-10-04T09:00:00");
    const p = panchang(now);
    for (const end of [p.tithiEnds, p.nakshatraEnds]) {
      const hours = (end.getTime() - now.getTime()) / 3_600_000;
      assert.ok(hours > 0 && hours < 27, `ends in ${hours}h`);
    }
    const next = panchang(new Date(p.tithiEnds.getTime() + 60_000));
    assert.equal(next.tithi, (p.tithi + 1) % 30);
    const before = panchang(new Date(p.tithiEnds.getTime() - 120_000));
    assert.equal(before.tithi, p.tithi);
  });

  it("names tithis within the paksha", () => {
    assert.equal(tithiName(0, "en"), "Pratipada");
    assert.equal(tithiName(14, "en"), "Purnima");
    assert.equal(tithiName(15, "en"), "Pratipada");
    assert.equal(tithiName(22, "hi"), "अष्टमी");
    assert.equal(NAKSHATRAS.en.length, 27);
  });
});
