"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { createContext, useContext, useEffect, useState, useSyncExternalStore } from "react";

import { api } from "@/components/apiClient";
import { LanguageToggle } from "@/components/LanguageToggle";
import { useLanguage } from "@/components/LanguageProvider";
import { MandirIcon, type MandirIconName } from "@/components/MandirIcons";
import { MandirLogo } from "@/components/MandirLogo";
import { ThemeToggle } from "@/components/ThemeToggle";
import { Alert } from "@/components/ui";
import { clock, darshanState } from "@/lib/darshan";
import { displayPhone, type MySeva } from "@/lib/devotee";
import { describeError } from "@/lib/errors";
import { DEVOTEE_CHANGED, type PublicTemple } from "@/lib/temple";

const TempleContext = createContext<PublicTemple | null>(null);
const DevoteeContext = createContext<MySeva | null>(null);

/** The temple's public data, loaded once by the shell (null while loading). */
export const useTemple = () => useContext(TempleContext);

/** The signed-in devotee (null when signed out), so forms can start with their details. */
export const useDevotee = () => useContext(DevoteeContext);

function subscribeMinute(onChange: () => void) {
  const id = setInterval(onChange, 20_000);
  return () => clearInterval(id);
}

/** The current minute, client-only (null during server render, so markup never mismatches). */
export function useNow(): Date | null {
  const minute = useSyncExternalStore(subscribeMinute, () => Math.floor(Date.now() / 60_000), () => null);
  return minute === null ? null : new Date(minute * 60_000);
}

/**
 * The MandirCenter frame around every public temple page: header with the temple's name and
 * devotee sign-in, live darshan status, tabs for the services the temple actually offers, and a
 * footer. Pages render inside it; the vibrant palette applies only within (.mandir).
 */
