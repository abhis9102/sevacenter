"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { MandirIcon, type MandirIconName } from "@/components/MandirIcons";
import { MandirLogo } from "@/components/MandirLogo";
import { useMe } from "@/components/Session";
import { Pill } from "@/components/staff";
import { Card } from "@/components/ui";
import { istDate, upcomingEvents } from "@/lib/dashboard";
import { dateChip, formatIst, type StaffEvent } from "@/lib/events";
import { MONTHS, panchang, tithiName } from "@/lib/panchang";
import type { PujaBooking } from "@/lib/pujas";
import type { Signup } from "@/lib/sevak";
import {
  hasRole, moduleAccess, type DevoteePage, type DonationPage, type DonationSummary, type Role, type StaffModule,
} from "@/lib/types";

const inr = (v: string) => `₹${Number(v).toLocaleString("en-IN", { maximumFractionDigits: 0 })}`;

/**
 * One dashboard card: loads on its own, so a slow or denied endpoint never blanks the page. Shown
 * only when the role and module limits allow it; the server enforces the same rules regardless.
 */
function useCard<T>(enabled: boolean, path: string): { data: T | null; failed: boolean } {
  const [state, setState] = useState<{ data: T | null; failed: boolean }>({ data: null, failed: false });
  useEffect(() => {
    if (!enabled) return;
    let live = true;
    api.get<T>(path, { allowUnauthorized: true })
      .then((data) => live && setState({ data, failed: false }))
      .catch(() => live && setState({ data: null, failed: true }));
    return () => { live = false; };
  }, [enabled, path]);
  return state;
}

