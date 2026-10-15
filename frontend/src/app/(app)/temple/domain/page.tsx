"use client";

import { CustomDomainCard } from "@/components/CustomDomainCard";
import { useMe } from "@/components/Session";
import { TempleTabs } from "@/components/TempleTabs";
import { Card, PageHeader } from "@/components/ui";
import { hasRole } from "@/lib/types";

/** Mandir Center → Your own domain (ADR 0036). Trust admins only. */
export default function TempleDomainPage() {
  const me = useMe();
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Mandir Center" description="Show your temple's public pages at your own web address." />
      <TempleTabs />
      {hasRole(me.role, "TRUST_ADMIN") ? <CustomDomainCard />
        : <Card className="text-sm text-muted">Only the trust admin can connect a domain.</Card>}
    </div>
  );
}
