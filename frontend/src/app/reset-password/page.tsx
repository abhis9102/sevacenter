"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { AuthFrame } from "@/components/AuthFrame";
import { useLanguage } from "@/components/LanguageProvider";
import { Alert, Button, TextField } from "@/components/ui";
import { describeError } from "@/lib/errors";
import { passwordProblem, takeSetupTokenFromUrl } from "@/lib/setupToken";

export default function ResetPasswordPage() {
  const router = useRouter();
  const { t } = useLanguage();
  const [token, setToken] = useState<string | null>(null);
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const extracted = takeSetupTokenFromUrl(window);
    if (extracted) {
      setToken(extracted);
    }
  }, []);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!token) {
      setError(t.auth.reset.tokenMissing);
      return;
    }

    const problem = passwordProblem(newPassword, confirmPassword);
    if (problem) {
      setError(problem);
      return;
    }

    setError(null);
    setBusy(true);

    try {
      await api.request("/auth/reset-password", {
        method: "POST",
        json: { token, newPassword },
        allowUnauthorized: true,
      });
      // Redirect to login with reset=done
      router.replace("/login?reset=done");
    } catch (err) {
      setError(describeError(err, t.errors));
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthFrame title={t.auth.reset.title}>
      {!token ? (
        <div className="flex flex-col gap-4">
          <Alert tone="warning">{t.auth.reset.tokenMissing}</Alert>
          <div className="text-center">
            <Link href="/forgot-password" className="text-xs text-primary-strong hover:underline">
              {t.auth.forgot.title} →
            </Link>
          </div>
        </div>
      ) : (
        <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
          <p className="text-sm text-muted">{t.auth.reset.subtitle}</p>

          {error ? <Alert tone="danger">{error}</Alert> : null}

          <TextField
            label={t.auth.reset.newPassword}
            type="password"
            name="newPassword"
            autoComplete="new-password"
            required
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
          />

          <TextField
            label={t.auth.reset.confirmPassword}
            type="password"
            name="confirmPassword"
            autoComplete="new-password"
            required
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
          />

          <Button
            type="submit"
            busy={busy}
            disabled={!newPassword || !confirmPassword || newPassword.length < 12}
          >
            {busy ? t.auth.reset.submitting : t.auth.reset.submit}
          </Button>

          <div className="pt-2 text-center">
            <Link href="/login" className="text-xs text-primary-strong hover:underline">
              ← {t.auth.forgot.backToLogin}
            </Link>
          </div>
        </form>
      )}
    </AuthFrame>
  );
}
