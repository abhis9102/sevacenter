"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { MaskedNote } from "@/components/MaskedNote";
import { useLanguage } from "@/components/LanguageProvider";
import { useMe } from "@/components/Session";
import { Alert, Button, Card, PageHeader, Spinner } from "@/components/ui";
import { describeError } from "@/lib/errors";
import { pageRange } from "@/lib/format";
import { hasRole, type DevoteePage } from "@/lib/types";

const PAGE_SIZE = 25;

/*
 * Search terms (which can be a phone number or email) stay in memory, not in the page URL, so
 * they don't end up in browser history or bookmarks.
 */
export default function DevoteesPage() {
  const me = useMe();
  const { t } = useLanguage();
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const [draft, setDraft] = useState("");
  const [data, setData] = useState<DevoteePage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

  const canEdit = hasRole(me.role, "LEADER");
  const isAdmin = hasRole(me.role, "TRUST_ADMIN");

  useEffect(() => {
    let cancelled = false;
    const search = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
    if (q) {
      search.set("q", q);
    }
    api
      .get<DevoteePage>(`/devotees?${search.toString()}`)
      .then((d) => {
        if (!cancelled) {
          setData(d);
          setError(null);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(describeError(err));
        }
      });
    return () => {
      cancelled = true;
    };
  }, [q, page]);

  function go(nextQ: string, nextPage: number) {
    setQ(nextQ);
    setPage(nextPage);
  }

  async function exportCsv() {
    setExporting(true);
    setExportError(null);
    try {
      const res = await api.request<Response>("/devotees/export", { raw: true, accept: "text/csv, */*" });
      const blob = await res.blob();
      const name = filenameFrom(res.headers.get("content-disposition")) ?? "devotees.csv";
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = name;
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (err) {
      setExportError(describeError(err));
    } finally {
      setExporting(false);
    }
  }

  const range = data ? pageRange(data.page, data.size, data.total) : null;
  const masked = data?.items.some((d) => d.masked) || me.role === "MEMBER";

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={t.devotees.title}
        description={t.devotees.description}
        actions={
          <>
            {isAdmin ? (
              <>
                <Button variant="secondary" onClick={exportCsv} busy={exporting}>
                  {exporting ? t.devotees.exporting : t.devotees.exportCsv}
                </Button>
                <Link href="/devotees/import" className="inline-flex items-center rounded-[10px] border border-line bg-surface px-3.5 py-2 text-sm font-medium hover:border-primary">
                  {t.devotees.importCsv}
                </Link>
              </>
            ) : null}
            {canEdit ? (
              <Link href="/devotees/new" className="inline-flex items-center rounded-[10px] bg-primary px-3.5 py-2 text-sm font-medium text-on-primary hover:bg-primary-strong">
                {t.devotees.addDevotee}
              </Link>
            ) : null}
          </>
        }
      />

      {exportError ? <Alert tone="danger">{exportError}</Alert> : null}
      {isAdmin ? (
        <p className="-mt-3 text-xs text-muted">
          Exports contain personal data: store them securely and delete them when you&apos;re done.
        </p>
      ) : null}

      {masked ? <MaskedNote /> : null}

      <form
        role="search"
        className="flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          go(draft.trim(), 0);
        }}
      >
        <label htmlFor="devotee-search" className="sr-only">
          {t.common.search}
        </label>
        <input
          id="devotee-search"
          type="search"
          value={draft}
          maxLength={100}
          onChange={(e) => setDraft(e.target.value)}
          placeholder={me.role === "MEMBER" ? t.devotees.searchPlaceholderMember : t.devotees.searchPlaceholderLeader}
          className="min-w-0 flex-1 rounded-[10px] border border-line bg-surface px-3 py-2 placeholder:text-muted focus:border-primary focus:outline-none"
        />
        <Button type="submit">{t.devotees.searchButton}</Button>
        {q ? (
          <Button
            variant="ghost"
            onClick={() => {
              setDraft("");
              go("", 0);
            }}
          >
            {t.devotees.clearSearch}
          </Button>
        ) : null}
      </form>

      {error ? <Alert tone="danger">{error}</Alert> : null}
      {!data && !error ? (
        <p className="flex items-center gap-2 text-muted">
          <Spinner /> {t.common.loading}
        </p>
      ) : null}

      {data ? (
        <>
          <Card className="overflow-x-auto p-0">
            {data.items.length === 0 ? (
              <p className="px-4 py-10 text-center text-muted">
                {q ? t.devotees.emptyState : t.devotees.emptyState}
              </p>
            ) : (
              <table className="w-full min-w-[40rem] text-left text-sm">
                <thead className="border-b border-line text-xs uppercase tracking-wide text-muted">
                  <tr>
                    <th scope="col" className="px-4 py-3 font-medium">{t.devotees.colName}</th>
                    <th scope="col" className="px-4 py-3 font-medium">{t.devotees.colPhone}</th>
                    <th scope="col" className="px-4 py-3 font-medium">{t.devotees.colEmail}</th>
                    <th scope="col" className="px-4 py-3 font-medium">{t.devotees.colCity}</th>
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((d) => (
                    <tr key={d.id} className="border-b border-line last:border-0 hover:bg-surface-2">
                      <td className="px-4 py-3">
                        <Link href={`/devotees/${d.id}`} className="font-medium text-primary-strong hover:underline">
                          {d.fullName}
                        </Link>
                      </td>
                      <td className="px-4 py-3 font-mono text-xs">{d.phone ?? <span className="text-muted">—</span>}</td>
                      <td className="px-4 py-3">{d.email ?? <span className="text-muted">—</span>}</td>
                      <td className="px-4 py-3">{[d.city, d.state].filter(Boolean).join(", ") || <span className="text-muted">—</span>}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </Card>

          {range && data.total > 0 ? (
            <nav aria-label="Pages" className="flex items-center justify-between gap-4 text-sm">
              <p className="text-muted">
                {t.devotees.pageShowing(range.from, range.to, data.total)}
              </p>
              <div className="flex gap-2">
                <Button variant="secondary" disabled={data.page <= 0} onClick={() => go(q, data.page - 1)}>
                  {t.devotees.prev}
                </Button>
                <Button variant="secondary" disabled={data.page + 1 >= range.pages} onClick={() => go(q, data.page + 1)}>
                  {t.devotees.next}
                </Button>
              </div>
            </nav>
          ) : null}
        </>
      ) : null}
    </div>
  );
}

/** `attachment; filename="devotees-2026-10-03.csv"` -> the name, if it's a plain safe one. */
function filenameFrom(disposition: string | null): string | null {
  const m = disposition?.match(/filename="?([A-Za-z0-9._-]+)"?/);
  return m?.[1] ?? null;
}
