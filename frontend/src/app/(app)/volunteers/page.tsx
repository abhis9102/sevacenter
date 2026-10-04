"use client";

import { useCallback, useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { SevaIcon, SevaIconPicker } from "@/components/SevaIcons";
import { ConfirmDialog, EmptyState, Pill, StaffTitle, StatCard, useView, ViewSwitcher } from "@/components/staff";
import { Alert, Button, Card, Dialog, SelectField, TextField } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import { formatDate } from "@/lib/format";
import { hhmm, hubStats, sevaIcon, type SevaIconKey, type Signup, type Team } from "@/lib/sevak";

const STATUS = {
  NEW: { tone: "warning", label: "Pending review" },
  APPROVED: { tone: "success", label: "Approved" },
  DECLINED: { tone: "neutral", label: "Declined" },
} as const;
const VIEWS = ["teams", "directory"] as const;

/**
 * Sevak Hub (ADR 0015, 0028), LEADER+: seva teams (volunteer activities) with shifts and a target,
 * volunteers who offered seva on the Mandir Center or were registered here, and who serves where.
 */
export default function VolunteersPage() {
  const view = useView(VIEWS);
  const [signups, setSignups] = useState<Signup[] | null>(null);
  const [teams, setTeams] = useState<Team[] | null>(null);
  const [notice, setNotice] = useState<{ tone: "success" | "danger"; text: string } | null>(null);
  const [editingTeam, setEditingTeam] = useState<Team | "new" | null>(null);
  const [deletingTeam, setDeletingTeam] = useState<Team | null>(null);
  const [registering, setRegistering] = useState<number | "any" | null>(null);
  const [deploying, setDeploying] = useState<Team | null>(null);
  const [removing, setRemoving] = useState<Signup | null>(null);

  const load = useCallback(async () => {
    try {
      const [s, t] = await Promise.all([api.get<Signup[]>("/sevaks"), api.get<Team[]>("/seva-teams")]);
      setSignups(s);
      setTeams(t);
    } catch (err) {
      setNotice({ tone: "danger", text: describeError(err) });
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial data load
    void load();
  }, [load]);

  async function run(action: () => Promise<unknown>, success: string) {
    setNotice(null);
    try {
      await action();
      setNotice({ tone: "success", text: success });
      await load();
    } catch (err) {
      setNotice({ tone: "danger", text: describeError(err) });
    }
  }

  const review = (s: Signup, approve: boolean) =>
    run(() => api.request(`/sevaks/${s.id}/${approve ? "approve" : "decline"}`, { method: "POST" }),
      `${s.fullName} ${approve ? "approved" : "declined"}.`);
  const assign = (s: Signup, teamId: number | null, duty?: string) =>
    run(() => api.request(`/sevaks/${s.id}/assign`, { method: "POST", json: { teamId, duty: duty ?? null } }),
      teamId ? `${s.fullName} deployed to ${teams?.find((t) => t.id === teamId)?.name}.` : `${s.fullName} released from their team.`);

  const stats = hubStats(signups ?? [], teams ?? []);
  const teamName = (id: number | null) => teams?.find((t) => t.id === id)?.name ?? null;

  return (
    <div className="flex flex-col gap-6">
      <StaffTitle
        title="Sevak & Volunteer Hub"
        pill="Sevak Teams"
        description="Organise the temple's seva teams, schedule shifts and deploy volunteers to them."
        views={<ViewSwitcher label="Sevak Hub views" current={view} views={[
          { id: "teams", label: "Teams & Shifts Roster" }, { id: "directory", label: "Volunteer Directory" }]} />}
        actions={<Button onClick={() => setRegistering("any")}>+ Register volunteer</Button>}
      />
      {notice ? <Alert tone={notice.tone}>{notice.text}</Alert> : null}

      <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
        <StatCard label="Total sevaks" value={stats.total} />
        <StatCard label="Assigned duty" tone="success" value={stats.assigned} />
        <StatCard label="Approved, ready" tone="info" value={stats.ready} />
        <StatCard label="Pending review" tone="warning" value={stats.pending} />
        <StatCard label="Roster readiness" tone="primary" value={stats.readiness === null ? "—" : `${stats.readiness}%`}
                  note="Assigned vs team targets" />
      </div>

      {view === "teams" ? (
        <>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm text-muted">Seva teams and their shifts. Deploy approved volunteers to each team.</p>
            <Button variant="secondary" onClick={() => setEditingTeam("new")}>+ Create seva team</Button>
          </div>
          {teams === null ? null : teams.length === 0 ? (
            <EmptyState title="No seva teams yet.">Create teams such as Annadanam kitchen or Darshan queue, with their shifts.</EmptyState>
          ) : (
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              {teams.map((t) => {
                const members = (signups ?? []).filter((s) => s.teamId === t.id);
                const pct = t.targetCount ? Math.min(100, Math.round((members.length / t.targetCount) * 100)) : null;
                return (
                  <Card key={t.id} className="flex flex-col gap-3">
                    <div className="flex items-start gap-3">
                      <span className="flex size-11 shrink-0 items-center justify-center rounded-[12px] bg-gradient-to-br from-primary/20 to-gold/25 text-primary-strong">
                        <SevaIcon icon={t.icon} className="size-6" />
                      </span>
                      <div className="min-w-0 flex-1">
                        <h2 className="text-lg font-semibold leading-tight">{t.name}</h2>
                        {t.description ? <p className="text-sm text-muted">{t.description}</p> : null}
                      </div>
                      <span className="shrink-0 rounded-full bg-surface-2 px-2.5 py-0.5 font-mono text-xs font-semibold">
                        {members.length}{t.targetCount ? ` / ${t.targetCount}` : ""}
                      </span>
                    </div>
                    {pct !== null ? (
                      <div>
                        <div className="mb-1 flex justify-between text-xs text-muted"><span>Roster capacity</span><span>{pct}%</span></div>
                        <div className="h-1.5 overflow-hidden rounded-full bg-surface-2">
                          <div className={`h-full rounded-full ${pct >= 100 ? "bg-success" : "bg-primary"}`} style={{ width: `${pct}%` }} />
                        </div>
                      </div>
                    ) : null}
                    {t.shifts.length > 0 ? (
                      <div className="border-t border-line pt-3">
                        <p className="mb-1.5 text-xs font-semibold uppercase tracking-wider text-muted">Shift timings</p>
                        <div className="flex flex-wrap gap-1.5">
                          {t.shifts.map((sh) => (
                            <span key={sh.name + sh.startsAt} className="inline-flex items-center gap-1.5 rounded-[8px] border border-line bg-surface-2 px-2 py-1 text-xs">
                              <span className="size-1.5 rounded-full bg-primary" aria-hidden="true" />
                              <span className="font-semibold">{sh.name}</span>
                              <span className="font-mono text-muted">{hhmm(sh.startsAt)} – {hhmm(sh.endsAt)}</span>
                            </span>
                          ))}
                        </div>
                      </div>
                    ) : null}
                    <div className="border-t border-line pt-3">
                      <p className="mb-1.5 text-sm font-semibold">Assigned sevaks ({members.length})</p>
                      {members.length === 0 ? (
                        <p className="rounded-[10px] border border-dashed border-line bg-surface-2 px-3 py-3 text-center text-sm text-muted">No sevaks deployed yet.</p>
                      ) : (
                        <ul className="flex flex-col gap-1">
                          {members.map((m) => (
                            <li key={m.id} className="flex items-center justify-between gap-2 rounded-[8px] bg-surface-2 px-2.5 py-1.5 text-sm">
                              <span className="min-w-0 truncate"><span className="font-medium">{m.fullName}</span>
                                {m.duty ? <span className="text-muted"> · {m.duty}</span> : null}</span>
                              <button type="button" className="shrink-0 text-xs text-muted hover:text-danger"
                                      onClick={() => void assign(m, null)} aria-label={`Release ${m.fullName}`}>Release</button>
                            </li>
                          ))}
                        </ul>
                      )}
                    </div>
                    <div className="mt-auto flex flex-wrap gap-2 border-t border-line pt-3">
                      <Button variant="secondary" className="flex-1" onClick={() => setDeploying(t)}>+ Deploy sevak</Button>
                      <Button variant="ghost" onClick={() => setEditingTeam(t)}>Edit</Button>
                      <Button variant="ghost" className="text-danger" onClick={() => setDeletingTeam(t)}>Delete</Button>
                    </div>
                  </Card>
                );
              })}
            </div>
          )}
        </>
      ) : (
        signups === null ? null : signups.length === 0 ? (
          <EmptyState title="No volunteers yet.">Devotees offer seva on the Mandir Center; you can also register them here.</EmptyState>
        ) : (
          <Card className="overflow-x-auto p-0">
            <table className="w-full text-left text-sm">
              <thead className="bg-surface-2 text-xs uppercase tracking-wider text-muted">
                <tr><th className="px-4 py-2">Sevak</th><th className="px-4 py-2">Contact</th><th className="px-4 py-2">Offered</th>
                  <th className="px-4 py-2">Status</th><th className="px-4 py-2">Team</th><th className="px-4 py-2" /></tr>
              </thead>
              <tbody>
                {signups.map((s) => (
                  <tr key={s.id} className="border-t border-line align-top">
                    <td className="px-4 py-3">
                      <p className="font-medium">{s.fullName}</p>
                      <p className="text-xs text-muted">{formatDate(s.createdAt)}{s.registeredByStaff ? " · registered by staff" : " · via Mandir Center"}</p>
                    </td>
                    <td className="px-4 py-3 text-muted">{s.phone ?? s.email}</td>
                    <td className="px-4 py-3">
                      <p>{s.sevaAreas}</p>
                      {s.availability ? <p className="text-xs text-muted">{s.availability}</p> : null}
                      {s.notes ? <p className="text-xs italic text-muted">{s.notes}</p> : null}
                    </td>
                    <td className="px-4 py-3"><Pill tone={STATUS[s.status].tone}>{STATUS[s.status].label}</Pill></td>
                    <td className="px-4 py-3">
                      {s.status !== "DECLINED" && teams ? (
                        <select aria-label={`Team for ${s.fullName}`} value={s.teamId ?? ""}
                                onChange={(e) => void assign(s, e.target.value ? Number(e.target.value) : null, s.duty ?? undefined)}
                                className="rounded-[8px] border border-line bg-surface px-2 py-1 text-sm">
                          <option value="">Unassigned</option>
                          {teams.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
                        </select>
                      ) : <span className="text-muted">{teamName(s.teamId) ?? "—"}</span>}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-right">
                      {s.status === "NEW" ? (
                        <span className="inline-flex gap-1">
                          <Button onClick={() => void review(s, true)}>Approve</Button>
                          <Button variant="secondary" onClick={() => void review(s, false)}>Decline</Button>
                        </span>
                      ) : null}
                      <Button variant="ghost" className="text-danger" onClick={() => setRemoving(s)}>Remove</Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>
        )
      )}

      {editingTeam ? (
        <TeamDialog team={editingTeam === "new" ? null : editingTeam} onClose={() => setEditingTeam(null)}
                    onSaved={(n) => { setEditingTeam(null); setNotice({ tone: "success", text: `${n} saved.` }); void load(); }} />
      ) : null}
      {registering !== null && teams ? (
        <RegisterDialog teams={teams} teamId={registering === "any" ? null : registering} onClose={() => setRegistering(null)}
                        onSaved={(n) => { setRegistering(null); setNotice({ tone: "success", text: `${n} registered.` }); void load(); }} />
      ) : null}
      {deploying && signups ? (
        <DeployDialog team={deploying} candidates={signups.filter((s) => s.status !== "DECLINED" && s.teamId !== deploying.id)}
                      onClose={() => setDeploying(null)} onRegister={() => { const t = deploying; setDeploying(null); setRegistering(t.id); }}
                      onDeploy={(s, duty) => { const t = deploying; setDeploying(null); void assign(s, t.id, duty); }} />
      ) : null}
      <ConfirmDialog open={deletingTeam !== null} title="Delete this seva team?" confirm="Delete team"
                     onClose={() => setDeletingTeam(null)}
                     onConfirm={() => { const t = deletingTeam; setDeletingTeam(null);
                       if (t) void run(() => api.request(`/seva-teams/${t.id}`, { method: "DELETE" }), `${t.name} deleted.`); }}>
        {deletingTeam ? <p>{deletingTeam.name} and its shifts will be removed. Its volunteers stay in the directory, unassigned.</p> : null}
      </ConfirmDialog>
      <ConfirmDialog open={removing !== null} title="Remove this volunteer?" confirm="Remove"
                     onClose={() => setRemoving(null)}
                     onConfirm={() => { const s = removing; setRemoving(null);
                       if (s) void run(() => api.request(`/sevaks/${s.id}`, { method: "DELETE" }), `${s.fullName} removed.`); }}>
        {removing ? <p>{removing.fullName} will disappear from the Sevak Hub and from their My Mandir. The audit trail keeps a record.</p> : null}
      </ConfirmDialog>
    </div>
  );
}

function TeamDialog({ team, onClose, onSaved }: { team: Team | null; onClose: () => void; onSaved: (name: string) => void }) {
  const [f, setF] = useState({ name: team?.name ?? "", description: team?.description ?? "",
                               targetCount: team?.targetCount ? String(team.targetCount) : "" });
  const [icon, setIcon] = useState<SevaIconKey>(sevaIcon(team?.icon));
  const [shifts, setShifts] = useState(team?.shifts.map((s) => ({ name: s.name, startsAt: hhmm(s.startsAt), endsAt: hhmm(s.endsAt) }))
    ?? [{ name: "", startsAt: "", endsAt: "" }]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Readonly<Record<string, string>>>({});
  const setShift = (i: number, k: "name" | "startsAt" | "endsAt", v: string) =>
    setShifts(shifts.map((s, j) => (j === i ? { ...s, [k]: v } : s)));

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFields({});
    try {
      await api.request(team ? `/seva-teams/${team.id}` : "/seva-teams", {
        method: team ? "PUT" : "POST",
        json: { name: f.name.trim(), description: f.description.trim() || null, icon,
                targetCount: f.targetCount ? Number(f.targetCount) : null,
                shifts: shifts.filter((s) => s.name.trim() || s.startsAt || s.endsAt)
                  .map((s) => ({ name: s.name.trim(), startsAt: s.startsAt || null, endsAt: s.endsAt || null })) },
      });
      onSaved(f.name.trim());
    } catch (err) {
      if (err instanceof ApiError) setFields(err.fields);
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog open onClose={onClose} title={team ? `Edit ${team.name}` : "Create a seva team"}>
      <form onSubmit={onSubmit} className="flex flex-col gap-3">
        <div className="grid gap-3 sm:grid-cols-[1fr_8rem]">
          <TextField label="Team / activity" placeholder="Annadanam kitchen" value={f.name}
                     onChange={(e) => setF({ ...f, name: e.target.value })} error={fields.name} />
          <TextField label="Target sevaks" inputMode="numeric" value={f.targetCount}
                     onChange={(e) => setF({ ...f, targetCount: e.target.value.replace(/\D/g, "") })} error={fields.targetCount} />
        </div>
        <TextField label="What they do (optional)" value={f.description} onChange={(e) => setF({ ...f, description: e.target.value })} />
        <SevaIconPicker value={icon} onChange={setIcon} />
        {fields.icon ? <Alert tone="danger">{fields.icon}</Alert> : null}
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-1 text-sm font-medium">Shifts</legend>
          {fields.shifts ? <Alert tone="danger">{fields.shifts}</Alert> : null}
          {shifts.map((s, i) => (
            <div key={i} className="grid items-end gap-2 sm:grid-cols-[1fr_6.5rem_6.5rem_auto]">
              <TextField label="Shift" placeholder="Pratah preparation" value={s.name} onChange={(e) => setShift(i, "name", e.target.value)} />
              <TextField label="From" type="time" value={s.startsAt} onChange={(e) => setShift(i, "startsAt", e.target.value)} />
              <TextField label="To" type="time" value={s.endsAt} onChange={(e) => setShift(i, "endsAt", e.target.value)} />
              <Button type="button" variant="ghost" aria-label="Remove shift" onClick={() => setShifts(shifts.filter((_, j) => j !== i))}>✕</Button>
            </div>
          ))}
          {shifts.length < 8 ? (
            <div><Button type="button" variant="secondary" onClick={() => setShifts([...shifts, { name: "", startsAt: "", endsAt: "" }])}>+ Add shift</Button></div>
          ) : null}
        </fieldset>
        {error ? <Alert tone="danger">{error}</Alert> : null}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" type="button" onClick={onClose}>Cancel</Button>
          <Button type="submit" busy={busy}>Save team</Button>
        </div>
      </form>
    </Dialog>
  );
}

function RegisterDialog({ teams, teamId, onClose, onSaved }: {
  teams: Team[]; teamId: number | null; onClose: () => void; onSaved: (name: string) => void;
}) {
  const [f, setF] = useState({ fullName: "", phone: "", email: "", sevaAreas: teams.find((t) => t.id === teamId)?.name ?? "",
    availability: "", notes: "", teamId: teamId ? String(teamId) : "", duty: "", approved: "true" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Readonly<Record<string, string>>>({});
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value });

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFields({});
    try {
      await api.request("/sevaks", { method: "POST", json: {
        fullName: f.fullName.trim(), phone: f.phone.trim() || null, email: f.email.trim() || null,
        sevaAreas: f.sevaAreas.trim(), availability: f.availability.trim() || null, notes: f.notes.trim() || null,
        approved: f.approved === "true", teamId: f.teamId ? Number(f.teamId) : null, duty: f.duty.trim() || null } });
      onSaved(f.fullName.trim());
    } catch (err) {
      if (err instanceof ApiError) setFields(err.fields);
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog open onClose={onClose} title="Register a volunteer">
      <form onSubmit={onSubmit} className="flex flex-col gap-3">
        <div className="grid gap-3 sm:grid-cols-2">
          <TextField label="Full name" placeholder="Ramesh Sharma" value={f.fullName} onChange={set("fullName")} error={fields.fullName} />
          <TextField label="Mobile" inputMode="tel" placeholder="98765 43210" value={f.phone} onChange={set("phone")} error={fields.phone} />
        </div>
        <TextField label="Email (if no mobile)" type="email" value={f.email} onChange={set("email")} error={fields.email} />
        <TextField label="Seva they offer" placeholder="Kitchen, crowd management" value={f.sevaAreas} onChange={set("sevaAreas")}
                   error={fields.sevaAreas} />
        <div className="grid gap-3 sm:grid-cols-2">
          <TextField label="Availability (optional)" placeholder="Weekends & festivals" value={f.availability} onChange={set("availability")} />
          <SelectField label="Status" value={f.approved} onChange={set("approved")}
                       options={[{ value: "true", label: "Approved / ready" }, { value: "false", label: "Pending review" }]} />
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <SelectField label="Assign team (optional)" value={f.teamId} onChange={set("teamId")}
                       options={[{ value: "", label: "— Unassigned —" }, ...teams.map((t) => ({ value: String(t.id), label: t.name }))]} />
          <TextField label="Duty / shift (optional)" placeholder="Navratri morning kitchen" value={f.duty} onChange={set("duty")} />
        </div>
        <TextField label="Skills / notes (optional)" value={f.notes} onChange={set("notes")} />
        {error ? <Alert tone="danger">{error}</Alert> : null}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" type="button" onClick={onClose}>Cancel</Button>
          <Button type="submit" busy={busy}>Register volunteer</Button>
        </div>
      </form>
    </Dialog>
  );
}

function DeployDialog({ team, candidates, onClose, onDeploy, onRegister }: {
  team: Team; candidates: Signup[]; onClose: () => void; onDeploy: (s: Signup, duty: string) => void; onRegister: () => void;
}) {
  const [pick, setPick] = useState(String(candidates.find((c) => c.teamId === null)?.id ?? candidates[0]?.id ?? ""));
  const [duty, setDuty] = useState(team.shifts[0] ? `${team.shifts[0].name} ${hhmm(team.shifts[0].startsAt)}–${hhmm(team.shifts[0].endsAt)}` : "");
  const chosen = candidates.find((c) => String(c.id) === pick);
  return (
    <Dialog open onClose={onClose} title={`Deploy a sevak · ${team.name}`}>
      <div className="flex flex-col gap-3">
        {candidates.length === 0 ? <p className="text-sm text-muted">Everyone is already in this team.</p> : (
          <>
            <SelectField label="Sevak" value={pick} onChange={(e) => setPick(e.target.value)}
                         options={candidates.map((c) => ({ value: String(c.id), label:
                           `${c.fullName}${c.status === "NEW" ? " (pending; deploying approves)" : ""}${c.teamId ? " · in another team" : ""}` }))} />
            <SelectField label="Shift" value={duty} onChange={(e) => setDuty(e.target.value)}
                         options={[{ value: "", label: "— Any —" }, ...team.shifts.map((s) => {
                           const v = `${s.name} ${hhmm(s.startsAt)}–${hhmm(s.endsAt)}`;
                           return { value: v, label: v };
                         })]} />
          </>
        )}
        <div className="flex flex-wrap justify-between gap-2">
          <Button type="button" variant="ghost" onClick={onRegister}>+ Register someone new</Button>
          <span className="flex gap-2">
            <Button variant="secondary" type="button" onClick={onClose}>Cancel</Button>
            <Button type="button" disabled={!chosen} onClick={() => chosen && onDeploy(chosen, duty)}>Deploy</Button>
          </span>
        </div>
      </div>
    </Dialog>
  );
}
