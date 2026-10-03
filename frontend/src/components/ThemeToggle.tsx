"use client";

import { useSyncExternalStore } from "react";

import { parseTheme, themeCookie, type ThemeChoice } from "@/lib/theme";

const listeners = new Set<() => void>();

function subscribe(onChange: () => void) {
  listeners.add(onChange);
  return () => listeners.delete(onChange);
}

/** The theme the server applied (<html data-theme>, from the sc_theme cookie) or one picked since. */
function current(): ThemeChoice | null {
  return parseTheme(document.documentElement.dataset.theme);
}

/**
 * Device / dark / light switch. The choice is a cookie the server reads to set <html data-theme>
 * on every page load (no flash, no inline script under the strict CSP); it also applies at once.
 */
export function ThemeToggle() {
  const choice = useSyncExternalStore(subscribe, current, () => null);

  function apply(next: ThemeChoice | null) {
    if (next) {
      document.documentElement.dataset.theme = next;
    } else {
      delete document.documentElement.dataset.theme;
    }
    document.cookie = themeCookie(next, window.location.protocol === "https:");
    listeners.forEach((l) => l());
  }

  const next: ThemeChoice | null = choice === null ? "dark" : choice === "dark" ? "light" : null;
  const label = choice === null ? "Theme follows your device" : choice === "dark" ? "Dark theme" : "Light theme";

  return (
    <button type="button" onClick={() => apply(next)} title={`${label} (click to change)`}
            aria-label={`${label}. Change theme`}
            className="rounded-full border border-line bg-surface-2 px-2.5 py-1 text-xs text-muted hover:text-fg">
      {choice === null ? "◐" : choice === "dark" ? "☾" : "☀"}
    </button>
  );
}
