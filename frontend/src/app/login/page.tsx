"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { AuthFrame } from "@/components/AuthFrame";
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
        setError("Email or password is incorrect.");
      } else if (err instanceof ApiError && err.status === 429) {
        setError("Too many sign-in attempts. Wait a few minutes, then try again.");
      } else {
        setError(describeError(err));
      }
      setBusy(false);
    }
  }

  return (
    <AuthFrame title="Sign in">
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        {params.get("setup") === "done" ? (
          <Alert tone="success">Your password is set. Sign in to continue.</Alert>
        ) : null}
        {error ? <Alert tone="danger">{error}</Alert> : null}
        <TextField
          label="Email"
          type="email"
          name="email"
          autoComplete="username"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <TextField
          label="Password"
          type="password"
          name="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <Button type="submit" busy={busy} disabled={!email || !password}>
          Sign in
        </Button>
        <p className="text-xs text-muted">
          New staff members get a one-time setup link from their trust admin.
        </p>
      </form>
    </AuthFrame>
  );
}
