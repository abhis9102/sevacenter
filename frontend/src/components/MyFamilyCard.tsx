"use client";

import { useCallback, useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { useLanguage } from "@/components/LanguageProvider";
import { Alert, Badge, Button, Card, TextField } from "@/components/ui";
import { describeError } from "@/lib/errors";

type Relation = "HEAD" | "SPOUSE" | "CHILD" | "PARENT" | "SIBLING" | "GRANDPARENT" | "GRANDCHILD" | "OTHER";

interface Family {
  householdId: number | null;
  name: string | null;
  gotra: string | null;
  myRelation: Relation | null;
  canManage: boolean;
  members: { memberId: number; name: string; relation: Relation; child: boolean; dateOfBirth: string | null;
    me: boolean; canEdit: boolean; canRemove: boolean }[];
  sentInvites: { id: number; channel: "EMAIL" | "SMS"; contact: string; relation: Relation; expiresAt: string }[];
  invitations: { id: number; familyName: string; relation: Relation }[];
}

const ADULT_RELATIONS: Relation[] = ["SPOUSE", "PARENT", "SIBLING", "GRANDPARENT", "OTHER"];

/**
 * My Mandir → My family (ADR 0039): start a family, add and edit children, invite adults (they
 * join by accepting), see and answer invitations, leave. Other adults' contacts are never shown.
 */
export function MyFamilyCard() {
  const { t } = useLanguage();
  const f = t.mandir.family;
  const [fam, setFam] = useState<Family | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [mode, setMode] = useState<"child" | "invite" | null>(null);
  const [editing, setEditing] = useState<number | null>(null);
  const [form, setForm] = useState<Record<string, string>>({});
  const set = (k: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setForm({ ...form, [k]: e.target.value });

  const load = useCallback(() => api.get<Family>("/portal/family").then(setFam).catch((e) => setError(describeError(e))), []);
  useEffect(() => {
    void load();
  }, [load]);

  async function run(path: string, method: "POST" | "PUT" | "DELETE", json?: object) {
    setError(null);
    try {
      setFam(await api.request<Family>(path, { method, json }));
      setMode(null);
      setEditing(null);
      setForm({});
    } catch (err) {
      setError(describeError(err));
    }
  }
  const rel = (r: Relation) => f.relations[r];

  if (!fam) return null;
  return (
    <Card className="flex flex-col gap-3">
      <h2 className="text-base font-semibold">{f.title}</h2>
      {error ? <Alert tone="danger">{error}</Alert> : null}

      {fam.invitations.length > 0 ? (
        <div className="flex flex-col gap-2 rounded-[10px] border border-haldi/40 bg-haldi/10 p-3">
          <p className="text-sm font-semibold">{f.invitationsTitle}</p>
          {fam.invitations.map((i) => (
            <div key={i.id} className="flex flex-col gap-2 text-sm">
              <span>{f.invitedYou(i.familyName, rel(i.relation))}</span>
              {fam.householdId === null ? (
                <TextField label={f.yourName} value={form[`name${i.id}`] ?? ""} maxLength={120} onChange={set(`name${i.id}`)} />
              ) : null}
              <div className="flex gap-2">
                <Button onClick={() => void run(`/portal/family/invitations/${i.id}/accept`, "POST",
                  { yourName: form[`name${i.id}`] || null })}>{f.accept}</Button>
                <Button variant="secondary" onClick={() => void run(`/portal/family/invitations/${i.id}/decline`, "POST")}>{f.decline}</Button>
              </div>
            </div>
          ))}
        </div>
      ) : null}

      {fam.householdId === null ? (
        <form method="post" className="flex flex-col gap-2" onSubmit={(e) => {
          e.preventDefault();
          void run("/portal/family", "POST", { yourName: form.yourName || null, familyName: form.familyName, gotra: form.gotra || null });
        }}>
          <p className="text-sm text-muted">{f.startNote}</p>
          <TextField label={f.yourName} value={form.yourName ?? ""} maxLength={120} onChange={set("yourName")} />
          <TextField label={f.familyName} value={form.familyName ?? ""} maxLength={120} onChange={set("familyName")} required />
          <TextField label={f.gotra} value={form.gotra ?? ""} maxLength={60} onChange={set("gotra")} />
          <div><Button type="submit" disabled={!form.familyName?.trim()}>{f.startButton}</Button></div>
        </form>
      ) : (
        <>
          <p className="text-sm"><span className="font-semibold">{fam.name}</span>
            {fam.gotra ? <span className="text-muted"> · {f.gotra}: {fam.gotra}</span> : null}</p>
          <ul className="flex flex-col divide-y divide-line text-sm">
            {fam.members.map((m) => (
              <li key={m.memberId} className="flex flex-col gap-2 py-2">
                {editing === m.memberId ? (
                  <form method="post" className="flex flex-col gap-2" onSubmit={(e) => {
                    e.preventDefault();
                    void run(`/portal/family/members/${m.memberId}`, "PUT",
                      { fullName: form.editName ?? m.name, dateOfBirth: (form.editDob ?? m.dateOfBirth) || null });
                  }}>
                    <TextField label={f.childName} defaultValue={m.name} maxLength={120} onChange={set("editName")} />
                    <TextField label={f.dob} type="date" defaultValue={m.dateOfBirth ?? ""} onChange={set("editDob")} />
                    <div className="flex gap-2"><Button type="submit">{f.save}</Button>
                      <Button type="button" variant="secondary" onClick={() => setEditing(null)}>{f.cancel}</Button></div>
                  </form>
                ) : (
                  <div className="flex items-center justify-between gap-2">
                    <span>{m.name}{m.me ? <span className="text-muted"> ({f.you})</span> : null}
                      <span className="ml-2 text-xs text-muted">{rel(m.relation)}</span></span>
                    <span className="flex gap-1">
                      {m.canEdit ? <Button variant="ghost" onClick={() => setEditing(m.memberId)}>{f.edit}</Button> : null}
                      {m.canRemove && !m.me ? <Button variant="ghost" onClick={() => void run(`/portal/family/members/${m.memberId}`, "DELETE")}>{f.remove}</Button> : null}
                    </span>
                  </div>
                )}
              </li>
            ))}
          </ul>
          {fam.sentInvites.map((i) => (
            <div key={i.id} className="flex items-center justify-between gap-2 text-xs text-muted">
              <span><Badge>{f.invited}</Badge> {i.contact} · {rel(i.relation)}</span>
              <Button variant="ghost" onClick={() => void run(`/portal/family/invites/${i.id}`, "DELETE")}>{f.cancel}</Button>
            </div>
          ))}

          {fam.canManage && mode === null ? (
            <div className="flex flex-wrap gap-2">
              <Button variant="secondary" onClick={() => setMode("child")}>{f.addChild}</Button>
              <Button variant="secondary" onClick={() => setMode("invite")}>{f.invite}</Button>
            </div>
          ) : null}
          {mode === "child" ? (
            <form method="post" className="flex flex-col gap-2" onSubmit={(e) => {
              e.preventDefault();
              void run("/portal/family/children", "POST", { fullName: form.childName, dateOfBirth: form.childDob || null,
                relation: form.childRelation || "CHILD" });
            }}>
              <TextField label={f.childName} value={form.childName ?? ""} maxLength={120} onChange={set("childName")} required />
              <TextField label={f.dob} type="date" value={form.childDob ?? ""} onChange={set("childDob")} />
              <select aria-label="Relation" value={form.childRelation ?? "CHILD"} onChange={set("childRelation")}
                      className="rounded-[10px] border border-line bg-surface px-3 py-2 text-sm">
                <option value="CHILD">{rel("CHILD")}</option><option value="GRANDCHILD">{rel("GRANDCHILD")}</option>
              </select>
              <div className="flex gap-2"><Button type="submit" disabled={!form.childName?.trim()}>{f.add}</Button>
                <Button type="button" variant="secondary" onClick={() => setMode(null)}>{f.cancel}</Button></div>
            </form>
          ) : null}
          {mode === "invite" ? (
            <form method="post" className="flex flex-col gap-2" onSubmit={(e) => {
              e.preventDefault();
              const contact = (form.inviteContact ?? "").trim();
              void run("/portal/family/invites", "POST", { channel: contact.includes("@") ? "EMAIL" : "SMS", contact,
                relation: form.inviteRelation || "SPOUSE" });
            }}>
              <p className="text-xs text-muted">{f.inviteNote}</p>
              <TextField label={f.contactLabel} value={form.inviteContact ?? ""} maxLength={254} onChange={set("inviteContact")} required />
              <select aria-label="Relation" value={form.inviteRelation ?? "SPOUSE"} onChange={set("inviteRelation")}
                      className="rounded-[10px] border border-line bg-surface px-3 py-2 text-sm">
                {ADULT_RELATIONS.map((r) => <option key={r} value={r}>{rel(r)}</option>)}
              </select>
              <div className="flex gap-2"><Button type="submit" disabled={!form.inviteContact?.trim()}>{f.send}</Button>
                <Button type="button" variant="secondary" onClick={() => setMode(null)}>{f.cancel}</Button></div>
            </form>
          ) : null}

          <div className="border-t border-line pt-2">
            <Button variant="ghost" onClick={() => {
              const self = fam.members.find((m) => m.me);
              if (self && window.confirm(f.confirmLeave)) void run(`/portal/family/members/${self.memberId}`, "DELETE");
            }}>{f.leave}</Button>
          </div>
        </>
      )}
      <p className="text-xs text-muted">{f.office}</p>
    </Card>
  );
}
