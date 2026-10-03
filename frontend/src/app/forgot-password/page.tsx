"use client";

import Link from "next/link";
import { useState } from "react";

import { api } from "@/components/apiClient";
import { AuthFrame } from "@/components/AuthFrame";
import { useLanguage } from "@/components/LanguageProvider";
import { Alert, Button, TextField } from "@/components/ui";
import { describeError } from "@/lib/errors";

interface ForgotPasswordResult {
  message: string;
  devToken?: string | null;
}

export default function ForgotPasswordPage() {
  const { t } = useLanguage();
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [devToken, setDevToken] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!email.trim()) return;

    setError(null);
    setNotice(null);
    setDevToken(null);
    setBusy(true);

    try {
      const res = await api.request<ForgotPasswordResult>("/auth/forgot-password", {
        method: "POST",
        json: { email: email.trim() },
        allowUnauthorized: true,
      });
      setNotice(res.message);
      if (res.devToken) {
        setDevToken(res.devToken);
      }
    } catch (err) {
      setError(describeError(err, t.errors));
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthFrame title={t.auth.forgot.title}>
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        <p className="text-sm text-muted">{t.auth.forgot.instructions}</p>

        {notice ? <Alert tone="success">{notice}</Alert> : null}
        {error ? <Alert tone="danger">{error}</Alert> : null}

        {devToken ? (
          <div className="rounded-md border border-primary-strong/30 bg-primary/5 p-3 text-xs flex flex-col gap-2">
            <p className="font-semibold text-primary-strong">{t.auth.forgot.devTokenNotice}</p>
            <Link
              href={`/reset-password#token=${devToken}`}
              className="inline-flex items-center font-medium text-primary-strong underline hover:opacity-80"
            >
              {t.auth.forgot.proceedToReset} →
            </Link>
          </div>
        ) : null}

        {!notice ? (
          <>
            <TextField
              label={t.auth.forgot.emailLabel}
              type="email"
              name="email"
              autoComplete="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />

            <Button type="submit" busy={busy} disabled={!email.trim()}>
              {busy ? t.auth.forgot.submitting : t.auth.forgot.submit}
            </Button>
          </>
        ) : null}

        <div className="pt-2 text-center">
          <Link href="/login" className="text-xs text-primary-strong hover:underline">
            ← {t.auth.forgot.backToLogin}
          </Link>
        </div>
      </form>
    </AuthFrame>
  );
}
