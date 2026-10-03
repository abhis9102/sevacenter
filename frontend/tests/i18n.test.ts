import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { DICTIONARIES, LANG_COOKIE } from "../src/lib/i18n";

describe("i18n dictionaries", () => {
  it("defines matching top-level keys for both en and hi", () => {
    const enKeys = Object.keys(DICTIONARIES.en).sort();
    const hiKeys = Object.keys(DICTIONARIES.hi).sort();
    assert.deepEqual(enKeys, hiKeys);
  });

  it("defines common strings in Hindi and English", () => {
    assert.equal(DICTIONARIES.en.common.save, "Save");
    assert.equal(DICTIONARIES.hi.common.save, "सुरक्षित करें");
    assert.equal(DICTIONARIES.en.common.cancel, "Cancel");
    assert.equal(DICTIONARIES.hi.common.cancel, "रद्द करें");
  });

  it("defines navigation items in Hindi and English", () => {
    assert.match(DICTIONARIES.hi.nav.devotees, /भक्त/);
    assert.match(DICTIONARIES.hi.nav.staff, /कर्मचारी/);
    assert.equal(DICTIONARIES.en.nav.devotees, "Devotees");
  });

  it("defines consent sources in Hindi and English", () => {
    assert.match(DICTIONARIES.hi.consent.IN_PERSON, /व्यक्तिगत/);
    assert.match(DICTIONARIES.hi.consent.PHONE, /फ़ोन/);
    assert.match(DICTIONARIES.hi.consent.ONLINE_FORM, /ऑनलाइन/);
    assert.match(DICTIONARIES.hi.consent.WRITTEN, /लिखित/);
  });

  it("formats devotee count strings dynamically", () => {
    const enText = DICTIONARIES.en.devotees.pageShowing(1, 25, 100);
    assert.equal(enText, "Showing 1–25 of 100 devotees");

    const hiText = DICTIONARIES.hi.devotees.pageShowing(1, 25, 100);
    assert.equal(hiText, "कुल 100 में से 1–25 भक्त प्रदर्शित");
  });

  it("formats import count strings dynamically", () => {
    const enText = DICTIONARIES.en.devotees.importPage.importCompleteMultiple(5);
    assert.equal(enText, "5 devotees were added.");

    const hiText = DICTIONARIES.hi.devotees.importPage.importCompleteMultiple(5);
    assert.equal(hiText, "5 भक्तों के रिकॉर्ड सफलतापूर्वक जोड़े गए।");
  });

  it("defines profile strings in Hindi and English", () => {
    assert.equal(DICTIONARIES.en.nav.profile, "Profile");
    assert.match(DICTIONARIES.hi.nav.profile, /प्रोफ़ाइल/);
    assert.equal(DICTIONARIES.en.profile.tabs.security, "Security & Password");
    assert.equal(DICTIONARIES.hi.profile.tabs.security, "सुरक्षा व पासवर्ड");
    assert.equal(DICTIONARIES.en.profile.tabs.privacy, "Privacy");
    assert.equal(DICTIONARIES.hi.profile.tabs.privacy, "गोपनीयता");
    assert.equal(DICTIONARIES.en.profile.tabs.notifications, "Notifications");
    assert.equal(DICTIONARIES.hi.profile.tabs.notifications, "सूचनाएं");
    assert.equal(DICTIONARIES.en.profile.avatar.uploadPhoto, "Upload photo");
    assert.equal(DICTIONARIES.hi.profile.avatar.uploadPhoto, "फ़ोटो अपलोड करें");
  });

  it("defines sc_lang as the cookie name", () => {
    assert.equal(LANG_COOKIE, "sc_lang");
  });
});
