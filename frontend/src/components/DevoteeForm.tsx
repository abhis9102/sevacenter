"use client";

import { useState } from "react";

import { api } from "@/components/apiClient";
import { useLanguage } from "@/components/LanguageProvider";
import { Alert, Button, SelectField, TextField } from "@/components/ui";
import {
  clientFieldErrors,
  EMPTY_DEVOTEE,
  toDevoteeInput,
  valuesFromDevotee,
  type DevoteeFormValues,
} from "@/lib/devoteeForm";
import { ApiError, describeError } from "@/lib/errors";
import { CONSENT_LABELS, CONSENT_SOURCES, type Devotee } from "@/lib/types";

/** Create (POST) or edit (PUT) a devotee. Server validation errors appear next to each field. */
export function DevoteeForm({
  devotee,
  onSaved,
  onCancel,
}: {
  devotee?: Devotee;
  onSaved: (d: Devotee) => void;
  onCancel: () => void;
}) {
  const { t } = useLanguage();
  const mode = devotee ? "edit" : "create";
  const [values, setValues] = useState<DevoteeFormValues>(devotee ? valuesFromDevotee(devotee) : EMPTY_DEVOTEE);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const consentOptions = CONSENT_SOURCES.map((c) => ({
    value: c,
    label: t.consent[c] ?? CONSENT_LABELS[c],
  }));

  function bind(name: keyof DevoteeFormValues) {
    return {
      value: values[name],
      error: fields[name],
      onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
        setValues((v) => ({ ...v, [name]: e.target.value })),
    };
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    const local = clientFieldErrors(values, mode);
    setFields(local);
    if (Object.keys(local).length > 0) {
      return;
    }
    setBusy(true);
    try {
      const body = toDevoteeInput(values, mode);
      const saved = devotee
        ? await api.request<Devotee>(`/devotees/${devotee.id}`, { method: "PUT", json: body })
        : await api.request<Devotee>("/devotees", { method: "POST", json: body });
      onSaved(saved);
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fields).length > 0) {
        setFields({ ...err.fields });
      }
      setError(describeError(err, t.errors));
      setBusy(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-5" noValidate>
      {error ? <Alert tone="danger">{error}</Alert> : null}
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          label={t.devotees.form.fullNameLabel}
          placeholder={t.devotees.form.fullNamePlaceholder}
          required
          maxLength={120}
          autoComplete="off"
          className="sm:col-span-2"
          {...bind("fullName")}
        />
        <TextField
          label={t.devotees.form.phoneLabel}
          placeholder={t.devotees.form.phonePlaceholder}
          type="tel"
          maxLength={30}
          autoComplete="off"
          hint="Indian numbers can be typed as 98765 43210."
          {...bind("phone")}
        />
        <TextField
          label={t.devotees.form.emailLabel}
          placeholder={t.devotees.form.emailPlaceholder}
          type="email"
          maxLength={254}
          autoComplete="off"
          {...bind("email")}
        />
        <TextField
          label={t.devotees.form.addressLabel}
          placeholder={t.devotees.form.addressPlaceholder}
          maxLength={200}
          autoComplete="off"
          className="sm:col-span-2"
          {...bind("addressLine")}
        />
        <TextField
          label={t.devotees.form.cityLabel}
          placeholder={t.devotees.form.cityPlaceholder}
          maxLength={80}
          autoComplete="off"
          {...bind("city")}
        />
        <TextField
          label={t.devotees.form.stateLabel}
          placeholder={t.devotees.form.statePlaceholder}
          maxLength={80}
          autoComplete="off"
          {...bind("state")}
        />
        <TextField
          label={t.devotees.form.pincodeLabel}
          placeholder={t.devotees.form.pincodePlaceholder}
          inputMode="numeric"
          maxLength={6}
          autoComplete="off"
          {...bind("pincode")}
        />
        <TextField
          label={t.devotees.form.dobLabel}
          type="date"
          {...bind("dateOfBirth")}
        />
        {mode === "create" ? (
          <SelectField
            label={t.devotees.form.consentLabel}
            required
            placeholder={t.devotees.form.consentHelp}
            options={consentOptions}
            className="sm:col-span-2"
            {...bind("consentSource")}
          />
        ) : null}
      </div>
      {mode === "create" ? (
        <p className="text-xs text-muted">
          {t.devotees.form.newDescription}
        </p>
      ) : null}
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onCancel}>
          {t.common.cancel}
        </Button>
        <Button type="submit" busy={busy}>
          {busy
            ? t.devotees.form.savingButton
            : mode === "create"
            ? t.devotees.addDevotee
            : t.common.save}
        </Button>
      </div>
    </form>
  );
}
