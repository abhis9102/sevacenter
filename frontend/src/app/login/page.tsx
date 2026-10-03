"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { AuthFrame } from "@/components/AuthFrame";
import { useLanguage } from "@/components/LanguageProvider";
import { useTenant } from "@/components/TenantProvider";
import { Alert, Button, TextField } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import { safeReturnTo } from "@/lib/returnTo";
import type { Me } from "@/lib/types";

export default function LoginPage() {
  return (
    <Suspense>
      <LoginForm />
    </Suspense>
  );
}

function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const tenant = useTenant();
  const next = safeReturnTo(params.get("next"));
  const { t } = useLanguage();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Already signed in on this host? Skip the form.
  useEffect(() => {
    if (!tenant) {
      return;
    }
    let cancelled = false;
    api
      .get<Me>("/me", { allowUnauthorized: true })
      .then(() => {
        if (!cancelled) {
          router.replace(next);
        }
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [tenant, next, router]);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await api.request<Me>("/auth/login", {
        method: "POST",
        form: { email, password },
        allowUnauthorized: true,
      });
      setPassword("");
      // The CSRF token rotates on login: fetch the new one before any other write.
      await api.refreshCsrf();
      router.replace(next);
    } catch (err) {
      setPassword("");
      if (err instanceof ApiError && err.status === 401) {
        // Generic on purpose: never say whether the email exists.
        setError(t.auth.invalidCredentials);
      } else if (err instanceof ApiError && err.status === 429) {
        setError(t.auth.rateLimited);
      } else {
        setError(describeError(err, t.errors));
      }
      setBusy(false);
    }
  }

  return (
    <AuthFrame title={t.auth.signIn}>
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        {params.get("setup") === "done" ? (
          <Alert tone="success">{t.auth.setupDone}</Alert>
        ) : null}
        {params.get("reset") === "done" ? (
          <Alert tone="success">{t.auth.resetDone}</Alert>
        ) : null}
        {error ? <Alert tone="danger">{error}</Alert> : null}
        <TextField
          label={t.auth.email}
          type="email"
          name="email"
          autoComplete="username"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <TextField
          label={t.auth.password}
          action={
            <Link
              href="/forgot-password"
              className="text-xs text-primary hover:underline"
            >
              {t.auth.forgotPassword}
            </Link>
          }
          type="password"
          name="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <Button type="submit" busy={busy} disabled={!email || !password}>
          {t.auth.signInButton}
        </Button>
        <p className="text-xs text-muted">
          {t.auth.newStaffHelp}
        </p>
        <div className="border-t border-line/60 pt-3 text-center text-xs text-muted">
          <Link href="/register" className="font-medium text-primary hover:underline">
            {t.auth.registerTrust}
          </Link>
        </div>
      </form>
    </AuthFrame>
  );
}
