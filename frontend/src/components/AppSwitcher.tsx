"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useId, useRef, useState } from "react";

import { useLanguage } from "@/components/LanguageProvider";
import { LanguageToggle } from "@/components/LanguageToggle";
import { ThemeToggle } from "@/components/ThemeToggle";
import { isActive, type NavModule, type Section, type SectionGroup } from "@/lib/apps";

/** Each section's tint, from the theme tokens so it works in light and dark. */
const TONES: Record<Section, string> = {
  dashboard: "bg-primary/15 text-primary-strong",
  people: "bg-info/15 text-info",
  finance: "bg-success/15 text-success",
  worship: "bg-gold/20 text-maroon",
  mandir: "bg-maroon/12 text-maroon",
  admin: "bg-fg/10 text-fg",
};

const PATHS: Record<Section, React.ReactNode> = {
  dashboard: <><rect x="3.5" y="3.5" width="7" height="7" rx="1.5" /><rect x="13.5" y="3.5" width="7" height="7" rx="1.5" /><rect x="3.5" y="13.5" width="7" height="7" rx="1.5" /><rect x="13.5" y="13.5" width="7" height="7" rx="1.5" /></>,
  people: <><circle cx="9" cy="8" r="3.5" /><path d="M2.5 20c0-3.6 2.9-6 6.5-6s6.5 2.4 6.5 6" /><path d="M16 4.5a3.5 3.5 0 0 1 0 7" /><path d="M18 14.3c2 .7 3.5 2.7 3.5 5.7" /></>,
  finance: <path d="M6 4h12M6 9h12M14.5 20 7 13h2.5a4.5 4.5 0 0 0 0-9" />,
  worship: <><path d="M12 3c2 2.4 3 4.1 3 5.6a3 3 0 0 1-6 0C9 7.1 10 5.4 12 3z" /><path d="M3.5 14h17a8.5 6 0 0 1-17 0z" /></>,
  mandir: <><path d="M12 2.5v2.5" /><path d="M12 5 8.5 10h7z" /><path d="M6.5 10h11l1 3h-13z" /><path d="M6 13h12v7.5H6z" /><path d="M10.5 20.5v-4a1.5 1.5 0 0 1 3 0v4" /></>,
  admin: <><path d="M12 3 4.5 6v5.5c0 4.6 3.2 8.3 7.5 9.5 4.3-1.2 7.5-4.9 7.5-9.5V6z" /><path d="m9 12 2 2 4-4" /></>,
};

export function SectionIcon({ section, className = "size-8" }: { section: Section; className?: string }) {
  return (
    <span className={`inline-flex shrink-0 items-center justify-center rounded-[10px] ${TONES[section]} ${className}`}>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"
           strokeLinejoin="round" className="size-[58%]" aria-hidden="true">
        {PATHS[section]}
      </svg>
    </span>
  );
}

function Waffle() {
  return (
    <svg viewBox="0 0 16 16" className="size-4" fill="currentColor" aria-hidden="true">
      {[2.5, 8, 13.5].flatMap((y) => [2.5, 8, 13.5].map((x) => <circle key={`${x}-${y}`} cx={x} cy={y} r="1.5" />))}
    </svg>
  );
}

/**
 * The switcher: a button naming the current section, opening a panel of every section this user
 * can open, with each section's pages. Closes on Escape (focus returns to the button), on a click outside, and on navigation.
 */
