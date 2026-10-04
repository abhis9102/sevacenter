"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { MandirLogo } from "@/components/MandirLogo";
import { Alert, Card } from "@/components/ui";
import { describeError } from "@/lib/errors";

interface PublicTemple {
  trustName: string | null;
  deity: string | null;
  address: string | null;
  helpline: string | null;
  timings: string | null;
  announcement: string | null;
  onlineDonations: boolean;
  upcomingEvents: boolean;
  pujaBooking: boolean;
}

/**
 * A trust's public front door (ADR 0017): only what the temple entered, and links only to the
 * services it actually offers right now. All text is rendered as text.
 */
export function TemplePublicPage() {
  const [temple, setTemple] = useState<PublicTemple | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.get<PublicTemple>("/public/temple").then(setTemple).catch((err) => setError(describeError(err)));
  }, []);

  const links = temple
    ? [
        temple.onlineDonations && { href: "/donate", label: "Donate", note: "UPI, cards, netbanking" },
        temple.upcomingEvents && { href: "/upcoming", label: "Upcoming events", note: "Register for a pass" },
        temple.pujaBooking && { href: "/book-puja", label: "Book a puja", note: "Sankalp in your family's name" },
        { href: "/sevak", label: "Offer seva", note: "Volunteer at the temple" },
        { href: "/my-seva", label: "My seva", note: "Your bookings, passes and seva" },
      ].filter(Boolean) as Array<{ href: string; label: string; note: string }>
    : [];

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 px-4 py-10">
      <header className="flex items-center gap-3">
        <MandirLogo className="size-10" />
        <div>
          <h1 className="text-2xl font-semibold">{temple?.trustName ?? "…"}</h1>
          {temple?.deity ? <p className="text-muted">{temple.deity}</p> : null}
        </div>
      </header>
      {error ? <Alert tone="danger">{error}</Alert> : null}
      {temple?.announcement ? <Alert tone="info">{temple.announcement}</Alert> : null}

      {temple && (temple.timings || temple.address || temple.helpline) ? (
        <Card className="flex flex-col gap-1 text-sm">
          {temple.timings ? <p><span className="text-muted">Timings:</span> {temple.timings}</p> : null}
          {temple.address ? <p><span className="text-muted">Address:</span> {temple.address}</p> : null}
          {temple.helpline ? (
            <p><span className="text-muted">Helpline:</span> <a className="underline" href={`tel:${temple.helpline}`}>{temple.helpline}</a></p>
          ) : null}
        </Card>
      ) : null}

      <div className="grid gap-3 sm:grid-cols-2">
        {links.map((l) => (
          <Link key={l.href} href={l.href} className="rounded-[12px] border border-line bg-surface p-4 hover:border-primary">
            <p className="font-medium">{l.label}</p>
            <p className="text-sm text-muted">{l.note}</p>
          </Link>
        ))}
      </div>

      <footer className="pt-6 text-xs text-muted">
        <Link href="/login" className="underline">Staff sign in</Link>
      </footer>
    </main>
  );
}
