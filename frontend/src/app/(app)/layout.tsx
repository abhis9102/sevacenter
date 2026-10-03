"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { Diya, Wordmark } from "@/components/Diya";
import { LanguageToggle } from "@/components/LanguageToggle";
import { useLanguage } from "@/components/LanguageProvider";
import { SessionProvider } from "@/components/Session";
import { useTenant } from "@/components/TenantProvider";
import { Alert, Spinner } from "@/components/ui";
import { UserMenu } from "@/components/UserMenu";
import { ApiError, describeError } from "@/lib/errors";
import { hasRole, type Me, type Role } from "@/lib/types";

/** Signed-in shell: loads the current user (401 -> login), nav, user + role, sign out. */
export default function AppLayout({ children }: { children: React.ReactNode }) {
  const tenant = useTenant();
  const pathname = usePathname();
  const router = useRouter();
  const [me, setMe] = useState<Me | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [signingOut, setSigningOut] = useState(false);
  const { t } = useLanguage();

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
          setError(describeError(err, t.errors));
        }
      });
    return () => {
      cancelled = true;
    };
  }, [tenant, router, t.errors]);

  useEffect(() => {
    function handleUserUpdated() {
      api.get<Me>("/me").then(setMe).catch(() => {});
    }
    window.addEventListener("user-updated", handleUserUpdated);
    return () => window.removeEventListener("user-updated", handleUserUpdated);
  }, []);

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
        <Spinner /> {t.common.loading}
      </main>
    );
  }

  const navItems = [
    { href: "/devotees", label: t.nav.devotees, min: "MEMBER" as Role },
    { href: "/donations", label: t.nav.donations, min: "LEADER" as Role },
    { href: "/staff", label: t.nav.staff, min: "LEADER" as Role },
    { href: "/events", label: t.nav.events, min: "MEMBER" as Role },
    { href: "/payments", label: t.nav.payments, min: "TRUST_ADMIN" as Role },
  ];

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
              {navItems.filter((n) => hasRole(me.role, n.min)).map((n) => {
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
              <LanguageToggle />
              <UserMenu me={me} onSignOut={signOut} signingOut={signingOut} />
            </div>
          </div>
        </header>
        <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">{children}</main>
      </div>
    </SessionProvider>
  );
}
