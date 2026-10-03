"use client";
/* eslint-disable @next/next/no-img-element -- the avatar is a private, session-authenticated API image; next/image would proxy and cache it */

import { useEffect, useRef, useState } from "react";

import { api } from "@/components/apiClient";
import { useLanguage } from "@/components/LanguageProvider";
import { Alert, Badge, Button, Card, PageHeader, Spinner, TextField } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import { formatDate } from "@/lib/format";
import type { UserProfile } from "@/lib/types";

export default function ProfilePage() {
  const { t } = useLanguage();
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<"account" | "security" | "notifications">("account");

  // Avatar / Profile photo
  const [avatarUploading, setAvatarUploading] = useState(false);
  const [avatarRemoving, setAvatarRemoving] = useState(false);
  const [avatarNotice, setAvatarNotice] = useState<string | null>(null);
  const [avatarError, setAvatarError] = useState<string | null>(null);
  const [avatarTimestamp, setAvatarTimestamp] = useState<number>(() => Date.now());
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Account name editing
  const [displayName, setDisplayName] = useState("");
  const [nameSaving, setNameSaving] = useState(false);
  const [nameNotice, setNameNotice] = useState<string | null>(null);
  const [nameError, setNameError] = useState<string | null>(null);

  // Password changing
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordSaving, setPasswordSaving] = useState(false);
  const [passwordNotice, setPasswordNotice] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [passwordFieldErrors, setPasswordFieldErrors] = useState<Record<string, string>>({});

  // Notification preferences
  const [notifyDevotees, setNotifyDevotees] = useState(true);
  const [notifyDonations, setNotifyDonations] = useState(true);
  const [notifySecurity, setNotifySecurity] = useState(true);
  const [notifySaving, setNotifySaving] = useState(false);
  const [notifyNotice, setNotifyNotice] = useState<string | null>(null);
  const [notifyError, setNotifyError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    api
      .get<UserProfile>("/profile")
      .then((p) => {
        if (!cancelled) {
          setProfile(p);
          setDisplayName(p.displayName);
          setNotifyDevotees(p.notifyDevotees);
          setNotifyDonations(p.notifyDonations);
          setNotifySecurity(p.notifySecurity);
          setLoading(false);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(describeError(err, t.errors));
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [t.errors]);

  async function onSaveName(e: React.FormEvent) {
    e.preventDefault();
    if (!displayName.trim()) return;
    setNameSaving(true);
    setNameNotice(null);
    setNameError(null);
    try {
      const updated = await api.request<UserProfile>("/profile", {
        method: "PATCH",
        json: { displayName: displayName.trim() },
      });
      setProfile(updated);
      setNameNotice(t.profile.account.savedSuccess);
      window.dispatchEvent(new CustomEvent("user-updated"));
    } catch (err) {
      setNameError(describeError(err, t.errors));
    } finally {
      setNameSaving(false);
    }
  }

  async function onFileSelected(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;

    if (file.size > 2 * 1024 * 1024) {
      setAvatarError(t.profile.avatar.tooLarge);
      e.target.value = "";
      return;
    }
    if (!["image/png", "image/jpeg", "image/webp"].includes(file.type)) {
      setAvatarError(t.profile.avatar.invalidFormat);
      e.target.value = "";
      return;
    }

    setAvatarUploading(true);
    setAvatarNotice(null);
    setAvatarError(null);

    const formData = new FormData();
    formData.append("file", file);

    try {
      const updated = await api.request<UserProfile>("/profile/avatar", {
        method: "POST",
        multipart: formData,
      });
      setProfile(updated);
      setAvatarNotice(t.profile.avatar.photoUploaded);
      setAvatarTimestamp(Date.now());
      window.dispatchEvent(new CustomEvent("user-updated"));
    } catch (err) {
      setAvatarError(describeError(err, t.errors));
    } finally {
      setAvatarUploading(false);
      e.target.value = "";
    }
  }

  async function onRemoveAvatar() {
    setAvatarRemoving(true);
    setAvatarNotice(null);
    setAvatarError(null);
    try {
      await api.request<void>("/profile/avatar", { method: "DELETE" });
      setProfile((prev) => (prev ? { ...prev, hasAvatar: false } : null));
      setAvatarNotice(t.profile.avatar.photoRemoved);
      setAvatarTimestamp(Date.now());
      window.dispatchEvent(new CustomEvent("user-updated"));
    } catch (err) {
      setAvatarError(describeError(err, t.errors));
    } finally {
      setAvatarRemoving(false);
    }
  }

  async function onChangePassword(e: React.FormEvent) {
    e.preventDefault();
    setPasswordNotice(null);
    setPasswordError(null);
    setPasswordFieldErrors({});

    if (!currentPassword) {
      setPasswordFieldErrors({ currentPassword: t.common.required });
      return;
    }
    if (newPassword.length < 12) {
      setPasswordFieldErrors({ newPassword: t.profile.security.passwordTooShort });
      return;
    }
    if (newPassword !== confirmPassword) {
      setPasswordFieldErrors({ confirmPassword: t.profile.security.passwordMismatch });
      return;
    }
    if (newPassword === currentPassword) {
      setPasswordFieldErrors({ newPassword: t.profile.security.passwordSame });
      return;
    }

    setPasswordSaving(true);
    try {
      await api.request<void>("/profile/change-password", {
        method: "POST",
        json: { currentPassword, newPassword },
      });
      setPasswordNotice(t.profile.security.savedSuccess);
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fields).length > 0) {
        setPasswordFieldErrors(err.fields);
      }
      setPasswordError(describeError(err, t.errors));
    } finally {
      setPasswordSaving(false);
    }
  }

  async function onSaveNotifications(e: React.FormEvent) {
    e.preventDefault();
    setNotifySaving(true);
    setNotifyNotice(null);
    setNotifyError(null);
    try {
      const updated = await api.request<UserProfile>("/profile", {
        method: "PATCH",
        json: {
          notifyDevotees,
          notifyDonations,
          notifySecurity,
        },
      });
      setProfile(updated);
      setNotifyNotice(t.profile.notifications.savedSuccess);
    } catch (err) {
      setNotifyError(describeError(err, t.errors));
    } finally {
      setNotifySaving(false);
    }
  }

  if (loading) {
    return (
      <p className="flex items-center gap-2 text-muted">
        <Spinner /> {t.common.loading}
      </p>
    );
  }

  if (error || !profile) {
    return <Alert tone="danger">{error ?? t.common.error}</Alert>;
  }

  const tabs: Array<{ id: "account" | "security" | "notifications"; label: string }> = [
    { id: "account", label: t.profile.tabs.account },
    { id: "security", label: t.profile.tabs.security },
    { id: "notifications", label: t.profile.tabs.notifications },
  ];

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={t.profile.title} description={t.profile.description} />

      {/* Tabs */}
      <nav aria-label="Profile navigation" className="flex border-b border-line gap-2 overflow-x-auto pb-px">
        {tabs.map((tab) => {
          const active = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              type="button"
              onClick={() => setActiveTab(tab.id)}
              className={`rounded-t-[8px] border-b-2 px-4 py-2.5 text-sm font-medium transition-colors ${
                active
                  ? "border-primary text-primary-strong bg-surface"
                  : "border-transparent text-muted hover:text-fg hover:border-line"
              }`}
            >
              {tab.label}
            </button>
          );
        })}
      </nav>

      {/* Tab: Account Details */}
      {activeTab === "account" ? (
        <div className="grid gap-6 lg:grid-cols-3">
          <Card className="flex flex-col gap-4 lg:col-span-2">
            <h2 className="text-lg font-medium">{t.profile.account.heading}</h2>
            {nameNotice ? <Alert tone="success">{nameNotice}</Alert> : null}
            {nameError ? <Alert tone="danger">{nameError}</Alert> : null}

            <form onSubmit={onSaveName} className="flex flex-col gap-4">
              <div className="grid gap-4 sm:grid-cols-2">
                <div>
                  <dt className="text-xs uppercase tracking-wide text-muted">{t.profile.account.email}</dt>
                  <dd className="mt-1 font-mono text-sm">{profile.email}</dd>
                </div>
                <div>
                  <dt className="text-xs uppercase tracking-wide text-muted">{t.profile.account.role}</dt>
                  <dd className="mt-1">
                    <Badge tone="primary">{t.roles[profile.role] ?? profile.role}</Badge>
                  </dd>
                </div>
                <div>
                  <dt className="text-xs uppercase tracking-wide text-muted">{t.profile.account.tenant}</dt>
                  <dd className="mt-1 font-mono text-sm">{profile.tenant ?? "—"}</dd>
                </div>
                <div>
                  <dt className="text-xs uppercase tracking-wide text-muted">{t.profile.account.memberSince}</dt>
                  <dd className="mt-1 text-sm">{formatDate(profile.createdAt) ?? "—"}</dd>
                </div>
              </div>

              <div className="mt-2 pt-4 border-t border-line">
                <TextField
                  label={t.profile.account.displayName}
                  value={displayName}
                  placeholder={t.profile.account.displayNamePlaceholder}
                  maxLength={120}
                  required
                  onChange={(e) => setDisplayName(e.target.value)}
                />
              </div>

              <div className="flex justify-end">
                <Button type="submit" busy={nameSaving} disabled={!displayName.trim() || displayName === profile.displayName}>
                  {t.profile.account.saveDisplayName}
                </Button>
              </div>
            </form>
          </Card>

          <Card className="flex flex-col gap-4">
            <h3 className="text-sm font-semibold text-fg uppercase tracking-wider">{t.profile.avatar.heading}</h3>

            {avatarNotice ? <Alert tone="success">{avatarNotice}</Alert> : null}
            {avatarError ? <Alert tone="danger">{avatarError}</Alert> : null}

            <div className="flex flex-col sm:flex-row items-center gap-4">
              {profile.hasAvatar ? (
                <img
                  src={`/api/v1/profile/avatar?t=${avatarTimestamp}`}
                  alt={profile.displayName}
                  className="size-20 rounded-full object-cover border-2 border-line shadow-sm shrink-0"
                />
              ) : (
                <div className="flex size-20 items-center justify-center rounded-full bg-primary/10 text-2xl font-bold text-primary-strong border-2 border-line/50 shrink-0">
                  {profile.displayName.charAt(0).toUpperCase()}
                </div>
              )}

              <div className="flex flex-col gap-2 w-full">
                <input
                  type="file"
                  ref={fileInputRef}
                  onChange={onFileSelected}
                  accept="image/png,image/jpeg,image/webp"
                  className="hidden"
                />
                <div className="flex flex-wrap gap-2">
                  <Button
                    type="button"
                    variant="secondary"
                    busy={avatarUploading}
                    onClick={() => fileInputRef.current?.click()}
                  >
                    {profile.hasAvatar ? t.profile.avatar.changePhoto : t.profile.avatar.uploadPhoto}
                  </Button>
                  {profile.hasAvatar ? (
                    <Button
                      type="button"
                      variant="ghost"
                      busy={avatarRemoving}
                      onClick={onRemoveAvatar}
                      className="text-danger hover:text-danger-strong hover:bg-danger/10"
                    >
                      {t.profile.avatar.removePhoto}
                    </Button>
                  ) : null}
                </div>
                <p className="text-[11px] text-muted">{t.profile.avatar.hint}</p>
              </div>
            </div>

            <div className="border-t border-line pt-3 mt-1">
              <p className="font-semibold text-base">{profile.displayName}</p>
              <p className="text-xs text-muted font-mono">{profile.email}</p>
              <div className="mt-3 text-xs text-muted flex flex-col gap-1.5">
                <p>
                  <span className="font-medium text-fg">{t.profile.account.status}:</span> {profile.status}
                </p>
                <p>
                  <span className="font-medium text-fg">{t.profile.account.tenant}:</span> {profile.tenant}
                </p>
              </div>
            </div>
          </Card>
        </div>
      ) : null}

      {/* Tab: Security & Password */}
      {activeTab === "security" ? (
        <Card className="max-w-2xl flex flex-col gap-4">
          <div>
            <h2 className="text-lg font-medium">{t.profile.security.heading}</h2>
            <p className="text-sm text-muted mt-1">{t.profile.security.description}</p>
          </div>

          {passwordNotice ? <Alert tone="success">{passwordNotice}</Alert> : null}
          {passwordError ? <Alert tone="danger">{passwordError}</Alert> : null}

          <form onSubmit={onChangePassword} className="flex flex-col gap-4" noValidate>
            <TextField
              label={t.profile.security.currentPassword}
              type="password"
              required
              autoComplete="current-password"
              value={currentPassword}
              error={passwordFieldErrors.currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
            />
            <TextField
              label={t.profile.security.newPassword}
              type="password"
              required
              minLength={12}
              maxLength={200}
              autoComplete="new-password"
              value={newPassword}
              hint={t.profile.security.newPasswordHint}
              error={passwordFieldErrors.newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
            />
            <TextField
              label={t.profile.security.confirmPassword}
              type="password"
              required
              minLength={12}
              maxLength={200}
              autoComplete="new-password"
              value={confirmPassword}
              error={passwordFieldErrors.confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
            />

            <div className="flex justify-end pt-2">
              <Button type="submit" busy={passwordSaving}>
                {passwordSaving ? t.profile.security.savingPassword : t.profile.security.savePassword}
              </Button>
            </div>
          </form>
        </Card>
      ) : null}

      {/* Tab: Notification Preferences */}
      {activeTab === "notifications" ? (
        <Card className="max-w-3xl flex flex-col gap-5">
          <div>
            <h2 className="text-lg font-medium">{t.profile.notifications.heading}</h2>
            <p className="text-sm text-muted mt-1">{t.profile.notifications.description}</p>
          </div>

          {notifyNotice ? <Alert tone="success">{notifyNotice}</Alert> : null}
          {notifyError ? <Alert tone="danger">{notifyError}</Alert> : null}

          <form onSubmit={onSaveNotifications} className="flex flex-col gap-4">
            <label className="flex items-start gap-3 rounded-[10px] border border-line p-3 hover:bg-surface-2 cursor-pointer">
              <input
                type="checkbox"
                checked={notifyDevotees}
                onChange={(e) => setNotifyDevotees(e.target.checked)}
                className="mt-1 size-4 rounded border-line accent-primary"
              />
              <div className="text-sm">
                <span className="font-medium text-fg">{t.profile.notifications.devoteesTitle}</span>
                <p className="text-xs text-muted mt-0.5">{t.profile.notifications.devoteesDesc}</p>
              </div>
            </label>

            <label className="flex items-start gap-3 rounded-[10px] border border-line p-3 hover:bg-surface-2 cursor-pointer">
              <input
                type="checkbox"
                checked={notifyDonations}
                onChange={(e) => setNotifyDonations(e.target.checked)}
                className="mt-1 size-4 rounded border-line accent-primary"
              />
              <div className="text-sm">
                <span className="font-medium text-fg">{t.profile.notifications.donationsTitle}</span>
                <p className="text-xs text-muted mt-0.5">{t.profile.notifications.donationsDesc}</p>
              </div>
            </label>

            <label className="flex items-start gap-3 rounded-[10px] border border-line p-3 hover:bg-surface-2 cursor-pointer">
              <input
                type="checkbox"
                checked={notifySecurity}
                onChange={(e) => setNotifySecurity(e.target.checked)}
                className="mt-1 size-4 rounded border-line accent-primary"
              />
              <div className="text-sm">
                <span className="font-medium text-fg">{t.profile.notifications.securityTitle}</span>
                <p className="text-xs text-muted mt-0.5">{t.profile.notifications.securityDesc}</p>
              </div>
            </label>

            <div className="flex justify-end pt-2">
              <Button type="submit" busy={notifySaving}>
                {t.profile.notifications.saveNotifications}
              </Button>
            </div>
          </form>
        </Card>
      ) : null}
    </div>
  );
}
