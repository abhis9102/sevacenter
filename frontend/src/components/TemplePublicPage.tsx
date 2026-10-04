"use client";

import Link from "next/link";

import { useLanguage } from "@/components/LanguageProvider";
import { MandirIcon, type MandirIconName } from "@/components/MandirIcons";
import { MandirShell, useNow, useTemple } from "@/components/MandirShell";
import { Card, Spinner } from "@/components/ui";
import { clock, nextAarti } from "@/lib/darshan";
import { MONTHS, NAKSHATRAS, panchang, tithiName, type LunarCalendar } from "@/lib/panchang";

/**
 * A trust's public front door (ADR 0017, 0024): only what the temple entered, and links only to
 * the services it actually offers right now. All text is rendered as text.
 */
export function TemplePublicPage() {
  return (
    <MandirShell>
      <TempleHome />
    </MandirShell>
  );
}

function TempleHome() {
  const temple = useTemple();
  const { t } = useLanguage();
  const now = useNow();

  if (!temple) {
    return <div className="flex justify-center py-16 text-muted"><Spinner /></div>;
  }

  const actions: Array<{ href: string; icon: MandirIconName; key: keyof typeof t.mandir.quick; show: boolean }> = [
    { href: "/book-puja", icon: "puja", key: "puja", show: temple.pujaBooking },
    { href: "/donate", icon: "donate", key: "donate", show: temple.onlineDonations },
    { href: "/upcoming", icon: "utsav", key: "pass", show: temple.upcomingEvents },
    { href: "/sevak", icon: "sevak", key: "sevak", show: true },
  ];
  const upNext = now ? nextAarti(temple.aartis, now) : null;

  return (
    <div className="flex flex-col gap-6">
      <section className="relative overflow-hidden rounded-[16px] border border-primary/25 bg-gradient-to-br from-primary/20 via-haldi/10 to-surface p-6 shadow-xs sm:p-8">
        <div aria-hidden="true" className="pointer-events-none absolute -right-16 -top-16 size-64 rounded-full bg-haldi/20 blur-3xl" />
        <div className="relative grid gap-6 lg:grid-cols-[1fr_22rem] lg:items-center">
          <div>
            <h2 className="text-3xl font-semibold tracking-tight sm:text-4xl">{temple.trustName}</h2>
            {temple.deity ? (
              <p className="mt-2 text-sm text-muted">
                {t.mandir.presidingDeity}: <span className="font-semibold text-maroon">{temple.deity}</span>
              </p>
            ) : null}
            {temple.address ? <p className="mt-1 text-sm text-muted">{temple.address}</p> : null}
          </div>
          {now ? <PanchangCard at={now} calendar={temple.calendar} /> : null}
        </div>
      </section>

      {temple.announcement ? (
        <div className="flex items-start gap-3 rounded-[12px] border border-haldi/40 bg-haldi/12 p-4 text-sm">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-haldi/25 text-maroon">
            <MandirIcon name="bell" className="size-5" />
          </span>
          <div>
            <p className="font-semibold text-maroon">{t.mandir.announcement}</p>
            <p className="whitespace-pre-line">{temple.announcement}</p>
          </div>
        </div>
      ) : null}

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {actions.filter((a) => a.show).map((a) => (
          <Link key={a.href} href={a.href}
                className="group flex flex-col items-center gap-2 rounded-[14px] border border-line bg-surface p-4 text-center transition-all hover:-translate-y-0.5 hover:border-primary/50 hover:shadow-md">
            <span className="flex size-12 items-center justify-center rounded-full bg-gradient-to-br from-primary to-kumkum text-white shadow-xs transition-transform group-hover:scale-105">
              <MandirIcon name={a.icon} className="size-6" />
            </span>
            <span className="text-sm font-semibold">{t.mandir.quick[a.key].title}</span>
            <span className="text-xs text-muted">{t.mandir.quick[a.key].note}</span>
          </Link>
        ))}
      </div>

      {temple.aartis.length > 0 ? (
        <Card className="flex flex-col gap-4 p-6">
          <div className="flex items-center justify-between gap-3 border-b border-line pb-4">
            <div>
              <h3 className="text-lg font-semibold">{t.mandir.aartis.title}</h3>
              <p className="text-xs text-muted">{t.mandir.aartis.subtitle}</p>
            </div>
            <MandirIcon name="clock" className="size-6 text-primary" />
          </div>
          <ol className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {temple.aartis.map((a) => {
              const isNext = upNext?.name === a.name && upNext.at === a.at;
              return (
                <li key={`${a.at}-${a.name}`}
                    className={`flex flex-col gap-1.5 rounded-[12px] border p-3.5 ${
                      isNext ? "border-primary/50 bg-primary/10" : "border-line bg-surface-2"}`}>
                  <div className="flex items-start justify-between gap-2">
                    <span className="text-sm font-semibold">{a.name}</span>
                    <span className="shrink-0 rounded-full bg-primary/12 px-2 py-0.5 font-mono text-xs font-semibold text-primary-strong">
                      {clock(a.at)}
                    </span>
                  </div>
                  {a.description ? <p className="text-xs leading-relaxed text-muted">{a.description}</p> : null}
                  {isNext ? <span className="text-xs font-semibold text-primary-strong">{t.mandir.aartis.next}</span> : null}
                </li>
              );
            })}
          </ol>
          {temple.timings ? (
            <p className="text-xs text-muted"><span className="font-semibold">{t.mandir.timingNotes}:</span> {temple.timings}</p>
          ) : null}
        </Card>
      ) : temple.timings ? (
        <Card className="text-sm"><span className="font-semibold">{t.mandir.timingNotes}:</span> {temple.timings}</Card>
      ) : null}

      {temple.helpline || temple.address ? (
        <div className="grid gap-3 sm:grid-cols-2">
          {temple.helpline ? (
            <a href={`tel:${temple.helpline}`} className="flex items-center gap-3 rounded-[12px] border border-line bg-surface p-4 hover:border-primary/50">
              <span className="flex size-10 items-center justify-center rounded-full bg-primary/12 text-primary-strong">
                <MandirIcon name="phone" />
              </span>
              <span>
                <span className="block text-xs font-semibold uppercase tracking-wider text-muted">{t.mandir.helpline}</span>
                <span className="font-mono text-sm font-semibold">{temple.helpline}</span>
              </span>
            </a>
          ) : null}
          {temple.address ? (
            <div className="flex items-center gap-3 rounded-[12px] border border-line bg-surface p-4">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-maroon/10 text-maroon">
                <MandirIcon name="pin" />
              </span>
              <span>
                <span className="block text-xs font-semibold uppercase tracking-wider text-muted">{t.mandir.address}</span>
                <span className="text-sm">{temple.address}</span>
              </span>
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

function PanchangCard({ at, calendar }: { at: Date; calendar: LunarCalendar }) {
  const { lang, t } = useLanguage();
  const p = panchang(at, calendar);
  const time = (d: Date) => d.toLocaleString(lang === "hi" ? "hi-IN" : "en-IN", {
    timeZone: "Asia/Kolkata", weekday: "short", hour: "numeric", minute: "2-digit",
  });
  const till = (d: Date) => (lang === "hi" ? `${time(d)} ${t.mandir.panchang.till}` : `${t.mandir.panchang.till} ${time(d)}`);
  return (
    <div className="rounded-[14px] border border-primary/25 bg-surface/90 p-4 shadow-xs backdrop-blur">
      <p className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-primary-strong">
        <MandirIcon name="moon" className="size-4" /> {t.mandir.panchang.title}
      </p>
      <p className="mt-2 font-display text-lg font-semibold">
        {p.adhik ? `${t.mandir.panchang.adhik} ` : ""}{MONTHS[lang][p.month]} · {p.paksha === "SHUKLA" ? t.mandir.panchang.shukla : t.mandir.panchang.krishna}
      </p>
      <dl className="mt-2 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
        <dt className="text-muted">{t.mandir.panchang.tithi}</dt>
        <dd><span className="font-semibold">{tithiName(p.tithi, lang)}</span> <span className="text-xs text-muted">{till(p.tithiEnds)}</span></dd>
        <dt className="text-muted">{t.mandir.panchang.nakshatra}</dt>
        <dd><span className="font-semibold">{NAKSHATRAS[lang][p.nakshatra]}</span> <span className="text-xs text-muted">{till(p.nakshatraEnds)}</span></dd>
      </dl>
      <p className="mt-2 text-[11px] text-muted">{t.mandir.panchang.note}</p>
    </div>
  );
}
