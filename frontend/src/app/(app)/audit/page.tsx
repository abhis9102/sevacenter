"use client";

import { useCallback, useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { useMe } from "@/components/Session";
import { Alert, Button, Card, PageHeader } from "@/components/ui";
import { describeError } from "@/lib/errors";
import { formatDateTime } from "@/lib/format";
import { hasRole } from "@/lib/types";

interface AuditItem {
  id: number;
  at: string;
  action: string;
  actor: string;
  targetType: string | null;
  targetId: number | null;
  detail: string | null;
}

interface AuditPage {
  items: AuditItem[];
  page: number;
  size: number;
  total: number;
}

const ACTIONS = [
  "USER_INVITED", "USER_ROLE_CHANGED", "USER_DEACTIVATED", "USER_DELETED", "USER_SETUP_LINK_REISSUED",
  "USER_RESET_LINK_ISSUED", "DEVOTEE_CREATED", "DEVOTEE_UPDATED", "DEVOTEE_ERASED", "DEVOTEES_IMPORTED",
  "DEVOTEES_EXPORTED", "DONATION_RECORDED", "DONATION_REVERSED", "RECEIPT_ISSUED", "TRUST_PROFILE_SAVED",
  "PAYMENT_SETTINGS_SAVED", "EVENT_STATUS_CHANGED", "PUJA_SAVED", "PUJA_BOOKING_CANCELLED", "SEVAK_REVIEWED",
  "TEMPLE_PAGE_SAVED", "USER_ACCESS_CHANGED", "FUND_SAVED",
] as const;

const SIZE = 50;

function label(action: string): string {
  const s = action.toLowerCase().replace(/_/g, " ");
  return s.charAt(0).toUpperCase() + s.slice(1);
}

/** The trust's audit log (ADR 0020): who did what and when. Read-only, TRUST_ADMIN only. */
export default function AuditPageView() {
  const me = useMe();
  const isAdmin = hasRole(me.role, "TRUST_ADMIN");
  const [data, setData] = useState<AuditPage | null>(null);
  const [page, setPage] = useState(0);
  const [action, setAction] = useState("");
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (p: number, a: string) => {
    try {
      const q = new URLSearchParams({ page: String(p), size: String(SIZE) });
      if (a) q.set("action", a);
      setData(await api.get<AuditPage>(`/audit?${q.toString()}`));
      setError(null);
    } catch (err) {
      setError(describeError(err));
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- data load on filter/page change
    if (isAdmin) void load(page, action);
  }, [isAdmin, load, page, action]);

  if (!isAdmin) {
    return <Alert tone="danger">Only trust admins can see the audit log.</Alert>;
  }

  const pages = data ? Math.max(1, Math.ceil(data.total / SIZE)) : 1;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Audit log"
                  description="Every staff action on people, money and access, newest first. Entries can't be edited or deleted." />
      <div className="flex flex-wrap items-center gap-2">
        <label htmlFor="audit-action" className="text-sm text-muted">Show</label>
        <select id="audit-action" className="rounded-[8px] border border-line bg-surface px-3 py-2 text-sm"
                value={action} onChange={(e) => { setPage(0); setAction(e.target.value); }}>
          <option value="">All actions</option>
          {ACTIONS.map((a) => <option key={a} value={a}>{label(a)}</option>)}
        </select>
      </div>
      {error ? <Alert tone="danger">{error}</Alert> : null}
      <Card className="overflow-x-auto p-0">
        <table className="w-full text-sm">
          <thead className="text-left text-muted">
            <tr>
              <th className="px-4 py-2 font-medium">When</th>
              <th className="px-4 py-2 font-medium">Who</th>
              <th className="px-4 py-2 font-medium">What</th>
              <th className="px-4 py-2 font-medium">On</th>
              <th className="px-4 py-2 font-medium">Detail</th>
            </tr>
          </thead>
          <tbody>
            {data?.items.map((e) => (
              <tr key={e.id} className="border-t border-line">
                <td className="whitespace-nowrap px-4 py-2">{formatDateTime(e.at)}</td>
                <td className="px-4 py-2">{e.actor}</td>
                <td className="px-4 py-2">{label(e.action)}</td>
                <td className="px-4 py-2 text-muted">{e.targetType ? `${e.targetType}${e.targetId ? ` #${e.targetId}` : ""}` : ""}</td>
                <td className="px-4 py-2 text-muted">{e.detail ?? ""}</td>
              </tr>
            ))}
            {data && data.items.length === 0 ? (
              <tr><td colSpan={5} className="px-4 py-6 text-center text-muted">Nothing recorded yet.</td></tr>
            ) : null}
          </tbody>
        </table>
      </Card>
      {data && data.total > SIZE ? (
        <div className="flex items-center gap-3 text-sm">
          <Button variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Newer</Button>
          <span className="text-muted">Page {page + 1} of {pages}</span>
          <Button variant="secondary" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Older</Button>
        </div>
      ) : null}
    </div>
  );
}