/** The staff landing page: the temple's operations at a glance (built on the existing APIs). */
export default function DashboardPage() {
  const me = useMe();
  const can = (min: Role, module?: StaffModule) =>
    hasRole(me.role, min) && (!module || moduleAccess(me.moduleLimits, module) !== "NONE");
  const [now] = useState(() => Date.now());
  const today = istDate(new Date(now));
  const p = panchang(new Date(now));

  const pujas = useCard<PujaBooking[]>(can("MEMBER", "PUJAS"), `/puja-bookings?date=${today}`);
  const events = useCard<StaffEvent[]>(can("MEMBER", "EVENTS"), "/events");
  const devotees = useCard<DevoteePage>(can("MEMBER", "DEVOTEES"), "/devotees?page=0&size=1");
  const summary = useCard<DonationSummary>(can("LEADER", "DONATIONS"), "/donations/summary");
  const recent = useCard<DonationPage>(can("LEADER", "DONATIONS"), "/donations?page=0&size=6");
  const sevaks = useCard<Signup[]>(can("LEADER", "VOLUNTEERS"), "/sevaks");

  const upcoming = upcomingEvents(events.data ?? [], now, 4);
  const todays = (pujas.data ?? []).filter((b) => b.status !== "CANCELLED");
  const performed = todays.filter((b) => b.status === "PERFORMED").length;
  const deployed = (sevaks.data ?? []).filter((s) => s.teamId !== null).length;
  const pendingSevaks = (sevaks.data ?? []).filter((s) => s.status === "NEW").length;

  const launch: Array<{ href: string; icon: MandirIconName; title: string; note: string; show: boolean }> = [
    { href: "/donations", icon: "donate", title: "Record donation", note: "80G receipt", show: can("LEADER", "DONATIONS") },
    { href: "/devotees/new", icon: "user", title: "New devotee", note: "With consent", show: can("LEADER", "DEVOTEES") },
    { href: "/pujas", icon: "puja", title: "Sankalp roster", note: "Priest view", show: can("MEMBER", "PUJAS") },
    { href: "/events", icon: "utsav", title: "Gate entry", note: "Pass check-in", show: can("MEMBER", "EVENTS") },
    { href: "/volunteers", icon: "sevak", title: "Sevak Hub", note: "Teams & shifts", show: can("LEADER", "VOLUNTEERS") },
    { href: "/temple", icon: "home", title: "Mandir Center", note: "Aartis & timings", show: can("MEMBER", "TEMPLE") },
  ];

  return (
    <div className="flex flex-col gap-6">
      <section className="flex flex-wrap items-center justify-between gap-4 rounded-[16px] border border-primary/20 bg-gradient-to-br from-primary/15 via-gold/10 to-surface p-6 shadow-xs">
        <div className="flex items-center gap-4">
          <MandirLogo className="size-12" />
          <div>
            <h1 className="text-2xl font-semibold sm:text-3xl">Namaste, {me.displayName}!</h1>
            <p className="text-sm text-muted">
              {p.adhik ? "Adhik " : ""}{MONTHS.en[p.month]} {p.paksha === "SHUKLA" ? "Shukla" : "Krishna"} {tithiName(p.tithi, "en")}
              {" · "}Temple operations at a glance
            </p>
          </div>
        </div>
        <a href="/" target="_blank" rel="noopener noreferrer"
           className="inline-flex items-center gap-1.5 rounded-[10px] bg-primary px-3.5 py-2 text-sm font-semibold text-on-primary shadow-xs hover:bg-primary-strong">
          Open Mandir Center <span aria-hidden="true">↗</span>
        </a>
      </section>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {can("MEMBER", "DEVOTEES") ? (
          <Kpi href="/devotees" label="Devotee family" tone="text-info" badge="Active directory" failed={devotees.failed}
               value={devotees.data ? devotees.data.total.toLocaleString("en-IN") : null} note="Devotees recorded with consent" />
        ) : null}
        {can("LEADER", "DONATIONS") ? (
          <Kpi href="/donations" label="Donations net" tone="text-success" badge={summary.data ? `FY ${summary.data.financialYear}` : undefined}
               failed={summary.failed} value={summary.data ? inr(summary.data.net) : null} note="Ledger balance with 80G tracking" />
        ) : null}
        {can("MEMBER", "PUJAS") ? (
          <Kpi href="/pujas" label="Today's sankalps" tone="text-primary-strong" badge={pujas.data ? `${performed}/${todays.length} done` : undefined}
               failed={pujas.failed} value={pujas.data ? String(todays.length) : null} note="Pujas booked for today" />
        ) : null}
        {can("LEADER", "VOLUNTEERS") ? (
          <Kpi href="/volunteers" label="Sevak volunteers" tone="text-maroon" badge={sevaks.data ? `${deployed} deployed` : undefined}
               failed={sevaks.failed} value={sevaks.data ? String(sevaks.data.filter((s) => s.status !== "DECLINED").length) : null}
               note={pendingSevaks ? `${pendingSevaks} waiting for review` : "Seva teams and shifts"} />
        ) : null}
      </div>

      <Card className="flex flex-col gap-3">
        <h2 className="text-xs font-semibold uppercase tracking-wider text-muted">Quick operations</h2>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
          {launch.filter((l) => l.show).map((l) => (
            <Link key={l.href} href={l.href}
                  className="group flex flex-col items-center gap-1.5 rounded-[12px] border border-line p-4 text-center transition-all hover:-translate-y-0.5 hover:border-primary/50 hover:shadow-md">
              <span className="flex size-10 items-center justify-center rounded-full bg-primary/12 text-primary-strong transition-transform group-hover:scale-105">
                <MandirIcon name={l.icon} className="size-5" />
              </span>
              <span className="text-sm font-semibold">{l.title}</span>
              <span className="text-xs text-muted">{l.note}</span>
            </Link>
          ))}
        </div>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        {can("MEMBER", "PUJAS") ? (
          <Card className="flex flex-col gap-3">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-semibold">Today&apos;s priest sankalp roster</h2>
              <Link href="/pujas" className="text-sm font-medium text-primary-strong hover:underline">View all →</Link>
            </div>
            {todays.length === 0 ? <p className="py-6 text-center text-sm text-muted">No pujas booked for today.</p> : (
              <ul className="flex flex-col divide-y divide-line">
                {todays.slice(0, 6).map((b) => (
                  <li key={b.id} className="flex items-center justify-between gap-3 py-2 text-sm">
                    <span className="min-w-0">
                      <span className="font-semibold">{b.pujaName}</span>
                      <span className="text-muted"> · {b.devoteeName}{b.gotra ? ` (${b.gotra})` : ""}</span>
                      {b.priestName ? <span className="block text-xs text-muted">{b.priestName}</span> : null}
                    </span>
                    <Pill tone={b.status === "PERFORMED" ? "success" : "primary"}>{b.status === "PERFORMED" ? "Done" : "To perform"}</Pill>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        ) : null}
        {can("MEMBER", "EVENTS") ? (
          <Card className="flex flex-col gap-3">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-semibold">Upcoming festivals &amp; utsavs</h2>
              <Link href="/events?view=festivals" className="text-sm font-medium text-primary-strong hover:underline">View all →</Link>
            </div>
            {upcoming.length === 0 ? <p className="py-6 text-center text-sm text-muted">Nothing scheduled.</p> : (
              <ul className="flex flex-col divide-y divide-line">
                {upcoming.map((e) => {
                  const chip = dateChip(e.startsAt);
                  return (
                    <li key={e.id} className="flex items-center gap-3 py-2 text-sm">
                      <span className="flex size-10 shrink-0 flex-col items-center justify-center rounded-[10px] bg-primary/10 text-primary-strong">
                        <span className="text-sm font-semibold leading-none">{chip.day}</span>
                        <span className="text-[10px] font-semibold uppercase">{chip.month}</span>
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block truncate font-semibold">{e.title}</span>
                        <span className="text-xs text-muted">{formatIst(e.startsAt)}</span>
                      </span>
                      <span className="shrink-0 rounded-[8px] bg-surface-2 px-2 py-0.5 font-mono text-xs">
                        {e.seatsTaken}{e.capacity ? `/${e.capacity}` : ""}
                      </span>
                    </li>
                  );
                })}
              </ul>
            )}
          </Card>
        ) : null}
      </div>

      {can("LEADER", "DONATIONS") && recent.data && recent.data.items.length > 0 ? (
        <Card className="flex flex-col gap-3 overflow-x-auto">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold">Recent donations &amp; 80G receipts</h2>
            <Link href="/donations" className="text-sm font-medium text-primary-strong hover:underline">Full ledger →</Link>
          </div>
          <table className="w-full text-left text-sm">
            <thead className="text-xs uppercase tracking-wider text-muted">
              <tr><th className="py-2">Donor</th><th>Amount</th><th>Mode</th><th>Purpose</th><th className="text-right">Date</th></tr>
            </thead>
            <tbody>
              {recent.data.items.map((d) => (
                <tr key={d.id} className="border-t border-line">
                  <td className="py-2 font-medium">{d.donorName}</td>
                  <td className={`font-mono font-semibold ${Number(d.amount) < 0 ? "text-danger" : "text-success"}`}>{inr(d.amount)}</td>
                  <td className="font-mono text-xs">{d.mode}</td>
                  <td className="text-muted">{d.purpose ?? "—"}</td>
                  <td className="text-right font-mono text-xs text-muted">{d.receivedOn}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      ) : null}
    </div>
  );
}

function Kpi({ href, label, tone, badge, value, note, failed }: {
  href: string; label: string; tone: string; badge?: string; value: string | null; note?: string; failed: boolean;
}) {
  return (
    <Link href={href} className="flex flex-col gap-1 rounded-[12px] border border-line bg-surface p-5 shadow-xs transition-colors hover:border-primary/50">
      <div className="flex items-start justify-between gap-2">
        <p className={`text-xs font-semibold uppercase tracking-wider ${tone}`}>{label}</p>
        {badge ? <span className="shrink-0 rounded-full bg-surface-2 px-2 py-0.5 text-[10px] font-semibold text-muted">{badge}</span> : null}
      </div>
      <p className="font-mono text-3xl font-semibold tabular-nums">{failed ? "—" : value ?? "…"}</p>
      <p className="text-xs text-muted">{failed ? "Couldn't load" : note}</p>
    </Link>
  );
}
