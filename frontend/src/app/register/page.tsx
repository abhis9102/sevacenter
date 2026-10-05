"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { Diya, Wordmark } from "@/components/Diya";
import { useLanguage } from "@/components/LanguageProvider";
import { LanguageToggle } from "@/components/LanguageToggle";
import { Alert, Button, Card, TextField } from "@/components/ui";
import { describeError } from "@/lib/errors";
import { portalBase } from "@/lib/tenant";

const SLUG_REGEX = /^[a-z0-9]([a-z0-9-]{1,38}[a-z0-9])$/;

interface RegistrationResult {
  slug: string;
  tenantId: number;
  adminUrl: string;
  publicUrl: string;
}

export default function RegisterPage() {
  const { t } = useLanguage();
  const [trustName, setTrustName] = useState("");
  const [slug, setSlug] = useState("");
  const [adminName, setAdminName] = useState("");
  const [adminEmail, setAdminEmail] = useState("");
  const [adminPassword, setAdminPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [base, setBase] = useState("");

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- the host is only known in the browser
    setBase(portalBase(window.location));
  }, []);
  const [result, setResult] = useState<RegistrationResult | null>(null);

  function onSlugChange(value: string) {
    // Force lowercase, remove characters other than a-z, 0-9, and hyphen
    const cleaned = value.toLowerCase().replace(/[^a-z0-9-]/g, "");
    setSlug(cleaned);
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    const cleanSlug = slug.trim().toLowerCase();
    if (!SLUG_REGEX.test(cleanSlug)) {
      setError(t.auth.register.invalidSlug);
      return;
    }

    if (adminPassword.length < 12) {
      setError(t.auth.register.passwordHelp);
      return;
    }

    setBusy(true);
    try {
      const res = await api.request<RegistrationResult>("/register", {
        method: "POST",
        json: {
          slug: cleanSlug,
          trustName: trustName.trim(),
          adminName: adminName.trim(),
          adminEmail: adminEmail.trim(),
          adminPassword,
        },
        allowUnauthorized: true,
      });
      setResult(res);
    } catch (err) {
      setError(describeError(err, t.errors));
    } finally {
      setBusy(false);
    }
  }

  // Preview of the trust's address under the domain this page is served on (ADR 0030). After
  // registering, the link comes from the server's configured staff URL.
  const portalHost = `${slug || "yourtrust"}.${base}`;
  const portalTargetUrl = result ? `${result.adminUrl}/login` : "";

  return (
    <main className="flex min-h-dvh flex-col items-center justify-center px-4 py-12">
      <div className="absolute top-4 right-4">
        <LanguageToggle />
      </div>

      <div className="flex w-full max-w-lg flex-col gap-6">
        <div className="flex items-center gap-3">
          <Diya className="size-10" />
          <div>
            <Wordmark />
            <p className="text-sm text-muted">Admin for temples &amp; trusts</p>
          </div>
        </div>

        <Card className="flex flex-col gap-6 p-6 sm:p-8">
          {result ? (
            <div className="flex flex-col gap-5 text-center sm:text-left">
              <div className="size-12 rounded-full bg-primary/10 text-primary-strong flex items-center justify-center font-bold text-xl self-center sm:self-start">
                ✓
              </div>
              <div>
                <h1 className="text-2xl font-serif font-bold text-fg">{t.auth.register.successTitle}</h1>
                <p className="mt-2 text-sm text-muted">{t.auth.register.successMessage}</p>
              </div>

              <div className="rounded-lg border border-line bg-subtle/50 p-4 text-left">
                <p className="text-xs uppercase tracking-wider text-muted font-semibold">Your Temple Portal</p>
                <p className="mt-1 font-mono text-base font-semibold text-primary-strong break-all">
                  {portalTargetUrl}
                </p>
                <p className="mt-2 text-xs text-muted">
                  Log in using your administrator email: <span className="font-semibold text-fg">{adminEmail}</span>
                </p>
              </div>

              <div className="pt-2">
                <Button
                  onClick={() => {
                    window.location.href = portalTargetUrl;
                  }}
                  className="w-full justify-center"
                >
                  {t.auth.register.openPortal} →
                </Button>
              </div>
            </div>
          ) : (
            <>
              <div>
                <h1 className="text-2xl font-serif font-bold text-fg">{t.auth.register.title}</h1>
                <p className="mt-1 text-sm text-muted">{t.auth.register.subtitle}</p>
              </div>

              {error ? <Alert tone="danger">{error}</Alert> : null}

              <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
                <TextField
                  label={t.auth.register.trustName}
                  placeholder={t.auth.register.trustNamePlaceholder}
                  required
                  value={trustName}
                  onChange={(e) => setTrustName(e.target.value)}
                />

                <div className="flex flex-col gap-1.5">
                  <label className="text-sm font-medium text-fg">
                    {t.auth.register.subdomain} <span className="text-danger">*</span>
                  </label>
                  <div className="flex items-center rounded-md border border-line bg-card focus-within:border-primary-strong focus-within:ring-1 focus-within:ring-primary-strong">
                    <input
                      type="text"
                      required
                      placeholder="e.g. siddheshwar"
                      value={slug}
                      onChange={(e) => onSlugChange(e.target.value)}
                      className="w-full bg-transparent px-3 py-2 text-sm font-mono text-fg placeholder:text-muted/60 focus:outline-none"
                    />
                    <span className="pr-3 text-xs font-mono text-muted select-none">
                      {base ? `.${base}` : null}
                    </span>
                  </div>
                  <p className="text-xs text-muted font-mono">
                    {base.startsWith("localhost") ? "http" : "https"}://{portalHost}
                  </p>
                  <p className="text-xs text-muted">{t.auth.register.subdomainHelp}</p>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <TextField
                    label={t.auth.register.adminName}
                    placeholder="Priya Sharma"
                    required
                    value={adminName}
                    onChange={(e) => setAdminName(e.target.value)}
                  />
                  <TextField
                    label={t.auth.register.adminEmail}
                    type="email"
                    placeholder="admin@example.org"
                    required
                    value={adminEmail}
                    onChange={(e) => setAdminEmail(e.target.value)}
                  />
                </div>

                <div className="flex flex-col gap-1">
                  <TextField
                    label={t.auth.register.adminPassword}
                    type="password"
                    autoComplete="new-password"
                    required
                    value={adminPassword}
                    onChange={(e) => setAdminPassword(e.target.value)}
                  />
                  <p className="text-xs text-muted">{t.auth.register.passwordHelp}</p>
                </div>

                <div className="pt-2">
                  <Button
                    type="submit"
                    busy={busy}
                    disabled={!trustName || !slug || !adminName || !adminEmail || adminPassword.length < 12}
                    className="w-full justify-center"
                  >
                    {busy ? t.auth.register.submitting : t.auth.register.submit}
                  </Button>
                </div>

                <div className="text-center pt-2">
                  <Link href="/login" className="text-xs text-primary-strong hover:underline">
                    {t.auth.register.alreadyHaveAccount}
                  </Link>
                </div>
              </form>
            </>
          )}
        </Card>
      </div>
    </main>
  );
}
