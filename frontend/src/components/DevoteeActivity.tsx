"use client";

import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { Badge, Card } from "@/components/ui";
import { describeError } from "@/lib/errors";
import { formatDate, formatDateTime } from "@/lib/format";

/** GET /devotees/{id}/activity (ADR 0023). A null section = not visible to this user; [] = none. */
interface Activity {
  donations: {
    net: string;
    count: number;
    items: { id: number; receivedOn: string; amount: string; mode: string; purpose: string | null; reversed: boolean; receiptNumber: string | null }[];
  } | null;
  pujaBookings: { id: number; bookingCode: string; pujaName: string; pujaDate: string; amount: string; status: string }[] | null;
  eventPasses: { id: number; eventTitle: string | null; startsAt: string | null; attendeeCount: number; status: string; checkedIn: boolean }[] | null;
  sevaOffers: { id: number; sevaAreas: string; status: string; createdAt: string }[] | null;
}

const inr = (v: string) => `₹${Number(v).toLocaleString("en-IN", { maximumFractionDigits: 2 })}`;

/** Everything this devotee has done with the temple, each part shown only to staff allowed to see it. */
export function DevoteeActivity({ devoteeId }: { devoteeId: number }) {
  const [data, setData] = useState<Activity | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let live = true;
    api.get<Activity>(`/devotees/${devoteeId}/activity`)
      .then((a) => live && setData(a))
      .catch((err) => live && setError(describeError(err)));
    return () => { live = false; };
  }, [devoteeId]);

  if (error) return <Card><p className="text-sm text-muted">Activity couldn&apos;t be loaded: {error}</p></Card>;
  if (!data) return null;

  return (
    <Card>
      <h2 className="mb-4 text-lg">Activity with the temple</h2>
      <div className="flex flex-col gap-6 text-sm">
        {data.donations ? (
          <Section title={`Donations · ${inr(data.donations.net)} net`} empty="No donations linked to this record."
                   count={data.donations.items.length}>
            {data.donations.items.map((d) => (
              <Row key={d.id} left={`${formatDate(d.receivedOn)} · ${inr(d.amount)} · ${d.mode}${d.purpose ? ` · ${d.purpose}` : ""}`}>
                {d.reversed ? <Badge tone="warning">Reversed</Badge> : null}
                {d.receiptNumber ? <Badge tone="success">80G {d.receiptNumber}</Badge> : null}
              </Row>
            ))}
          </Section>
        ) : null}
        {data.pujaBookings ? (
          <Section title="Puja bookings" empty="No bookings with this phone or email." count={data.pujaBookings.length}>
            {data.pujaBookings.map((b) => (
              <Row key={b.id} left={`${formatDate(b.pujaDate)} · ${b.pujaName}${Number(b.amount) > 0 ? ` · ${inr(b.amount)}` : ""}`}>
                <Badge>{b.status}</Badge>
              </Row>
            ))}
          </Section>
        ) : null}
        {data.eventPasses ? (
          <Section title="Event passes" empty="No passes with this phone or email." count={data.eventPasses.length}>
            {data.eventPasses.map((p) => (
              <Row key={p.id} left={`${p.eventTitle ?? "Event"}${p.startsAt ? ` · ${formatDateTime(p.startsAt)}` : ""} · ${p.attendeeCount} people`}>
                {p.checkedIn ? <Badge tone="success">Attended</Badge> : <Badge>{p.status}</Badge>}
              </Row>
            ))}
          </Section>
        ) : null}
        {data.sevaOffers ? (
          <Section title="Seva offered" empty="No volunteer offers with this phone or email." count={data.sevaOffers.length}>
            {data.sevaOffers.map((s) => (
              <Row key={s.id} left={`${formatDate(s.createdAt)} · ${s.sevaAreas}`}><Badge>{s.status}</Badge></Row>
            ))}
          </Section>
        ) : null}
      </div>
    </Card>
  );
}

function Section({ title, empty, count, children }: {
  title: string; empty: string; count: number; children: React.ReactNode;
}) {
  return (
    <section>
      <h3 className="mb-2 font-semibold">{title}</h3>
      {count === 0 ? <p className="text-muted">{empty}</p> : <ul className="flex flex-col gap-1.5">{children}</ul>}
    </section>
  );
}

function Row({ left, children }: { left: string; children?: React.ReactNode }) {
  return (
    <li className="flex flex-wrap items-center justify-between gap-2">
      <span>{left}</span>
      <span className="flex gap-1">{children}</span>
    </li>
  );
}
