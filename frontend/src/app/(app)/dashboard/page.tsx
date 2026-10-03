"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { useMe } from "@/components/Session";
import { Card, PageHeader } from "@/components/ui";
import { istDate, upcomingEvents } from "@/lib/dashboard";
import { formatIst, type StaffEvent } from "@/lib/events";
import type { PujaBooking } from "@/lib/pujas";
import { hasRole, moduleAccess, type DevoteePage, type DonationSummary, type Role, type StaffModule } from "@/lib/types";

const inr = (v: string) => `₹${Number(v).toLocaleString("en-IN", { maximumFractionDigits: 2 })}`;

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

/** The staff landing page: today at the temple, at a glance (built on the existing APIs). */
export default function DashboardPage() {
  const me = useMe();
  const can = (min: Role, module: StaffModule) =>
    hasRole(me.role, min) && moduleAccess(me.moduleLimits, module) !== "NONE";
  const [today] = useState(() => istDate(new Date()));

  const pujas = useCard<PujaBooking[]>(can("MEMBER", "PUJAS"), `/puja-bookings?date=${today}`);
  const events = useCard<StaffEvent[]>(can("MEMBER", "EVENTS"), "/events");
  const devotees = useCard<DevoteePage>(can("MEMBER", "DEVOTEES"), "/devotees?page=0&size=1");
  const donations = useCard<DonationSummary>(can("LEADER", "DONATIONS"), "/donations/summary");
  const signups = useCard<{ status: string }[]>(can("LEADER", "VOLUNTEERS"), "/sevaks");

  const [now] = useState(() => Date.now());
  const upcoming = upcomingEvents(events.data ?? [], now);
  const todaysPujas = (pujas.data ?? []).filter((b) => b.status !== "CANCELLED");
  const pendingPujas = todaysPujas.filter((b) => b.status === "CONFIRMED").length;
  const newSignups = (signups.data ?? []).filter((s) => s.status === "NEW").length;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={`Namaste, ${me.displayName}`} description="Today at the temple, at a glance." />
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {can("MEMBER", "PUJAS") ? (
          <Stat href="/pujas" label="Pujas today" failed={pujas.failed}
                value={pujas.data ? String(todaysPujas.length) : null}
                note={pujas.data ? `${pendingPujas} still to perform` : undefined} />
        ) : null}
        {can("MEMBER", "EVENTS") ? (
          <Stat href="/events" label="Upcoming events" failed={events.failed}
                value={events.data ? String(upcoming.length) : null}
                note={upcoming[0] ? `Next: ${upcoming[0].title}` : events.data ? "Nothing scheduled" : undefined} />
        ) : null}
        {can("MEMBER", "DEVOTEES") ? (
          <Stat href="/devotees" label="Devotees" failed={devotees.failed}
                value={devotees.data ? devotees.data.total.toLocaleString("en-IN") : null} />
        ) : null}
        {can("LEADER", "DONATIONS") ? (
          <Stat href="/donations" label={donations.data ? `Donations, FY ${donations.data.financialYear}` : "Donations"}
                failed={donations.failed} value={donations.data ? inr(donations.data.net) : null}
                note={donations.data ? `${donations.data.byMode.reduce((n, m) => n + m.donations, 0)} entries` : undefined} />
        ) : null}
        {can("LEADER", "VOLUNTEERS") ? (
          <Stat href="/volunteers" label="New volunteer offers" failed={signups.failed}
                value={signups.data ? String(newSignups) : null}
                note={signups.data ? (newSignups > 0 ? "Waiting for review" : "All reviewed") : undefined} />
        ) : null}
      </div>

      {can("MEMBER", "EVENTS") && upcoming.length > 0 ? (
        <Card>
          <h2 className="mb-3 font-semibold">Coming up</h2>
          <ul className="flex flex-col gap-2 text-sm">
            {upcoming.map((e) => (
              <li key={e.id} className="flex flex-wrap justify-between gap-2">
                <Link href="/events" className="font-medium hover:underline">{e.title}</Link>
                <span className="text-muted">
                  {formatIst(e.startsAt)}{e.capacity ? ` · ${e.seatsTaken}/${e.capacity} registered` : ` · ${e.seatsTaken} registered`}
                </span>
              </li>
            ))}
          </ul>
        </Card>
      ) : null}

      {can("LEADER", "DONATIONS") && donations.data && donations.data.byFund.length > 1 ? (
        <Card>
          <h2 className="mb-3 font-semibold">By fund, FY {donations.data.financialYear}</h2>
          <ul className="flex flex-col gap-1 text-sm">
            {donations.data.byFund.map((f) => (
              <li key={f.fundId ?? "general"} className="flex justify-between gap-3">
                <span>{f.fund}</span><span className="font-medium">{inr(f.net)}</span>
              </li>
            ))}
          </ul>
        </Card>
      ) : null}
    </div>
  );
}

function Stat({ href, label, value, note, failed }: {
  href: string; label: string; value: string | null; note?: string; failed: boolean;
}) {
  return (
    <Link href={href} className="block rounded-[12px] border border-line bg-surface p-5 hover:border-primary">
      <p className="text-sm text-muted">{label}</p>
      <p className="mt-1 text-2xl font-semibold">{failed ? "—" : value ?? "…"}</p>
      {failed ? <p className="mt-1 text-xs text-muted">Couldn&apos;t load</p> : note ? <p className="mt-1 text-xs text-muted">{note}</p> : null}
    </Link>
  );
}
