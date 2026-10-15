"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { useLanguage } from "@/components/LanguageProvider";
import { MyFamilyCard } from "@/components/MyFamilyCard";
import { Badge, Card } from "@/components/ui";

interface MyGroup {
  groupId: number;
  name: string;
  meets: string | null;
  status: "PENDING" | "ACTIVE";
}

/**
 * My Mandir's family (MyFamilyCard, ADR 0039) and groups (ADR 0037).
 */
export function FamilyAndGroupsCard() {
  const { t } = useLanguage();
  const f = t.mandir.family;
  const [groups, setGroups] = useState<MyGroup[] | null>(null);

  useEffect(() => {
    api.get<MyGroup[]>("/portal/groups").then(setGroups).catch(() => setGroups([]));
  }, []);

  return (
    <>
      <MyFamilyCard />
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
