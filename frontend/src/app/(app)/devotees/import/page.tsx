"use client";

import Link from "next/link";
import { useState } from "react";

import { api } from "@/components/apiClient";
import { useLanguage } from "@/components/LanguageProvider";
import { useMe } from "@/components/Session";
import { Alert, Button, Card, PageHeader } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import { hasRole, type ImportResult, type ImportRowError } from "@/lib/types";

/** Columns the import expects (DevoteeCsv.COLUMNS on the backend). */
const COLUMNS = ["fullName", "phone", "email", "addressLine", "city", "state", "pincode", "dateOfBirth", "consentSource"];
const MAX_BYTES = 2 * 1024 * 1024;

export default function ImportDevoteesPage() {
  const me = useMe();
  const { t } = useLanguage();
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [rows, setRows] = useState<readonly ImportRowError[]>([]);

  if (!hasRole(me.role, "TRUST_ADMIN")) {
    return <Alert tone="warning">Only trust admins can import devotees.</Alert>;
  }

  function downloadTemplate() {
    const sample = [
      COLUMNS.join(","),
      "Lakshmi Iyer,9876543210,lakshmi@example.org,12 Temple Street,Pune,Maharashtra,411001,1980-05-14,IN_PERSON",
    ].join("\r\n");
    const blob = new Blob([`${sample}\r\n`], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "devotees-template.csv";
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setResult(null);
    setError(null);
    setRows([]);
    if (!file) {
      setError(t.devotees.importPage.chooseFileError);
      return;
    }
    if (file.size > MAX_BYTES) {
      setError(t.errors.upload_too_large ?? "That file is too large. The limit is 2 MB.");
      return;
    }
    const body = new FormData();
    body.append("file", file, file.name);
    setBusy(true);
    try {
      const res = await api.request<ImportResult>("/devotees/import", { method: "POST", multipart: body });
      setResult(res.imported);
      setFile(null);
    } catch (err) {
      setError(describeError(err, t.errors));
      if (err instanceof ApiError) {
        setRows(err.rows);
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <Link href="/devotees" className="text-sm text-primary-strong hover:underline">
        {t.devotees.importPage.backLink}
      </Link>
      <PageHeader
        title={t.devotees.importPage.title}
        description={t.devotees.importPage.description}
      />

      <Card className="flex max-w-3xl flex-col gap-4">
        <div className="text-sm">
          <p className="font-medium">{t.devotees.importPage.formatTitle}</p>
          <p className="mt-1 text-muted">
            {t.devotees.importPage.formatDescription}
          </p>
          <p className="mt-2 break-words font-mono text-xs">{COLUMNS.join(", ")}</p>
          <ul className="mt-2 list-disc pl-5 text-muted">
            <li>
              <span className="font-mono">consentSource</span>: IN_PERSON, PHONE, ONLINE_FORM or WRITTEN
            </li>
            <li>
              <span className="font-mono">dateOfBirth</span>: YYYY-MM-DD
            </li>
            <li>A file exported from SevaCenter can be imported as is; each row becomes a new devotee.</li>
          </ul>
          <Button variant="ghost" className="mt-2 -ml-3" onClick={downloadTemplate}>
            {t.devotees.importPage.downloadTemplate}
          </Button>
        </div>

        <form onSubmit={onSubmit} className="flex flex-col gap-3">
          <label htmlFor="csv-file" className="text-sm font-medium">
            {t.devotees.importPage.fileLabel}
          </label>
          <input
            id="csv-file"
            type="file"
            accept=".csv,text/csv"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            className="text-sm file:mr-3 file:rounded-[10px] file:border file:border-line file:bg-surface-2 file:px-3 file:py-1.5 file:text-fg"
          />
          <div>
            <Button type="submit" busy={busy} disabled={!file}>
              {busy ? t.devotees.importPage.importing : t.devotees.importPage.importButton}
            </Button>
          </div>
        </form>
      </Card>

      {result !== null ? (
        <Alert tone="success" title={t.common.success}>
          {result === 1 ? t.devotees.importPage.importCompleteSingle : t.devotees.importPage.importCompleteMultiple(result)}{" "}
          <Link href="/devotees" className="underline">
            {t.devotees.importPage.viewDevotees}
          </Link>
        </Alert>
      ) : null}
      {error ? <Alert tone="danger">{error}</Alert> : null}

      {rows.length > 0 ? (
        <Card className="max-w-3xl overflow-x-auto p-0">
          <table className="w-full text-left text-sm">
            <caption className="px-4 pt-4 text-left font-medium">{t.devotees.importPage.rowsToFix}</caption>
            <thead className="border-b border-line text-xs uppercase tracking-wide text-muted">
              <tr>
                <th scope="col" className="px-4 py-3 font-medium">{t.devotees.importPage.colLine}</th>
                <th scope="col" className="px-4 py-3 font-medium">{t.devotees.importPage.colColumn}</th>
                <th scope="col" className="px-4 py-3 font-medium">{t.devotees.importPage.colProblem}</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r, i) => (
                <tr key={`${r.line}-${r.field}-${i}`} className="border-b border-line last:border-0">
                  <td className="px-4 py-2 font-mono">{r.line}</td>
                  <td className="px-4 py-2 font-mono">{r.field}</td>
                  <td className="px-4 py-2">{r.message}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      ) : null}
    </div>
  );
}
