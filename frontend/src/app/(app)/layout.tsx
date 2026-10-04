"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { AppSwitcher } from "@/components/AppSwitcher";
import { Diya } from "@/components/Diya";
import { LanguageToggle } from "@/components/LanguageToggle";
import { ThemeToggle } from "@/components/ThemeToggle";
import { useLanguage } from "@/components/LanguageProvider";
import { SessionProvider } from "@/components/Session";
import { useTenant } from "@/components/TenantProvider";
import { Alert, Spinner } from "@/components/ui";
import { UserMenu } from "@/components/UserMenu";
import { ApiError, describeError } from "@/lib/errors";
import { appForPath, isActive, visibleApps } from "@/lib/apps";
import type { Me } from "@/lib/types";

/**
 * Signed-in shell: loads the current user (401 -> login), then a two-tier header: the app switcher
 * and account controls on top, the current app's pages below (docs/design/navigation.md).
 */
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

  const apps = visibleApps(me);
  const current = appForPath(apps, pathname);

  return (
    <SessionProvider value={me}>
      <div className="flex min-h-dvh flex-col">
        <header className="sticky top-0 z-40 border-b border-line bg-surface/95 backdrop-blur supports-[backdrop-filter]:bg-surface/85">
          <div className="mx-auto flex max-w-6xl items-center gap-2 px-4 py-2 sm:gap-3">
            <Link
              href="/dashboard"
              className="flex shrink-0 items-center rounded-[10px] p-1 hover:bg-surface-2"
              aria-label="SevaCenter home"
            >
              <Diya className="size-8" />
            </Link>
            <span className="h-6 w-px shrink-0 bg-line" aria-hidden="true" />
            <AppSwitcher apps={apps} current={current} />
            <span className="ml-1 hidden truncate rounded-full bg-surface-2 px-2.5 py-0.5 font-mono text-xs text-muted md:inline">
              {me.tenant ?? tenant}
            </span>
            <div className="ml-auto flex shrink-0 items-center gap-2 sm:gap-3">
              <a
                href="/"
                target="_blank"
                rel="noopener noreferrer"
                className="hidden items-center gap-1 rounded-[10px] px-2.5 py-1.5 text-sm font-medium text-muted hover:bg-surface-2 hover:text-fg sm:inline-flex"
              >
                {t.apps.templeSite} <span aria-hidden="true">↗</span>
                <span className="sr-only"> ({t.apps.newTab})</span>
              </a>
              <span className="hidden items-center gap-3 sm:flex">
                <ThemeToggle />
                <LanguageToggle />
              </span>
              <UserMenu me={me} onSignOut={signOut} signingOut={signingOut} />
            </div>
          </div>
          {current ? (
            <div className="mx-auto max-w-6xl overflow-x-auto px-4">
              <nav aria-label={t.apps.names[current.id].name} className="-mb-px flex min-w-max gap-1">
                {current.pages.map((p) => {
                  const active = isActive(pathname, p.href);
                  return (
                    <Link
                      key={p.href}
                      href={p.href}
                      aria-current={active ? "page" : undefined}
                      className={`border-b-2 px-3 pb-2.5 pt-1.5 text-sm font-medium transition-colors ${
                        active
                          ? "border-primary text-primary-strong"
                          : "border-transparent text-muted hover:border-line hover:text-fg"
                      }`}
                    >
                      {t.nav[p.label]}
                    </Link>
                  );
                })}
              </nav>
            </div>
          ) : null}
        </header>
        <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">{children}</main>
      </div>
    </SessionProvider>
  );
}