export function MandirShell({ children }: { children: React.ReactNode }) {
  const { lang, t } = useLanguage();
  const pathname = usePathname();
  const now = useNow();
  // Hindi puts the postposition after the time ("9:30 pm तक"), English before ("until 9:30 pm").
  const phrase = (word: string, time: string) => (lang === "hi" ? `${time} ${word}` : `${word} ${time}`);
  const [temple, setTemple] = useState<PublicTemple | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [devotee, setDevotee] = useState<MySeva | null>(null);

  useEffect(() => {
    api.get<PublicTemple>("/public/temple").then(setTemple).catch((err) => setError(describeError(err)));
  }, []);

  useEffect(() => {
    function load() {
      api.get<MySeva>("/portal/me", { allowUnauthorized: true }).then(setDevotee, () => setDevotee(null));
    }
    load();
    window.addEventListener(DEVOTEE_CHANGED, load);
    return () => window.removeEventListener(DEVOTEE_CHANGED, load);
  }, []);

  const tabs: Array<{ href: string; label: string; icon: MandirIconName; show: boolean }> = [
    { href: "/", label: t.mandir.tabs.home, icon: "home", show: true },
    { href: "/book-puja", label: t.mandir.tabs.pujas, icon: "puja", show: !!temple?.pujaBooking },
    { href: "/donate", label: t.mandir.tabs.donate, icon: "donate", show: !!temple?.onlineDonations },
    { href: "/upcoming", label: t.mandir.tabs.utsavs, icon: "utsav", show: !!temple?.upcomingEvents },
    { href: "/sevak", label: t.mandir.tabs.sevak, icon: "sevak", show: true },
    { href: "/my-seva", label: t.mandir.tabs.myMandir, icon: "user", show: true },
  ];

  const who = devotee
    ? devotee.profile.fullName ?? (devotee.channel === "SMS" ? displayPhone(devotee.contact) : devotee.contact)
    : null;
  const state = temple && now ? darshanState(temple.hours, temple.status, temple.statusNote, now) : null;

  return (
    <TempleContext.Provider value={temple}>
    <DevoteeContext.Provider value={devotee}>
      <div className="mandir flex min-h-dvh flex-col">
        <header className="sticky top-0 z-30 border-b border-line bg-surface/95 shadow-xs backdrop-blur supports-[backdrop-filter]:bg-surface/85">
          <div className="mx-auto flex max-w-5xl items-center justify-between gap-3 px-4 py-2.5 sm:px-6">
            <Link href="/" className="flex min-w-0 items-center gap-3">
              <MandirLogo className="size-9 sm:size-10" />
              <span className="min-w-0">
                <span className="block truncate font-display text-base font-semibold leading-tight sm:text-lg">
                  {temple?.trustName ?? " "}
                </span>
                <span className="block truncate text-xs text-muted">{temple?.deity ?? t.mandir.tagline}</span>
              </span>
            </Link>
            <div className="flex shrink-0 items-center gap-2 sm:gap-3">
              <span className="hidden sm:inline-flex"><ThemeToggle /></span>
              <LanguageToggle />
              <Link
                href="/my-seva"
                className="inline-flex items-center gap-1.5 rounded-full border border-primary/30 bg-primary/10 px-2.5 py-1.5 text-xs font-semibold text-primary-strong hover:bg-primary/15 sm:px-3"
              >
                <MandirIcon name="user" className="size-4" />
                <span className="hidden max-w-[10rem] truncate sm:inline">{who ?? t.mandir.signIn}</span>
                <span className="sr-only sm:hidden">{who ?? t.mandir.signIn}</span>
              </Link>
            </div>
          </div>

          {state ? (
            <div className="flex items-center justify-center gap-2 border-t border-line bg-surface-2 px-4 py-1.5 text-center text-xs font-medium" role="status">
              <span className={`size-2 shrink-0 rounded-full ${state.open ? "bg-success motion-safe:animate-pulse" : "bg-kumkum"}`} />
              <span className={`font-semibold ${state.open ? "text-success" : "text-kumkum"}`}>
                {state.open ? t.mandir.openNow : t.mandir.closedNow}
              </span>
              <span className="truncate text-muted">
                {state.overridden
                  ? (state.note ? `· ${state.note}` : "")
                  : state.open
                    ? `· ${phrase(t.mandir.until, clock(state.until))}`
                    : state.opensAt ? `· ${phrase(t.mandir.opensAt, clock(state.opensAt))}` : ""}
              </span>
            </div>
          ) : null}

          <nav aria-label={t.mandir.tagline} className="border-t border-line">
            <div className="mx-auto flex max-w-5xl gap-1 overflow-x-auto px-4 py-2 sm:px-6">
              {tabs.filter((tab) => tab.show).map((tab) => {
                const active = tab.href === "/" ? pathname === "/" : pathname.startsWith(tab.href);
                return (
                  <Link
                    key={tab.href}
                    href={tab.href}
                    aria-current={active ? "page" : undefined}
                    className={`flex shrink-0 items-center gap-1.5 rounded-[10px] px-3.5 py-1.5 text-sm font-semibold transition-colors ${
                      active ? "bg-primary text-white shadow-xs" : "text-muted hover:bg-surface-2 hover:text-fg"
                    }`}
                  >
                    <MandirIcon name={tab.icon} className="size-4" />
                    {tab.label}
                  </Link>
                );
              })}
            </div>
          </nav>
        </header>

        <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-6 sm:px-6 sm:py-8">
          {error ? <Alert tone="danger">{error}</Alert> : children}
        </main>

        <footer className="border-t border-line bg-surface">
          <div className="mx-auto flex max-w-5xl flex-col gap-4 px-4 py-6 text-sm sm:flex-row sm:items-start sm:justify-between sm:px-6">
            <div className="flex items-start gap-3">
              <MandirLogo className="size-8" />
              <div>
                <p className="font-display font-semibold">{temple?.trustName}</p>
                {temple?.address ? <p className="text-muted">{temple.address}</p> : null}
                {temple?.helpline ? (
                  <a href={`tel:${temple.helpline}`} className="text-primary-strong underline-offset-2 hover:underline">
                    {temple.helpline}
                  </a>
                ) : null}
              </div>
            </div>
            <div className="flex flex-col gap-1 text-xs text-muted sm:items-end">
              <Link href="/login" className="underline-offset-2 hover:underline">{t.mandir.staffSignIn}</Link>
              <span>{t.mandir.poweredBy}</span>
            </div>
          </div>
        </footer>
      </div>
    </DevoteeContext.Provider>
    </TempleContext.Provider>
  );
}

/** Page heading inside the temple site: a tinted icon tile, title and one line of context. */
export function MandirPageTitle({ icon, title, subtitle, actions }: {
  icon: MandirIconName; title: string; subtitle?: string; actions?: React.ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div className="flex items-center gap-3">
        <span className="flex size-11 shrink-0 items-center justify-center rounded-[12px] bg-gradient-to-br from-primary/20 to-haldi/20 text-primary-strong">
          <MandirIcon name={icon} className="size-6" />
        </span>
        <div>
          <h1 className="text-xl font-semibold sm:text-2xl">{title}</h1>
          {subtitle ? <p className="text-sm text-muted">{subtitle}</p> : null}
        </div>
      </div>
      {actions}
    </div>
  );
}
