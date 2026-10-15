"use client";

import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { Alert, Button, Card, TextField } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";

interface Domain {
  id: number;
  hostname: string;
  status: "PENDING" | "ACTIVE" | "FAILED";
  pointsHere: boolean;
  lastError: string | null;
  verifiedAt: string | null;
  lastCheckedAt: string | null;
  txtName: string;
  txtValue: string;
}

interface Domains {
  target: string;
  domains: Domain[];
}

const STATUS: Record<Domain["status"], { label: string; tone: string }> = {
  ACTIVE: { label: "Connected", tone: "bg-success/15 text-success" },
  PENDING: { label: "Waiting for DNS", tone: "bg-haldi/20 text-maroon" },
  FAILED: { label: "Not verified", tone: "bg-danger/15 text-danger" },
};

/** "Your own domain" (ADR 0036): TRUST_ADMIN connects the temple's own domain to its Mandir Center. */
export function CustomDomainCard() {
  const [data, setData] = useState<Domains | null>(null);
  const [hostname, setHostname] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState<number | "add" | null>(null);

  const load = () => api.get<Domains>("/custom-domains").then(setData).catch((e) => setError(describeError(e)));
  useEffect(() => {
    void load();
  }, []);

  async function run(key: number | "add", fn: () => Promise<unknown>, ok?: string) {
    setBusy(key);
    setError(null);
    setNotice(null);
    try {
      await fn();
      await load();
      if (ok) setNotice(ok);
    } catch (e) {
      setError(e instanceof ApiError && e.fields.hostname ? e.fields.hostname : describeError(e));
    } finally {
      setBusy(null);
    }
  }

  return (
    <Card className="flex flex-col gap-3">
      <div>
        <h2 className="text-base font-semibold">Your own domain</h2>
        <p className="text-sm text-muted">
          Show your Mandir Center at your temple&apos;s own address, e.g. <span className="font-mono">www.yourtemple.org</span>.
          Only the public temple pages appear there; staff always sign in here.
        </p>
      </div>
      {error ? <Alert tone="danger">{error}</Alert> : null}
      {notice ? <Alert tone="success">{notice}</Alert> : null}

      {data?.domains.map((d) => (
        <div key={d.id} className="flex flex-col gap-3 rounded-[12px] border border-line p-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="font-mono text-sm font-semibold">{d.hostname}</span>
            <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${STATUS[d.status].tone}`}>
              {STATUS[d.status].label}
            </span>
          </div>
          {d.lastError ? <p className="text-sm text-danger">{d.lastError}</p> : null}
          <p className="text-sm">Add these two records where you manage your domain (GoDaddy, Hostinger, Cloudflare…):</p>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[32rem] text-left text-xs">
              <thead className="text-muted">
                <tr><th className="pb-1 pr-3">Type</th><th className="pb-1 pr-3">Name</th><th className="pb-1">Value</th><th /></tr>
              </thead>
              <tbody className="font-mono">
                <tr className="border-t border-line">
                  <td className="py-1.5 pr-3">TXT</td>
                  <td className="py-1.5 pr-3 break-all">{d.txtName}</td>
                  <td className="py-1.5 break-all">{d.txtValue}</td>
                  <td className="py-1.5 pl-2 font-sans text-muted">proves it&apos;s yours {d.status === "ACTIVE" ? "✓" : ""}</td>
                </tr>
                <tr className="border-t border-line">
                  <td className="py-1.5 pr-3">CNAME</td>
                  <td className="py-1.5 pr-3 break-all">{d.hostname}</td>
                  <td className="py-1.5 break-all">{data.target}</td>
                  <td className="py-1.5 pl-2 font-sans text-muted">sends visitors here {d.pointsHere ? "✓" : ""}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p className="text-xs text-muted">
            A domain without &ldquo;www&rdquo; can&apos;t use CNAME: point its A record at the same address as {data.target}.
            DNS changes can take up to an hour. Keep the TXT record: it&apos;s checked daily, and the domain disconnects
            if it&apos;s removed.
          </p>
          <div className="flex gap-2">
            <Button type="button" variant="secondary" busy={busy === d.id}
                    onClick={() => void run(d.id, () => api.request(`/custom-domains/${d.id}/verify`, { method: "POST" }),
                      "Checked DNS.")}>
              Check now
            </Button>
            <Button type="button" variant="secondary" disabled={busy !== null}
                    onClick={() => void run(d.id, () => api.request(`/custom-domains/${d.id}`, { method: "DELETE" }),
                      `${d.hostname} disconnected.`)}>
              Remove
            </Button>
          </div>
        </div>
      ))}

      {data && data.domains.length < 3 ? (
        <form className="flex max-w-xl items-end gap-2"
              onSubmit={(e) => {
                e.preventDefault();
                void run("add", () => api.request("/custom-domains", { method: "POST", json: { hostname } }))
                  .then(() => setHostname(""));
              }}>
          <div className="flex-1">
            <TextField label="Domain" placeholder="www.yourtemple.org" value={hostname} maxLength={253}
                       onChange={(e) => setHostname(e.target.value)} />
          </div>
          <Button type="submit" busy={busy === "add"} disabled={!hostname.trim()}>Add domain</Button>
        </form>
      ) : null}
    </Card>
  );
}
