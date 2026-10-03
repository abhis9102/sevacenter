"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { Diya, Wordmark } from "@/components/Diya";
import { SessionProvider } from "@/components/Session";
import { useTenant } from "@/components/TenantProvider";
import { Alert, Badge, Button, Spinner } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import { hasRole, ROLE_LABELS, type Me, type Role } from "@/lib/types";

const NAV: ReadonlyArray<{ href: string; label: string; min: Role }> = [
  { href: "/devotees", label: "Devotees", min: "MEMBER" },
  { href: "/staff", label: "Staff", min: "LEADER" },
];

/** Signed-in shell: loads the current user (401 -> login), nav, user + role, sign out. */
export default function AppLayout({ children }: { children: React.ReactNode }) {
  const tenant = useTenant();
  const pathname = usePathname();
  const router = useRouter();
  const [me, setMe] = useState<Me | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [signingOut, setSigningOut] = useState(false);

  useEffect(() => {
    if (!tenant) {
      router.replace("/login");
      return;
    }
    let cancelled = false;
    api
      .get<Me>("/me")
      .then((m) => {
        if (!cancelled) {
          setMe(m);
        }
      })
      .catch((err) => {
        // A 401 is already on its way to /login (the API client redirects).
        if (!cancelled && !(err instanceof ApiError && err.status === 401)) {
          setError(describeError(err));
        }
      });
    return () => {
      cancelled = true;
    };
  }, [tenant, router]);

  async function signOut() {
    setSigningOut(true);
    try {
      await api.request<void>("/auth/logout", { method: "POST", allowUnauthorized: true });
    } catch {
      // Whatever happened, leave the signed-in UI.
    }
    // A full page load on purpose (not router.push): it discards all in-memory state,
    // including any devotee data still held by React.
    // eslint-disable-next-line @next/next/no-location-assign-relative-destination
    window.location.assign("/login");
  }

  if (error) {
    return (
      <main className="mx-auto max-w-lg px-4 py-16">
        <Alert tone="danger" title="Couldn't load your account">
          {error}
        </Alert>
      </main>
    );
  }
  if (!me) {
    return (
      <main className="flex min-h-dvh items-center justify-center gap-2 text-muted">
        <Spinner /> Loading…
      </main>
    );
  }

  const role = me.role as Role;
  return (
    <SessionProvider value={me}>
      <div className="flex min-h-dvh flex-col">
        <header className="border-b border-line bg-surface">
          <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3">
            <Link href="/devotees" className="flex items-center gap-2" aria-label="SevaCenter home">
              <Diya className="size-8" />
              <Wordmark />
            </Link>
            <span className="hidden rounded-full bg-surface-2 px-2.5 py-0.5 font-mono text-xs sm:inline">
              {me.tenant ?? tenant}
            </span>
            <nav aria-label="Main" className="order-last flex w-full gap-1 sm:order-none sm:w-auto">
              {NAV.filter((n) => hasRole(me.role, n.min)).map((n) => {
                const active = pathname === n.href || pathname.startsWith(`${n.href}/`);
                return (
                  <Link
                    key={n.href}
                    href={n.href}
                    aria-current={active ? "page" : undefined}
                    className={`rounded-[10px] px-3 py-1.5 text-sm font-medium ${
                      active ? "bg-primary/15 text-primary-strong" : "text-fg hover:bg-surface-2"
                    }`}
                  >
                    {n.label}
                  </Link>
                );
              })}
            </nav>
            <div className="ml-auto flex items-center gap-3">
              <div className="text-right leading-tight">
                <p className="text-sm font-medium">{me.displayName}</p>
                <p className="text-xs text-muted">{me.email}</p>
              </div>
              <Badge tone="primary">{ROLE_LABELS[role] ?? me.role}</Badge>
              <Button variant="secondary" onClick={signOut} busy={signingOut}>
                Sign out
              </Button>
            </div>
          </div>
        </header>
        <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">{children}</main>
      </div>
    </SessionProvider>
  );
}
