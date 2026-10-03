"use client";

import { useRouter } from "next/navigation";

import { DevoteeForm } from "@/components/DevoteeForm";
import { useLanguage } from "@/components/LanguageProvider";
import { useMe } from "@/components/Session";
import { Alert, Card, PageHeader } from "@/components/ui";
import { hasRole } from "@/lib/types";

export default function NewDevoteePage() {
  const me = useMe();
  const { t } = useLanguage();
  const router = useRouter();

  if (!hasRole(me.role, "LEADER")) {
    return <Alert tone="warning">Only leaders and trust admins can add devotees.</Alert>;
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={t.devotees.addDevotee} description={t.devotees.form.newDescription} />
      <Card className="max-w-3xl">
        <DevoteeForm
          onSaved={(d) => router.replace(`/devotees/${d.id}?created=1`)}
          onCancel={() => router.push("/devotees")}
        />
      </Card>
    </div>
  );
}
