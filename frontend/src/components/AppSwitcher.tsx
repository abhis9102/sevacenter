"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useId, useRef, useState } from "react";

import { useLanguage } from "@/components/LanguageProvider";
import { LanguageToggle } from "@/components/LanguageToggle";
import { ThemeToggle } from "@/components/ThemeToggle";
import { isActive, type AppDef, type AppId } from "@/lib/apps";

/** Each app's tile colour, from the theme tokens so it works in light and dark. */
const TONES: Record<AppId, string> = {
  home: "bg-primary/15 text-primary-strong",
  people: "bg-info/15 text-info",
  giving: "bg-success/15 text-success",
  worship: "bg-gold/20 text-maroon",
  site: "bg-maroon/12 text-maroon",
  admin: "bg-fg/10 text-fg",
};

const PATHS: Record<AppId, React.ReactNode> = {
  home: (
    <>
      <rect x="3.5" y="3.5" width="7" height="7" rx="1.5" />
      <rect x="13.5" y="3.5" width="7" height="7" rx="1.5" />
      <rect x="3.5" y="13.5" width="7" height="7" rx="1.5" />
      <rect x="13.5" y="13.5" width="7" height="7" rx="1.5" />
    </>
  ),
  people: (
    <>
      <circle cx="9" cy="8" r="3.5" />
      <path d="M2.5 20c0-3.6 2.9-6 6.5-6s6.5 2.4 6.5 6" />
      <path d="M16 4.5a3.5 3.5 0 0 1 0 7" />
      <path d="M18 14.3c2 .7 3.5 2.7 3.5 5.7" />
    </>
  ),
  giving: <path d="M6 4h12M6 9h12M14.5 20 7 13h2.5a4.5 4.5 0 0 0 0-9" />,
  worship: (
    <>
      <path d="M12 3c2 2.4 3 4.1 3 5.6a3 3 0 0 1-6 0C9 7.1 10 5.4 12 3z" />
      <path d="M3.5 14h17a8.5 6 0 0 1-17 0z" />
    </>
  ),
  site: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18" />
    </>
  ),
  admin: (
    <>
      <path d="M12 3 4.5 6v5.5c0 4.6 3.2 8.3 7.5 9.5 4.3-1.2 7.5-4.9 7.5-9.5V6z" />
      <path d="m9 12 2 2 4-4" />
    </>
  ),
};

export function AppIcon({ app, className = "size-8" }: { app: AppId; className?: string }) {
  return (
    <span className={`inline-flex shrink-0 items-center justify-center rounded-[10px] ${TONES[app]} ${className}`}>
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="size-[58%]"
        aria-hidden="true"
      >
        {PATHS[app]}
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
 * The app switcher: a button naming the current app, opening a panel of every app this user can
 * open. Closes on Escape (focus returns to the button), on a click outside, and on navigation.
 */
export function AppSwitcher({ apps, current }: { apps: AppDef[]; current: AppDef | null }) {
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
        className={`flex items-center gap-2 rounded-[10px] py-1 pl-1 pr-2 text-left transition-colors hover:bg-surface-2 ${
          open ? "bg-surface-2" : ""
        }`}
      >
        {current ? <AppIcon app={current.id} className="size-8" /> : (
          <span className="flex size-8 items-center justify-center rounded-[10px] bg-surface-2 text-muted">
            <Waffle />
          </span>
        )}
        <span className="max-w-[11rem] truncate font-display text-lg font-semibold leading-tight sm:max-w-none">
          {current ? t.apps.names[current.id].name : t.apps.menuTitle}
        </span>
        <svg viewBox="0 0 16 16" className={`size-3.5 text-muted transition-transform ${open ? "rotate-180" : ""}`} aria-hidden="true">
          <path d="m4 6 4 4 4-4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        <span className="sr-only">{t.apps.switcher}</span>
      </button>

      <div
        id={panelId}
        hidden={!open}
        className="fixed inset-x-4 top-16 z-50 rounded-[12px] border border-line bg-surface p-2 shadow-xl sm:absolute sm:inset-x-auto sm:left-0 sm:top-full sm:mt-2 sm:w-[30rem]"
      >
        <nav aria-label={t.apps.menuTitle}>
          <p className="flex items-center gap-2 px-2.5 pb-2 pt-1.5 text-xs font-semibold uppercase tracking-wider text-muted">
            <Waffle /> {t.apps.menuTitle}
          </p>
          <ul className="grid gap-1 sm:grid-cols-2">
            {apps.map((app) => {
              const here = app.pages.some((p) => isActive(pathname, p.href));
              return (
                <li key={app.id}>
                  <Link
                    href={app.pages[0]!.href}
                    onClick={() => setOpenOn(null)}
                    aria-current={here ? "true" : undefined}
                    className={`flex items-start gap-3 rounded-[10px] border p-2.5 transition-colors ${
                      here ? "border-primary/30 bg-primary/8" : "border-transparent hover:bg-surface-2"
                    }`}
                  >
                    <AppIcon app={app.id} className="size-9" />
                    <span className="min-w-0">
                      <span className="block text-sm font-semibold text-fg">{t.apps.names[app.id].name}</span>
                      <span className="mt-0.5 block text-xs leading-snug text-muted">
                        {t.apps.names[app.id].description}
                      </span>
                    </span>
                  </Link>
                </li>
              );
            })}
          </ul>
          {/* On a phone the top bar only has room for the switcher and the user menu. */}
          <div className="mt-2 flex items-center gap-3 border-t border-line px-2.5 pb-1 pt-2.5 sm:hidden">
            <a href="/" target="_blank" rel="noopener noreferrer" className="mr-auto text-sm font-medium text-primary-strong">
              {t.apps.templeSite} ↗<span className="sr-only"> ({t.apps.newTab})</span>
            </a>
            <ThemeToggle />
            <LanguageToggle />
          </div>
        </nav>
      </div>
    </div>
  );
}
