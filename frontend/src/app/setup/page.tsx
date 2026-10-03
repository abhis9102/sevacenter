"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";

import { api } from "@/components/apiClient";
import { AuthFrame } from "@/components/AuthFrame";
import { Alert, Button, TextField } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import { MAX_PASSWORD_LENGTH, MIN_PASSWORD_LENGTH, passwordProblem, takeSetupTokenFromUrl } from "@/lib/setupToken";

/**
 * Account setup from a one-time link: `/setup#token=...`.
 * The token is read from the fragment once, kept only in memory, wiped from the address bar,
 * and sent only in the POST body. The page sends no Referer (Referrer-Policy: no-referrer).
 */
export default function SetupPage() {
  const router = useRouter();
  // undefined = not read yet (first render), null = no usable token in the link.
  const [token, setToken] = useState<string | null | undefined>(undefined);
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const read = useRef(false);

  useEffect(() => {
    // Browser only: the fragment never reaches the server render. Exactly once: the read also
    // wipes the fragment, so a second run (React Strict Mode re-runs effects) would find nothing.
    if (read.current) {
      return;
    }
    read.current = true;
    setToken(takeSetupTokenFromUrl(window));
  }, []);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    const problem = passwordProblem(password, confirm);
    setFieldError(problem);
    if (problem || !token) {
      return;
    }
    setBusy(true);
    try {
      await api.request<void>("/auth/setup", {
        method: "POST",
        json: { token, password },
        allowUnauthorized: true,
      });
      setToken(null);
      setPassword("");
      setConfirm("");
      router.replace("/login?setup=done");
    } catch (err) {
      if (err instanceof ApiError && err.fields.password) {
        setFieldError(err.fields.password);
      } else {
        setError(describeError(err));
      }
      setBusy(false);
    }
  }

  if (token === undefined) {
    return <AuthFrame title="Set up your account">{null}</AuthFrame>;
  }

  if (token === null) {
    return (
      <AuthFrame title="Set up your account">
        <Alert tone="warning" title="This link is incomplete">
          Open the full setup link you were given, or ask your trust admin for a new one.
        </Alert>
        <Link href="/login" className="text-sm text-primary-strong underline">
          Go to sign in
        </Link>
      </AuthFrame>
    );
  }

  return (
    <AuthFrame title="Set up your account">
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        <p className="text-sm text-muted">Choose a password to finish setting up your staff account.</p>
        {error ? <Alert tone="danger">{error}</Alert> : null}
        <TextField
          label="New password"
          type="password"
          autoComplete="new-password"
          minLength={MIN_PASSWORD_LENGTH}
          maxLength={MAX_PASSWORD_LENGTH}
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          hint={`At least ${MIN_PASSWORD_LENGTH} characters. A short phrase is easier to remember.`}
        />
        <TextField
          label="Confirm password"
          type="password"
          autoComplete="new-password"
          required
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          error={fieldError ?? undefined}
        />
        <Button type="submit" busy={busy}>
          Set password
        </Button>
      </form>
    </AuthFrame>
  );
}
