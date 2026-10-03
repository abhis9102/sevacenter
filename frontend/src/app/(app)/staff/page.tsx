"use client";

import { useCallback, useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { useMe } from "@/components/Session";
import { SetupLinkPanel } from "@/components/SetupLinkPanel";
import { Alert, Badge, Button, Card, Dialog, PageHeader, SelectField, Spinner, TextField } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import { formatDate } from "@/lib/format";
import {
  hasRole,
  ROLE_LABELS,
  ROLES,
  type CreatedStaffUser,
  type Role,
  type StaffUser,
  type UserStatus,
} from "@/lib/types";

const ROLE_OPTIONS = ROLES.map((r) => ({ value: r, label: ROLE_LABELS[r] }));

const STATUS: Record<UserStatus, { label: string; tone: "success" | "warning" | "neutral" }> = {
  ACTIVE: { label: "Active", tone: "success" },
  PENDING: { label: "Awaiting setup", tone: "warning" },
  DISABLED: { label: "Deactivated", tone: "neutral" },
};

const ROLE_HELP =
  "Members see devotees with contact details partly hidden. Leaders can add and edit devotees and see staff. Trust admins also manage staff, erase records and import/export.";

export default function StaffPage() {
  const me = useMe();
  const isAdmin = hasRole(me.role, "TRUST_ADMIN");
  const [users, setUsers] = useState<StaffUser[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [notice, setNotice] = useState<{ tone: "success" | "danger"; text: string } | null>(null);
  const [creating, setCreating] = useState(false);
  const [link, setLink] = useState<{ name: string; url: string } | null>(null);
  const [toDeactivate, setToDeactivate] = useState<StaffUser | null>(null);

  const load = useCallback(async () => {
    try {
      setUsers(await api.get<StaffUser[]>("/users"));
      setLoadError(null);
    } catch (err) {
      setLoadError(describeError(err));
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial data load
    void load();
  }, [load]);

  async function changeRole(user: StaffUser, role: Role) {
    setNotice(null);
    try {
      await api.request<StaffUser>(`/users/${user.id}/role`, { method: "PATCH", json: { role } });
      setNotice({ tone: "success", text: `${user.displayName} is now ${ROLE_LABELS[role].toLowerCase()}.` });
    } catch (err) {
      setNotice({ tone: "danger", text: describeError(err) });
    }
    await load();
  }

  async function reissue(user: StaffUser) {
    setNotice(null);
    try {
      const res = await api.request<{ setupUrl: string }>(`/users/${user.id}/setup-link`, { method: "POST" });
      setLink({ name: user.displayName, url: res.setupUrl });
    } catch (err) {
      setNotice({ tone: "danger", text: describeError(err) });
    }
  }

  async function deactivate(user: StaffUser) {
    setNotice(null);
    try {
      await api.request<StaffUser>(`/users/${user.id}/deactivate`, { method: "POST" });
      setNotice({ tone: "success", text: `${user.displayName} can no longer sign in.` });
    } catch (err) {
      setNotice({ tone: "danger", text: describeError(err) });
    }
    setToDeactivate(null);
    await load();
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Staff"
        description="People who can sign in to this trust's SevaCenter."
        actions={isAdmin ? <Button onClick={() => setCreating(true)}>Add staff member</Button> : null}
      />

      {notice ? <Alert tone={notice.tone}>{notice.text}</Alert> : null}
      {loadError ? <Alert tone="danger">{loadError}</Alert> : null}

      {users === null && !loadError ? (
        <p className="flex items-center gap-2 text-muted">
          <Spinner /> Loading staff…
        </p>
      ) : null}

      {users ? (
        <Card className="overflow-x-auto p-0">
          <table className="w-full min-w-[40rem] text-left text-sm">
            <thead className="border-b border-line text-xs uppercase tracking-wide text-muted">
              <tr>
                <th scope="col" className="px-4 py-3 font-medium">Name</th>
                <th scope="col" className="px-4 py-3 font-medium">Role</th>
                <th scope="col" className="px-4 py-3 font-medium">Status</th>
                <th scope="col" className="px-4 py-3 font-medium">Added</th>
                {isAdmin ? <th scope="col" className="px-4 py-3 font-medium"><span className="sr-only">Actions</span></th> : null}
              </tr>
            </thead>
            <tbody>
              {users.map((u) => {
                const self = u.id === me.userId;
                return (
                  <tr key={u.id} className="border-b border-line last:border-0">
                    <td className="px-4 py-3">
                      <p className="font-medium">
                        {u.displayName}
                        {self ? <span className="ml-1 text-muted">(you)</span> : null}
                      </p>
                      <p className="text-muted">{u.email}</p>
                    </td>
                    <td className="px-4 py-3">
                      {isAdmin && u.status !== "DISABLED" ? (
                        <select
                          aria-label={`Role for ${u.displayName}`}
                          value={u.role}
                          onChange={(e) => void changeRole(u, e.target.value as Role)}
                          className="rounded-[10px] border border-line bg-surface px-2 py-1"
                        >
                          {ROLE_OPTIONS.map((o) => (
                            <option key={o.value} value={o.value}>
                              {o.label}
                            </option>
                          ))}
                        </select>
                      ) : (
                        ROLE_LABELS[u.role] ?? u.role
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <Badge tone={STATUS[u.status]?.tone ?? "neutral"}>{STATUS[u.status]?.label ?? u.status}</Badge>
                    </td>
                    <td className="px-4 py-3 text-muted">{formatDate(u.createdAt)}</td>
                    {isAdmin ? (
                      <td className="px-4 py-3">
                        <div className="flex justify-end gap-2">
                          {u.status === "PENDING" ? (
                            <Button variant="secondary" onClick={() => void reissue(u)}>
                              New setup link
                            </Button>
                          ) : null}
                          {u.status !== "DISABLED" && !self ? (
                            <Button variant="ghost" className="text-danger" onClick={() => setToDeactivate(u)}>
                              Deactivate
                            </Button>
                          ) : null}
                        </div>
                      </td>
                    ) : null}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </Card>
      ) : null}

      <p className="max-w-prose text-sm text-muted">{ROLE_HELP}</p>

      {isAdmin ? (
        <CreateStaffDialog
          open={creating}
          onClose={() => setCreating(false)}
          onCreated={(created) => {
            setCreating(false);
            setLink({ name: created.user.displayName, url: created.setupUrl });
            void load();
          }}
        />
      ) : null}

      <Dialog open={link !== null} onClose={() => setLink(null)} title="Setup link">
        {link ? <SetupLinkPanel name={link.name} setupUrl={link.url} /> : null}
        <div className="flex justify-end">
          <Button onClick={() => setLink(null)}>I&apos;ve copied it</Button>
        </div>
      </Dialog>

      <Dialog open={toDeactivate !== null} onClose={() => setToDeactivate(null)} title="Deactivate staff member?">
        <p>
          {toDeactivate?.displayName} will be signed out everywhere and won&apos;t be able to sign in again. Their past
          work stays on record.
        </p>
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setToDeactivate(null)}>
            Cancel
          </Button>
          <Button variant="danger" onClick={() => toDeactivate && void deactivate(toDeactivate)}>
            Deactivate
          </Button>
        </div>
      </Dialog>
    </div>
  );
}

function CreateStaffDialog({
  open,
  onClose,
  onCreated,
}: {
  open: boolean;
  onClose: () => void;
  onCreated: (created: CreatedStaffUser) => void;
}) {
  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [role, setRole] = useState<Role>("MEMBER");
  const [fields, setFields] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function reset() {
    setEmail("");
    setDisplayName("");
    setRole("MEMBER");
    setFields({});
    setError(null);
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFields({});
    try {
      const created = await api.request<CreatedStaffUser>("/users", {
        method: "POST",
        json: { email, displayName, role },
      });
      reset();
      onCreated(created);
    } catch (err) {
      if (err instanceof ApiError && err.code === "email_taken") {
        setFields({ email: describeError(err) });
      } else if (err instanceof ApiError && Object.keys(err.fields).length > 0) {
        setFields({ ...err.fields });
        setError(describeError(err));
      } else {
        setError(describeError(err));
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog
      open={open}
      onClose={() => {
        reset();
        onClose();
      }}
      title="Add staff member"
    >
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        {error ? <Alert tone="danger">{error}</Alert> : null}
        <TextField label="Name" value={displayName} onChange={(e) => setDisplayName(e.target.value)} maxLength={120} required error={fields.displayName} />
        <TextField label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} maxLength={254} required error={fields.email} />
        <SelectField label="Role" value={role} onChange={(e) => setRole(e.target.value as Role)} options={ROLE_OPTIONS} error={fields.role} />
        <p className="text-xs text-muted">
          You&apos;ll get a one-time setup link to hand over; they choose their own password.
        </p>
        <div className="flex justify-end gap-2">
          <Button
            variant="secondary"
            onClick={() => {
              reset();
              onClose();
            }}
          >
            Cancel
          </Button>
          <Button type="submit" busy={busy}>
            Add and get link
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
