"use client";

import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { DevoteeForm } from "@/components/DevoteeForm";
import { MaskedNote } from "@/components/MaskedNote";
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
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const search = useSearchParams();
  const id = /^\d{1,18}$/.test(params.id) ? params.id : null;

  const [devotee, setDevotee] = useState<Devotee | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [notice, setNotice] = useState<string | null>(search.get("created") ? "Devotee added." : null);
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
      .catch((err) => !cancelled && setError(describeError(err)));
    return () => {
      cancelled = true;
    };
  }, [id]);

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
      setEraseError(describeError(err));
      setErasing(false);
    }
  }

  if (!id) {
    return <Alert tone="danger">That isn&apos;t a valid devotee link.</Alert>;
  }
  if (error) {
    return (
      <div className="flex flex-col gap-4">
        <Alert tone="danger">{error}</Alert>
        <BackLink />
      </div>
    );
  }
  if (!devotee) {
    return (
      <p className="flex items-center gap-2 text-muted">
        <Spinner /> Loading…
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <BackLink />
      <PageHeader
        title={devotee.fullName}
        actions={
          !editing ? (
            <>
              {canEdit && !devotee.masked ? (
                <Button variant="secondary" onClick={() => setEditing(true)}>
                  Edit
                </Button>
              ) : null}
              {canErase ? (
                <Button variant="danger" onClick={() => setConfirmErase(true)}>
                  Erase
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
              setNotice("Changes saved.");
            }}
            onCancel={() => setEditing(false)}
          />
        </Card>
      ) : (
        <div className="grid gap-6 lg:grid-cols-3">
          <Card className="lg:col-span-2">
            <h2 className="mb-4 text-lg">Contact</h2>
            <dl className="grid gap-x-6 gap-y-4 sm:grid-cols-2">
              <Field label="Phone" value={devotee.phone} mono />
              <Field label="Email" value={devotee.email} />
              <Field label="Address" value={devotee.addressLine} hidden={devotee.masked} wide />
              <Field label="City" value={devotee.city} />
              <Field label="State" value={devotee.state} />
              <Field label="Pincode" value={devotee.pincode} hidden={devotee.masked} mono />
              <Field label="Date of birth" value={formatDate(devotee.dateOfBirth)} hidden={devotee.masked} />
            </dl>
          </Card>
          <Card>
            <h2 className="mb-4 text-lg">Record</h2>
            <dl className="grid gap-4">
              <Field
                label="Consent"
                value={devotee.consentSource ? CONSENT_LABELS[devotee.consentSource] ?? devotee.consentSource : null}
              />
              <Field label="Consent recorded" value={formatDateTime(devotee.consentGivenAt)} />
              <Field label="Added" value={formatDateTime(devotee.createdAt)} />
              <Field label="Last changed" value={formatDateTime(devotee.updatedAt)} />
            </dl>
          </Card>
        </div>
      )}

      <Dialog open={confirmErase} onClose={() => setConfirmErase(false)} title="Erase this devotee permanently?">
        <p>
          This permanently deletes <strong>{devotee.fullName}</strong> and all of their details from SevaCenter. It
          can&apos;t be undone, and there is no copy to restore from.
        </p>
        <p className="text-sm text-muted">
          Use this when a devotee asks for their data to be erased. To keep the person but fix details, edit the record
          instead.
        </p>
        {eraseError ? <Alert tone="danger">{eraseError}</Alert> : null}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setConfirmErase(false)}>
            Cancel
          </Button>
          <Button variant="danger" busy={erasing} onClick={erase}>
            Erase permanently
          </Button>
        </div>
      </Dialog>
    </div>
  );
}

function BackLink() {
  return (
    <Link href="/devotees" className="text-sm text-primary-strong hover:underline">
      ← All devotees
    </Link>
  );
}

function Field({
  label,
  value,
  hidden = false,
  mono = false,
  wide = false,
}: {
  label: string;
  value: string | null | undefined;
  hidden?: boolean;
  mono?: boolean;
  wide?: boolean;
}) {
  return (
    <div className={wide ? "sm:col-span-2" : ""}>
      <dt className="text-xs uppercase tracking-wide text-muted">{label}</dt>
      <dd className={`mt-0.5 ${mono && !hidden && value ? "font-mono text-sm" : ""}`}>
        {hidden ? (
          <span className="text-muted italic">Hidden for your role</span>
        ) : value ? (
          value
        ) : (
          <span className="text-muted">—</span>
        )}
      </dd>
    </div>
  );
}
