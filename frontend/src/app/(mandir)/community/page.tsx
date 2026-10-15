"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { useLanguage } from "@/components/LanguageProvider";
import { MandirPageTitle, useDevotee } from "@/components/MandirShell";
import { Alert, Badge, Button, Card, Spinner } from "@/components/ui";
import { describeError } from "@/lib/errors";

type Category = "SATSANG" | "BHAJAN" | "YOUTH" | "WOMEN" | "STUDY" | "SEVA" | "OTHER";

interface PublicGroup {
  id: number;
  name: string;
  description: string | null;
  category: Category;
  meets: string | null;
  location: string | null;
  joinPolicy: "OPEN" | "APPROVAL";
  members: number;
  full: boolean;
}

interface MyGroup {
  groupId: number;
  status: "PENDING" | "ACTIVE";
}

/** Groups a devotee can join (ADR 0037). Members see a count, never each other. All text as text. */
export default function CommunityPage() {
  const { t } = useLanguage();
  const g = t.mandir.groups;
  const devotee = useDevotee();
  const [groups, setGroups] = useState<PublicGroup[] | null>(null);
  const [mine, setMine] = useState<Map<number, MyGroup["status"]>>(new Map());
  const [busy, setBusy] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setGroups(await api.get<PublicGroup[]>("/public/groups"));
      if (devotee) {
        const my = await api.get<MyGroup[]>("/portal/groups");
        setMine(new Map(my.map((m) => [m.groupId, m.status])));
      }
    } catch (err) {
      setError(describeError(err));
    }
  }, [devotee]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial data load
    void load();
  }, [load]);

  async function act(id: number, join: boolean) {
    setBusy(id);
    setError(null);
    try {
      await api.request(join ? `/portal/groups/${id}/join` : `/portal/groups/${id}`, { method: join ? "POST" : "DELETE" });
      await load();
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <MandirPageTitle icon="sevak" title={g.title} subtitle={g.subtitle} />
      {error ? <Alert tone="danger">{error}</Alert> : null}
      {groups === null ? <div className="flex justify-center py-12 text-muted"><Spinner /></div> : null}
      {groups?.length === 0 ? <Card className="text-sm text-muted">{g.none}</Card> : null}

      <div className="grid gap-4 sm:grid-cols-2">
        {groups?.map((x) => {
          const status = mine.get(x.id);
          return (
            <Card key={x.id} className="flex flex-col gap-3 p-5">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <h3 className="text-lg font-semibold">{x.name}</h3>
                  <span className="text-xs font-semibold uppercase tracking-wider text-primary-strong">{g.categories[x.category]}</span>
                </div>
                <span className="shrink-0 text-xs text-muted">{x.members} {g.members}</span>
              </div>
              {x.description ? <p className="whitespace-pre-line text-sm leading-relaxed">{x.description}</p> : null}
              <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
                {x.meets ? <><dt className="text-muted">{g.meets}</dt><dd>{x.meets}</dd></> : null}
                {x.location ? <><dt className="text-muted">{g.where}</dt><dd>{x.location}</dd></> : null}
              </dl>
              {x.joinPolicy === "APPROVAL" && !status ? <p className="text-xs text-muted">{g.askToJoin}</p> : null}
              <div className="mt-auto flex flex-wrap items-center gap-2">
                {status === "ACTIVE" ? <Badge tone="success">{g.joined}</Badge> : null}
                {status === "PENDING" ? <Badge tone="warning">{g.pending}</Badge> : null}
                {!devotee ? (
                  <Link href="/my-seva" className="text-sm font-semibold text-primary-strong">{g.signIn} →</Link>
                ) : status ? (
                  <Button variant="secondary" busy={busy === x.id} onClick={() => void act(x.id, false)}>{g.leave}</Button>
                ) : x.full ? (
                  <Badge tone="neutral">{g.full}</Badge>
                ) : (
                  <Button busy={busy === x.id} onClick={() => void act(x.id, true)}>
                    {x.joinPolicy === "OPEN" ? g.join : g.request}
                  </Button>
                )}
              </div>
            </Card>
          );
        })}
      </div>
    </div>
  );
}
