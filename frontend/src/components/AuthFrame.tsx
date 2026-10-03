"use client";

import Link from "next/link";
import { useState } from "react";

import { Diya, Wordmark } from "@/components/Diya";
import { LanguageToggle } from "@/components/LanguageToggle";
import { portalLoginUrl } from "@/lib/tenant";
import { useLanguage } from "@/components/LanguageProvider";
import { useTenant } from "@/components/TenantProvider";
import { Alert, Button, Card } from "@/components/ui";

/** Centered card for the signed-out pages (login, account setup, forgot password). */
export function AuthFrame({
  title,
  allowNoTenant = false,
  children,
}: {
  title: string;
  allowNoTenant?: boolean;
  children: React.ReactNode;
}) {
  const tenant = useTenant();
  return (
    <main className="flex min-h-dvh items-center justify-center px-4 py-10">
      <div className="flex w-full max-w-sm flex-col gap-6">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Diya className="size-10" />
            <div>
              <Wordmark />
              <p className="text-sm text-muted">Admin for temples &amp; trusts</p>
            </div>
          </div>
          <LanguageToggle />
        </div>
        <Card className="flex flex-col gap-5">
          <div>
            <h1 className="text-2xl">{title}</h1>
            {tenant ? (
              <p className="mt-1 text-sm text-muted">
                Trust: <span className="font-mono text-fg">{tenant}</span>
              </p>
            ) : null}
          </div>
          {tenant || allowNoTenant ? children : <NoTenant />}
        </Card>
      </div>
    </main>
  );
}

function NoTenant() {
  const { t } = useLanguage();
  const [slug, setSlug] = useState("");
  const [slugError, setSlugError] = useState(false);

  function onGo(e: React.FormEvent) {
    e.preventDefault();
    const url = portalLoginUrl(slug, window.location);
    if (!url) {
      setSlugError(true);
      return;
    }
    // Another origin (the trust's own host), so a full navigation, not the Next.js router.
    window.location.href = url;
  }

  return (
    <div className="flex flex-col gap-4">
      <Alert tone="info" title={t.auth.noTenantTitle}>
        {t.auth.noTenantDesc}
      </Alert>

      <form onSubmit={onGo} className="flex flex-col gap-2">
        <label className="text-xs font-medium text-fg" htmlFor="slug-input">
          Already registered? Enter temple subdomain:
        </label>
        <div className="flex gap-2">
          <input
            id="slug-input"
            type="text"
            placeholder="e.g. demo"
            value={slug}
            onChange={(e) => {
              setSlug(e.target.value);
              setSlugError(false);
            }}
            className="flex-1 rounded-[10px] border border-line bg-surface px-3 py-1.5 text-sm font-mono focus:border-primary focus:outline-none"
          />
          <Button type="submit" disabled={!slug.trim()} className="px-3 py-1.5 text-xs">
            Go
          </Button>
        </div>
        {slugError ? (
          <p role="alert" className="text-xs text-danger">
            Use 3–40 lowercase letters, digits or hyphens, e.g. siddheshwar.
          </p>
        ) : null}
      </form>

      <div className="border-t border-line/60 pt-4 flex flex-col gap-2">
        <p className="text-xs text-muted">
          Need a dedicated digital platform for your temple, trust, or ashram?
        </p>
        <Link
          href="/register"
          className="inline-flex items-center justify-center rounded-[10px] bg-primary px-3 py-2 text-sm font-semibold text-white shadow-xs hover:bg-primary-strong transition-colors"
        >
          {t.auth.noTenantRegisterButton}
        </Link>
      </div>

      <p className="text-xs text-muted/80">
        {t.auth.noTenantLocalNote}
      </p>
    </div>
  );
}
