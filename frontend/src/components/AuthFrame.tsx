"use client";

import { Diya, Wordmark } from "@/components/Diya";
import { useTenant } from "@/components/TenantProvider";
import { Alert, Card } from "@/components/ui";

/** Centered card for the signed-out pages (login, account setup). */
export function AuthFrame({ title, children }: { title: string; children: React.ReactNode }) {
  const tenant = useTenant();
  return (
    <main className="flex min-h-dvh items-center justify-center px-4 py-10">
      <div className="flex w-full max-w-sm flex-col gap-6">
        <div className="flex items-center gap-3">
          <Diya className="size-10" />
          <div>
            <Wordmark />
            <p className="text-sm text-muted">Admin for temples &amp; trusts</p>
          </div>
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
          {tenant ? children : <NoTenant />}
        </Card>
      </div>
    </main>
  );
}

function NoTenant() {
  return (
    <Alert tone="warning" title="Open your trust's own address">
      Each trust signs in at its own address, such as <span className="font-mono">yourtrust.sevacenter.app</span>.
      For local development use <span className="font-mono">yourtrust.localhost:3000</span>.
    </Alert>
  );
}
