"use client";

import { useCallback, useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { useMe } from "@/components/Session";
import { Alert, Badge, Button, Card, PageHeader } from "@/components/ui";
import { describeError } from "@/lib/errors";
import { formatDate } from "@/lib/format";
import { hasRole } from "@/lib/types";

interface Signup {
  id: number;
  fullName: string;
  phone: string | null;
  email: string | null;
  sevaAreas: string;
  availability: string | null;
  notes: string | null;
  status: "NEW" | "APPROVED" | "DECLINED";
  createdAt: string;
}

const TONE = { NEW: "warning", APPROVED: "success", DECLINED: "neutral" } as const;

/** Sevak (volunteer) signups from the public /sevak page (ADR 0015). LEADER+: contacts included. */
export default function VolunteersPage() {
  const me = useMe();
  const isLeader = hasRole(me.role, "LEADER");
  const [signups, setSignups] = useState<Signup[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setSignups(await api.get<Signup[]>("/sevaks"));
    } catch (err) {
      setError(describeError(err));
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial data load
    if (isLeader) void load();
  }, [isLeader, load]);

  async function review(s: Signup, action: "approve" | "decline") {
    try {
      await api.request(`/sevaks/${s.id}/${action}`, { method: "POST" });
      await load();
    } catch (err) {
      setError(describeError(err));
    }
  }

  if (!isLeader) {
    return <Alert tone="warning">Only leaders and trust admins can see volunteer signups.</Alert>;
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Volunteers" description="Devotees who offered seva on the temple's public /sevak page." />
      {error ? <Alert tone="danger">{error}</Alert> : null}
      {signups?.length === 0 ? <Card><p className="text-muted">No signups yet. Share the /sevak page.</p></Card> : null}
      <div className="grid gap-4 md:grid-cols-2">
        {signups?.map((s) => (
          <Card key={s.id} className="flex flex-col gap-1">
            <div className="flex items-start justify-between gap-2">
              <h2 className="font-medium">{s.fullName}</h2>
              <Badge tone={TONE[s.status]}>{s.status.toLowerCase()}</Badge>
            </div>
            <p className="text-sm text-muted">{s.phone ?? s.email} · {formatDate(s.createdAt)}</p>
            <p className="text-sm"><span className="text-muted">Seva:</span> {s.sevaAreas}</p>
            {s.availability ? <p className="text-sm"><span className="text-muted">When:</span> {s.availability}</p> : null}
            {s.notes ? <p className="text-sm text-muted">{s.notes}</p> : null}
            {s.status === "NEW" ? (
              <div className="mt-2 flex gap-2">
                <Button onClick={() => void review(s, "approve")}>Approve</Button>
                <Button variant="secondary" onClick={() => void review(s, "decline")}>Decline</Button>
              </div>
            ) : null}
          </Card>
        ))}
      </div>
    </div>
  );
}
