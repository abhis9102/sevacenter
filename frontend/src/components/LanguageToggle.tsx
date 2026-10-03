"use client";

import { useLanguage } from "./LanguageProvider";

export function LanguageToggle() {
  const { lang, setLang } = useLanguage();

  return (
    <div
      role="group"
      aria-label="Language selection"
      className="inline-flex items-center rounded-full border border-line bg-surface-2 p-0.5 text-xs font-medium"
    >
      <button
        type="button"
        onClick={() => setLang("en")}
        aria-pressed={lang === "en"}
        className={`rounded-full px-2.5 py-0.5 transition-colors ${
          lang === "en"
            ? "bg-surface text-primary-strong shadow-xs font-semibold"
            : "text-muted hover:text-fg"
        }`}
      >
        English
      </button>
      <button
        type="button"
        onClick={() => setLang("hi")}
        aria-pressed={lang === "hi"}
        className={`rounded-full px-2.5 py-0.5 transition-colors font-mukta ${
          lang === "hi"
            ? "bg-surface text-primary-strong shadow-xs font-semibold"
            : "text-muted hover:text-fg"
        }`}
      >
        हिन्दी
      </button>
    </div>
  );
}
