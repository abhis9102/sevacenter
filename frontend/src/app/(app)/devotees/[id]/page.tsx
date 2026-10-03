"use client";

import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { DevoteeForm } from "@/components/DevoteeForm";
import { MaskedNote } from "@/components/MaskedNote";
import { useLanguage } from "@/components/LanguageProvider";
import { useMe } from "@/components/Session";
import { Alert, Button, Card, Dialog, PageHeader, Spinner } from "@/components/ui";
import { describeError } from "@/lib/errors";
import { formatDate, formatDateTime } from "@/lib/format";
import { CONSENT_LABELS, hasRole, type Devotee } from "@/lib/types";

export default function DevoteePageWrapper() {
  return (
    <Suspense>
      <DevoteeDetail />
    </Suspense>
  );
}

function DevoteeDetail() {
  const me = useMe();
  const { t } = useLanguage();
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const search = useSearchParams();
  const id = /^\d{1,18}$/.test(params.id) ? params.id : null;

  const [devotee, setDevotee] = useState<Devotee | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [notice, setNotice] = useState<string | null>(search.get("created") ? t.common.success : null);
  const [confirmErase, setConfirmErase] = useState(false);
  const [erasing, setErasing] = useState(false);
  const [eraseError, setEraseError] = useState<string | null>(null);

  const canEdit = hasRole(me.role, "LEADER");
  const canErase = hasRole(me.role, "TRUST_ADMIN");

  // The "just created" flag is one-shot: drop it from the URL so a reload or a shared link
  // doesn't repeat the message.
  useEffect(() => {
    if (id && search.get("created")) {
      router.replace(`/devotees/${id}`, { scroll: false });
    }
  }, [id, search, router]);

  useEffect(() => {
    if (!id) {
      return;
    }
    let cancelled = false;
    api
      .get<Devotee>(`/devotees/${id}`)
      .then((d) => !cancelled && setDevotee(d))
      .catch((err) => !cancelled && setError(describeError(err, t.errors)));
    return () => {
      cancelled = true;
    };
  }, [id, t.errors]);

  async function erase() {
    if (!devotee) {
      return;
    }
    setErasing(true);
    setEraseError(null);
    try {
      await api.request<void>(`/devotees/${devotee.id}`, { method: "DELETE" });
      router.replace("/devotees");
    } catch (err) {
      setEraseError(describeError(err, t.errors));
      setErasing(false);
    }
  }

  if (!id) {
    return <Alert tone="danger">{t.errors.not_found ?? "That isn't a valid devotee link."}</Alert>;
  }
  if (error) {
    return (
      <div className="flex flex-col gap-4">
        <Alert tone="danger">{error}</Alert>
        <BackLink label={t.devotees.importPage.backLink} />
      </div>
    );
  }
  if (!devotee) {
    return (
      <p className="flex items-center gap-2 text-muted">
        <Spinner /> {t.common.loading}
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <BackLink label={t.devotees.importPage.backLink} />
      <PageHeader
        title={devotee.fullName}
        actions={
          !editing ? (
            <>
              {canEdit && !devotee.masked ? (
                <Button variant="secondary" onClick={() => setEditing(true)}>
                  {t.common.edit}
                </Button>
              ) : null}
              {canErase ? (
                <Button variant="danger" onClick={() => setConfirmErase(true)}>
                  {t.devotees.detail.eraseButton}
                </Button>
              ) : null}
            </>
          ) : null
        }
      />
      {notice ? <Alert tone="success">{notice}</Alert> : null}
      {devotee.masked ? <MaskedNote /> : null}

      {editing ? (
        <Card className="max-w-3xl">
          <DevoteeForm
            devotee={devotee}
            onSaved={(d) => {
              setDevotee(d);
              setEditing(false);
              setNotice(t.common.success);
            }}
            onCancel={() => setEditing(false)}
          />
        </Card>
      ) : (
        <div className="grid gap-6 lg:grid-cols-3">
          <Card className="lg:col-span-2">
            <h2 className="mb-4 text-lg">{t.devotees.detail.contactDetails}</h2>
            <dl className="grid gap-x-6 gap-y-4 sm:grid-cols-2">
              <Field label={t.devotees.colPhone} value={devotee.phone} mono />
              <Field label={t.devotees.colEmail} value={devotee.email} />
              <Field label={t.devotees.form.addressLabel} value={devotee.addressLine} hidden={devotee.masked} wide hiddenLabel={t.devotees.contactHiddenNote} />
              <Field label={t.devotees.colCity} value={devotee.city} />
              <Field label={t.devotees.colState} value={devotee.state} />
              <Field label={t.devotees.colPincode} value={devotee.pincode} hidden={devotee.masked} mono hiddenLabel={t.devotees.contactHiddenNote} />
              <Field label={t.devotees.colDob} value={formatDate(devotee.dateOfBirth)} hidden={devotee.masked} hiddenLabel={t.devotees.contactHiddenNote} />
            </dl>
          </Card>
          <Card>
            <h2 className="mb-4 text-lg">{t.devotees.detail.consentHeading}</h2>
            <dl className="grid gap-4">
              <Field
                label={t.devotees.colConsent}
                value={devotee.consentSource ? t.consent[devotee.consentSource] ?? CONSENT_LABELS[devotee.consentSource] ?? devotee.consentSource : null}
              />
              <Field label={t.devotees.detail.consentRecordedAt} value={formatDateTime(devotee.consentGivenAt)} />
              <Field label={t.devotees.detail.createdAt} value={formatDateTime(devotee.createdAt)} />
              <Field label={t.devotees.detail.updatedAt} value={formatDateTime(devotee.updatedAt)} />
            </dl>
          </Card>
        </div>
      )}

      <Dialog open={confirmErase} onClose={() => setConfirmErase(false)} title={t.devotees.detail.eraseConfirmTitle}>
        <p>
          {t.devotees.detail.eraseConfirmMessage}
        </p>
        <p className="text-sm text-muted">
          {t.common.permanentAction}
        </p>
        {eraseError ? <Alert tone="danger">{eraseError}</Alert> : null}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setConfirmErase(false)}>
            {t.common.cancel}
          </Button>
          <Button variant="danger" busy={erasing} onClick={erase}>
            {t.devotees.detail.eraseConfirmAction}
          </Button>
        </div>
      </Dialog>
    </div>
  );
}

function BackLink({ label }: { label: string }) {
  return (
    <Link href="/devotees" className="text-sm text-primary-strong hover:underline">
      {label}
    </Link>
  );
}

function Field({
  label,
  value,
  hidden = false,
  mono = false,
  wide = false,
  hiddenLabel = "Hidden for your role",
}: {
  label: string;
  value: string | null | undefined;
  hidden?: boolean;
  mono?: boolean;
  wide?: boolean;
  hiddenLabel?: string;
}) {
  return (
    <div className={wide ? "sm:col-span-2" : ""}>
      <dt className="text-xs uppercase tracking-wide text-muted">{label}</dt>
      <dd className={`mt-0.5 ${mono && !hidden && value ? "font-mono text-sm" : ""}`}>
        {hidden ? (
          <span className="text-muted italic">{hiddenLabel}</span>
        ) : value ? (
          value
        ) : (
          <span className="text-muted">—</span>
        )}
      </dd>
    </div>
  );
}
