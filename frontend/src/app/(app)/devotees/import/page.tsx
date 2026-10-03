"use client";

import Link from "next/link";
import { useState } from "react";

import { api } from "@/components/apiClient";
import { useMe } from "@/components/Session";
import { Alert, Button, Card, PageHeader } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import { hasRole, type ImportResult, type ImportRowError } from "@/lib/types";

/** Columns the import expects (DevoteeCsv.COLUMNS on the backend). */
const COLUMNS = ["fullName", "phone", "email", "addressLine", "city", "state", "pincode", "dateOfBirth", "consentSource"];
const MAX_BYTES = 2 * 1024 * 1024;

export default function ImportDevoteesPage() {
  const me = useMe();
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [rows, setRows] = useState<readonly ImportRowError[]>([]);

  if (!hasRole(me.role, "TRUST_ADMIN")) {
    return <Alert tone="warning">Only trust admins can import devotees.</Alert>;
  }

  function downloadTemplate() {
    const blob = new Blob([`${COLUMNS.join(",")}\r\n`], { type: "text/csv;charset=utf-8" });
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
      setError("Choose a CSV file first.");
      return;
    }
    if (file.size > MAX_BYTES) {
      setError("That file is too large. The limit is 2 MB.");
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
      setError(describeError(err));
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
        ← All devotees
      </Link>
      <PageHeader
        title="Import devotees"
        description="Add many devotees at once from a CSV file. Every row is checked with the same rules as the form; if any row has an error, nothing is imported."
      />

      <Card className="flex max-w-3xl flex-col gap-4">
        <div className="text-sm">
          <p className="font-medium">File format</p>
          <p className="mt-1 text-muted">
            CSV (UTF-8) with a header row and these columns. Only <span className="font-mono">fullName</span> and{" "}
            <span className="font-mono">consentSource</span> are required on each row.
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
            Download empty template
          </Button>
        </div>

        <form onSubmit={onSubmit} className="flex flex-col gap-3">
          <label htmlFor="csv-file" className="text-sm font-medium">
            CSV file
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
              Import
            </Button>
          </div>
        </form>
      </Card>

      {result !== null ? (
        <Alert tone="success" title="Import complete">
          {result === 1 ? "1 devotee was added." : `${result} devotees were added.`}{" "}
          <Link href="/devotees" className="underline">
            View devotees
          </Link>
        </Alert>
      ) : null}
      {error ? <Alert tone="danger">{error}</Alert> : null}

      {rows.length > 0 ? (
        <Card className="max-w-3xl overflow-x-auto p-0">
          <table className="w-full text-left text-sm">
            <caption className="px-4 pt-4 text-left font-medium">Rows to fix</caption>
            <thead className="border-b border-line text-xs uppercase tracking-wide text-muted">
              <tr>
                <th scope="col" className="px-4 py-3 font-medium">Line</th>
                <th scope="col" className="px-4 py-3 font-medium">Column</th>
                <th scope="col" className="px-4 py-3 font-medium">Problem</th>
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
