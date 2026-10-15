"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { useLanguage } from "@/components/LanguageProvider";
import { Badge, Card } from "@/components/ui";

type Relation = "HEAD" | "SPOUSE" | "CHILD" | "PARENT" | "SIBLING" | "GRANDPARENT" | "GRANDCHILD" | "OTHER";

interface MyHousehold {
  name: string;
  gotra: string | null;
  members: { name: string; relation: Relation }[];
}

interface MyGroup {
  groupId: number;
  name: string;
  meets: string | null;
  status: "PENDING" | "ACTIVE";
}

/**
 * My Mandir's family and groups (ADR 0037, 0038). The family comes from the temple office's
 * records, matched by this devotee's verified contacts: names and relations only, read-only.
 */
export function FamilyAndGroupsCard() {
  const { t } = useLanguage();
  const f = t.mandir.family;
  const [household, setHousehold] = useState<MyHousehold | null | undefined>(undefined);
  const [groups, setGroups] = useState<MyGroup[] | null>(null);

  useEffect(() => {
    // 204 = not linked to a household yet.
    api.get<MyHousehold | undefined>("/portal/household").then((h) => setHousehold(h ?? null)).catch(() => setHousehold(null));
    api.get<MyGroup[]>("/portal/groups").then(setGroups).catch(() => setGroups([]));
  }, []);

  return (
    <>
      <Card className="flex flex-col gap-2">
        <h2 className="text-base font-semibold">{f.title}</h2>
        {household === undefined ? null : household === null ? (
          <p className="text-sm text-muted">{f.none}</p>
        ) : (
          <>
            <p className="text-sm"><span className="font-semibold">{household.name}</span>
              {household.gotra ? <span className="text-muted"> · {f.gotra}: {household.gotra}</span> : null}</p>
            <ul className="flex flex-col gap-1 text-sm">
              {household.members.map((m, i) => (
                <li key={i} className="flex justify-between gap-2"><span>{m.name}</span><span className="text-muted">{f.relations[m.relation]}</span></li>
              ))}
            </ul>
          </>
        )}
        <p className="text-xs text-muted">{f.office}</p>
      </Card>
      <Card className="flex flex-col gap-2">
        <h2 className="text-base font-semibold">{f.groupsTitle}</h2>
        {groups?.length === 0 ? <p className="text-sm text-muted">{f.noGroups}</p> : null}
        <ul className="flex flex-col gap-1 text-sm">
          {groups?.map((g) => (
            <li key={g.groupId} className="flex items-center justify-between gap-2">
              <span>{g.name}{g.meets ? <span className="text-muted"> · {g.meets}</span> : null}</span>
              {g.status === "PENDING" ? <Badge tone="warning">{t.mandir.groups.pending}</Badge> : null}
            </li>
          ))}
        </ul>
        <Link href="/community" className="text-sm font-semibold text-primary-strong">{f.browse} →</Link>
      </Card>
    </>
  );
}
