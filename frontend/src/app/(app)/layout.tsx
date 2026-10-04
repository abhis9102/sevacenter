"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { AppSwitcher } from "@/components/AppSwitcher";
import { Diya, Wordmark } from "@/components/Diya";
import { LanguageToggle } from "@/components/LanguageToggle";
import { ThemeToggle } from "@/components/ThemeToggle";
import { useLanguage } from "@/components/LanguageProvider";
import { SessionProvider } from "@/components/Session";
import { useTenant } from "@/components/TenantProvider";
import { Alert, Spinner } from "@/components/ui";
import { UserMenu } from "@/components/UserMenu";
import { ApiError, describeError } from "@/lib/errors";
import { MandirLogo } from "@/components/MandirLogo";
import { moduleForPath, visibleModules } from "@/lib/apps";
import type { Me } from "@/lib/types";

/**
 * Signed-in shell: loads the current user (401 -> login), then one header bar: the module switcher
 * (the navigation), brand and account controls (docs/design/navigation.md).
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

  const modules = visibleModules(me);
  const current = moduleForPath(modules, pathname);

  return (
    <SessionProvider value={me}>
      <div className="flex min-h-dvh flex-col">
        <header className="sticky top-0 z-40 border-b border-line bg-surface/95 backdrop-blur supports-[backdrop-filter]:bg-surface/85">
          <div className="mx-auto flex max-w-6xl items-center gap-2 px-4 py-2.5 sm:gap-3">
            <AppSwitcher modules={modules} current={current} />
            <span className="hidden h-5 w-px shrink-0 bg-line sm:block" aria-hidden="true" />
            <Link href="/dashboard" className="hidden items-center gap-2 sm:flex" aria-label="SevaCenter home">
              <Diya className="size-7" />
              <Wordmark />
            </Link>
            <span className="hidden items-center gap-1.5 rounded-full bg-surface-2 px-2.5 py-0.5 font-mono text-xs text-muted md:inline-flex">
              <span className="size-1.5 rounded-full bg-success" aria-hidden="true" />
              {me.tenant ?? tenant}
            </span>
            <div className="ml-auto flex shrink-0 items-center gap-2 sm:gap-3">
              <a
                href="/"
                target="_blank"
                rel="noopener noreferrer"
                className="hidden items-center gap-1.5 rounded-[10px] border border-line bg-surface px-2.5 py-1.5 text-xs font-medium text-muted transition-colors hover:border-primary/50 hover:text-fg sm:inline-flex"
              >
                <MandirLogo className="size-4" />
                {t.apps.mandirCenter} <span aria-hidden="true">↗</span>
                <span className="sr-only"> ({t.apps.newTab})</span>
              </a>
              <span className="hidden items-center gap-3 sm:flex">
                <ThemeToggle />
                <LanguageToggle />
              </span>
              <UserMenu me={me} onSignOut={signOut} signingOut={signingOut} />
            </div>
          </div>
        </header>
        <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">{children}</main>
      </div>
    </SessionProvider>
  );
}
