"use client";

import { useCallback, useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { useMe } from "@/components/Session";
import { Alert, Badge, Button, Card, Dialog, PageHeader, SelectField, TextField } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import { hasRole, moduleAccess } from "@/lib/types";

type Category = "SATSANG" | "BHAJAN" | "YOUTH" | "WOMEN" | "STUDY" | "SEVA" | "OTHER";

interface Group {
  id: number;
  name: string;
  description: string | null;
  category: Category;
  meets: string | null;
  location: string | null;
  capacity: number | null;
  visibility: "PUBLIC" | "PRIVATE";
  joinPolicy: "OPEN" | "APPROVAL";
  active: boolean;
  members: number;
}

interface Member {
  id: number;
  status: "PENDING" | "ACTIVE";
  name: string | null;
  channel: "EMAIL" | "SMS" | null;
  contact: string | null;
  requestedAt: string;
  joinedAt: string | null;
}

const CATEGORIES: { value: Category; label: string }[] = [
  { value: "SATSANG", label: "Satsang" }, { value: "BHAJAN", label: "Bhajan mandali" },
  { value: "YOUTH", label: "Youth" }, { value: "WOMEN", label: "Mahila mandal" },
  { value: "STUDY", label: "Study circle" }, { value: "SEVA", label: "Seva" }, { value: "OTHER", label: "Other" },
];
const label = (c: Category) => CATEGORIES.find((x) => x.value === c)?.label ?? c;

const EMPTY = { name: "", description: "", category: "SATSANG" as Category, meets: "", location: "", capacity: "",
  visibility: "PUBLIC" as Group["visibility"], joinPolicy: "OPEN" as Group["joinPolicy"], active: true };

/** Groups (ADR 0037): MEMBER+ see them, LEADER+ run them and see who is in them. */
export default function GroupsPage() {
  const me = useMe();
  const canRun = hasRole(me.role, "LEADER") && moduleAccess(me.moduleLimits, "GROUPS") === "FULL";
  const [groups, setGroups] = useState<Group[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<{ id: number | null; form: typeof EMPTY } | null>(null);
  const [fields, setFields] = useState<Readonly<Record<string, string>>>({});
  const [busy, setBusy] = useState(false);
  const [open, setOpen] = useState<Group | null>(null);

  const load = useCallback(() => api.get<Group[]>("/groups").then(setGroups).catch((e) => setError(describeError(e))), []);
  useEffect(() => {
    void load();
  }, [load]);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (!editing) return;
    setBusy(true);
    setFields({});
    const f = editing.form;
    try {
      await api.request(editing.id ? `/groups/${editing.id}` : "/groups", {
        method: editing.id ? "PUT" : "POST",
        json: { ...f, description: f.description.trim() || null, meets: f.meets.trim() || null,
                location: f.location.trim() || null, capacity: f.capacity ? Number(f.capacity) : null },
      });
      setEditing(null);
      await load();
    } catch (err) {
      if (err instanceof ApiError) setFields(err.fields);
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  const set = (k: keyof typeof EMPTY) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) =>
    editing && setEditing({ ...editing, form: { ...editing.form, [k]: e.target.value } });

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Groups" description="Satsang, bhajan mandali, youth and study groups. Public groups appear on your Mandir Center for devotees to join."
                  actions={canRun ? <Button onClick={() => setEditing({ id: null, form: EMPTY })}>New group</Button> : null} />
      {error ? <Alert tone="danger">{error}</Alert> : null}
      {groups?.length === 0 ? <Card className="text-sm text-muted">No groups yet.</Card> : null}

      <div className="grid gap-4 md:grid-cols-2">
        {groups?.map((g) => (
          <Card key={g.id} className="flex flex-col gap-2">
            <div className="flex items-start justify-between gap-2">
              <div>
                <h2 className="text-base font-semibold">{g.name}</h2>
                <p className="text-xs text-muted">{label(g.category)}{g.meets ? ` · ${g.meets}` : ""}</p>
              </div>
              <div className="flex flex-wrap justify-end gap-1">
                {!g.active ? <Badge>Inactive</Badge> : null}
                <Badge tone={g.visibility === "PUBLIC" ? "primary" : "neutral"}>{g.visibility === "PUBLIC" ? "Public" : "Private"}</Badge>
                <Badge>{g.joinPolicy === "OPEN" ? "Open" : "Approval"}</Badge>
              </div>
            </div>
            {g.description ? <p className="line-clamp-3 whitespace-pre-line text-sm">{g.description}</p> : null}
            <p className="text-sm">{g.members}{g.capacity ? ` / ${g.capacity}` : ""} members</p>
            {canRun ? (
              <div className="flex gap-2">
                <Button variant="secondary" onClick={() => setOpen(g)}>Members</Button>
                <Button variant="secondary" onClick={() => setEditing({ id: g.id, form: {
                  name: g.name, description: g.description ?? "", category: g.category, meets: g.meets ?? "",
                  location: g.location ?? "", capacity: g.capacity ? String(g.capacity) : "", visibility: g.visibility,
                  joinPolicy: g.joinPolicy, active: g.active } })}>Edit</Button>
              </div>
            ) : null}
          </Card>
        ))}
      </div>

      <Dialog open={editing !== null} onClose={() => setEditing(null)} title={editing?.id ? "Edit group" : "New group"}>
        {editing ? (
          <form method="post" onSubmit={save} className="flex flex-col gap-3">
            <TextField label="Name" value={editing.form.name} maxLength={80} onChange={set("name")} error={fields.name} required />
            <SelectField label="Kind" value={editing.form.category} onChange={set("category")} options={CATEGORIES} />
            <label className="flex flex-col gap-1 text-sm">
              <span className="font-medium">About the group</span>
              <textarea rows={3} maxLength={1000} value={editing.form.description} onChange={set("description")}
                        className="rounded-[10px] border border-line bg-surface px-3 py-2" />
            </label>
            <div className="grid gap-3 sm:grid-cols-2">
              <TextField label="When it meets" placeholder="Every Sunday, 6 pm" value={editing.form.meets} maxLength={200} onChange={set("meets")} />
              <TextField label="Where" placeholder="Temple hall" value={editing.form.location} maxLength={200} onChange={set("location")} />
            </div>
            <div className="grid gap-3 sm:grid-cols-3">
              <SelectField label="Who can see it" value={editing.form.visibility} onChange={set("visibility")}
                           options={[{ value: "PUBLIC", label: "Listed on Mandir Center" }, { value: "PRIVATE", label: "Private (you add members)" }]} />
              <SelectField label="Joining" value={editing.form.joinPolicy} onChange={set("joinPolicy")}
                           options={[{ value: "OPEN", label: "Anyone can join" }, { value: "APPROVAL", label: "A leader approves" }]} />
              <TextField label="Size limit (optional)" type="number" min={1} max={10000} value={editing.form.capacity}
                         onChange={set("capacity")} error={fields.capacity} />
            </div>
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={editing.form.active}
                     onChange={(e) => setEditing({ ...editing, form: { ...editing.form, active: e.target.checked } })} />
              Active (inactive groups are hidden from devotees)
            </label>
            <div className="flex justify-between gap-2">
              <Button type="submit" busy={busy}>Save</Button>
              {editing.id ? (
                <Button type="button" variant="secondary" onClick={() => {
                  if (!window.confirm("Delete this group and its member list?")) return;
                  void api.request(`/groups/${editing.id}`, { method: "DELETE" }).then(() => { setEditing(null); return load(); })
                    .catch((err) => setError(describeError(err)));
                }}>Delete group</Button>
              ) : null}
            </div>
          </form>
        ) : null}
      </Dialog>

      {open ? <MembersDialog group={open} onClose={() => { setOpen(null); void load(); }} /> : null}
    </div>
  );
}

function MembersDialog({ group, onClose }: { group: Group; onClose: () => void }) {
  const [members, setMembers] = useState<Member[] | null>(null);
  const [contact, setContact] = useState("");
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(() => api.get<Member[]>(`/groups/${group.id}/members`).then(setMembers)
    .catch((e) => setError(describeError(e))), [group.id]);
  useEffect(() => {
    void load();
  }, [load]);

  async function run(fn: () => Promise<unknown>) {
    setError(null);
    try {
      await fn();
      await load();
    } catch (err) {
      setError(err instanceof ApiError && err.fields.contact ? err.fields.contact : describeError(err));
    }
  }

  const pending = members?.filter((m) => m.status === "PENDING") ?? [];
  const active = members?.filter((m) => m.status === "ACTIVE") ?? [];
  const who = (m: Member) => m.name ?? m.contact ?? "Devotee";

  return (
    <Dialog open onClose={onClose} title={`${group.name}: members`}>
      <div className="flex flex-col gap-4">
        {error ? <Alert tone="danger">{error}</Alert> : null}
        {pending.length > 0 ? (
          <section className="flex flex-col gap-2">
            <h3 className="text-sm font-semibold">Asking to join ({pending.length})</h3>
            {pending.map((m) => (
              <div key={m.id} className="flex items-center justify-between gap-2 rounded-[10px] border border-line p-2 text-sm">
                <span>{who(m)} <span className="text-muted">{m.contact}</span></span>
                <span className="flex gap-1">
                  <Button onClick={() => void run(() => api.request(`/groups/${group.id}/members/${m.id}/approve`, { method: "POST" }))}>Approve</Button>
                  <Button variant="secondary" onClick={() => void run(() => api.request(`/groups/${group.id}/members/${m.id}`, { method: "DELETE" }))}>Decline</Button>
                </span>
              </div>
            ))}
          </section>
        ) : null}
        <section className="flex flex-col gap-2">
          <h3 className="text-sm font-semibold">Members ({active.length}{group.capacity ? ` / ${group.capacity}` : ""})</h3>
          {active.length === 0 ? <p className="text-sm text-muted">No members yet.</p> : null}
          {active.map((m) => (
            <div key={m.id} className="flex items-center justify-between gap-2 text-sm">
              <span>{who(m)} <span className="text-muted">{m.contact}</span></span>
              <Button variant="secondary" onClick={() => void run(() => api.request(`/groups/${group.id}/members/${m.id}`, { method: "DELETE" }))}>Remove</Button>
            </div>
          ))}
        </section>
        <form method="post" className="flex items-end gap-2" onSubmit={(e) => {
          e.preventDefault();
          const channel = contact.includes("@") ? "EMAIL" : "SMS";
          void run(() => api.request(`/groups/${group.id}/members`, { method: "POST", json: { channel, contact } })).then(() => setContact(""));
        }}>
          <div className="flex-1">
            <TextField label="Add by email or mobile" hint="They need to have signed in to your Mandir Center once"
                       value={contact} maxLength={254} onChange={(e) => setContact(e.target.value)} />
          </div>
          <Button type="submit" disabled={!contact.trim()}>Add</Button>
        </form>
      </div>
    </Dialog>
  );
}