export function AppSwitcher({ sections, current }: { sections: SectionGroup[]; current: NavModule | null }) {
  const { t } = useLanguage();
  const pathname = usePathname();
  // Open "on" a path: navigating anywhere closes it without a state update in an effect.
  const [openOn, setOpenOn] = useState<string | null>(null);
  const open = openOn === pathname;
  const ref = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const panelId = useId();

  useEffect(() => {
    if (!open) {
      return;
    }
    function onPointer(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpenOn(null);
      }
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") {
        setOpenOn(null);
        buttonRef.current?.focus();
      }
    }
    document.addEventListener("mousedown", onPointer);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onPointer);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  return (
    <div className="relative" ref={ref}>
      <button
        ref={buttonRef}
        type="button"
        onClick={() => setOpenOn(open ? null : pathname)}
        aria-expanded={open}
        aria-controls={panelId}
        title={t.apps.switcher}
        className={`flex items-center gap-2 rounded-[10px] border border-line bg-surface px-2.5 py-1.5 text-sm font-semibold shadow-xs transition-colors hover:bg-surface-2 ${
          open ? "bg-surface-2" : ""
        }`}
      >
        <span className="text-muted"><Waffle /></span>
        <span className="max-w-[9rem] truncate sm:max-w-none">{current ? t.apps.sections[current.section].name : t.apps.menuTitle}</span>
        <svg viewBox="0 0 16 16" className={`size-3 text-muted transition-transform ${open ? "rotate-180" : ""}`} aria-hidden="true">
          <path d="m4 6 4 4 4-4" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        <span className="sr-only">{t.apps.switcher}</span>
      </button>

      <div
        id={panelId}
        hidden={!open}
        className="fixed inset-x-4 top-16 z-50 max-h-[calc(100dvh-5rem)] overflow-y-auto rounded-[14px] border border-line bg-surface p-3 shadow-xl sm:absolute sm:inset-x-auto sm:left-0 sm:top-full sm:mt-2 sm:w-[36rem]"
      >
        <nav aria-label={t.apps.menuTitle}>
          <p className="mb-2 flex items-center justify-between border-b border-line px-2 pb-2 pt-0.5 text-xs font-semibold uppercase tracking-wider text-muted">
            <span className="flex items-center gap-2"><Waffle /> {t.apps.menuTitle}</span>
          </p>
          <ul className="grid gap-1.5 sm:grid-cols-2">
            {sections.map((g) => {
              const here = g.modules.some((m) => isActive(pathname, m.href));
              return (
                <li key={g.section} className={`flex items-start gap-3 rounded-[10px] border p-2.5 transition-colors ${
                  here ? "border-primary/30 bg-primary/8" : "border-transparent hover:bg-surface-2"}`}>
                  <SectionIcon section={g.section} className="size-9" />
                  <span className="min-w-0 flex-1">
                    <Link href={g.modules[0]!.href} onClick={() => setOpenOn(null)}
                          aria-current={here ? "true" : undefined}
                          className="block text-sm font-semibold text-fg hover:text-primary-strong">
                      {t.apps.sections[g.section].name}
                    </Link>
                    <span className="mt-0.5 block text-xs leading-snug text-muted">{t.apps.sections[g.section].description}</span>
                    {g.modules.length > 1 ? (
                      <span className="mt-1.5 flex flex-wrap gap-1">
                        {g.modules.map((m) => (
                          <Link key={m.id} href={m.href} onClick={() => setOpenOn(null)}
                                aria-current={isActive(pathname, m.href) ? "page" : undefined}
                                className={`rounded-full border px-2 py-0.5 text-[11px] font-semibold transition-colors ${
                                  isActive(pathname, m.href) ? "border-primary/40 bg-primary/12 text-primary-strong"
                                    : "border-line text-muted hover:border-primary/40 hover:text-fg"}`}>
                            {t.nav[m.label]}
                          </Link>
                        ))}
                      </span>
                    ) : null}
                  </span>
                </li>
              );
            })}
          </ul>
          {/* On a phone the top bar only has room for the switcher and the user menu. */}
          <div className="mt-2 flex items-center gap-3 border-t border-line px-2.5 pb-1 pt-2.5 sm:hidden">
            <a href="/" target="_blank" rel="noopener noreferrer" className="mr-auto text-sm font-medium text-primary-strong">
              {t.apps.mandirCenter} ↗<span className="sr-only"> ({t.apps.newTab})</span>
            </a>
            <ThemeToggle />
            <LanguageToggle />
          </div>
        </nav>
      </div>
    </div>
  );
}
