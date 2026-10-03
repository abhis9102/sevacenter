"use client";

import { useEffect, useState } from "react";

import { Alert, Button } from "@/components/ui";
import { localEquivalentSetupUrl } from "@/lib/setupToken";

/**
 * Shows a one-time setup link exactly once. The link is a credential until it's used: it's
 * never stored, logged or re-fetchable, so the admin must copy it now and hand it over privately.
 */
export function SetupLinkPanel({ name, setupUrl }: { name: string; setupUrl: string }) {
  const [copied, setCopied] = useState<string | null>(null);
  const [localUrl, setLocalUrl] = useState<string | null>(null);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- depends on window.location (browser only)
    setLocalUrl(localEquivalentSetupUrl(setupUrl, window.location.origin));
  }, [setupUrl]);

  async function copy(value: string) {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(value);
    } catch {
      setCopied(null);
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <Alert tone="warning" title="Copy this link now: it won't be shown again">
        Send it to {name} privately (not in a group chat). It works once and expires in 72 hours. If it gets
        lost, issue a new one from the staff list.
      </Alert>
      <LinkRow label="Setup link" value={setupUrl} copied={copied === setupUrl} onCopy={() => copy(setupUrl)} />
      {localUrl ? (
        <LinkRow
          label="Local development equivalent"
          value={localUrl}
          copied={copied === localUrl}
          onCopy={() => copy(localUrl)}
        />
      ) : null}
    </div>
  );
}

function LinkRow({ label, value, copied, onCopy }: { label: string; value: string; copied: boolean; onCopy: () => void }) {
  return (
    <div className="flex flex-col gap-1">
      <span className="text-sm font-medium">{label}</span>
      <div className="flex gap-2">
        <input
          readOnly
          value={value}
          aria-label={label}
          onFocus={(e) => e.currentTarget.select()}
          className="min-w-0 flex-1 rounded-[10px] border border-line bg-surface-2 px-3 py-2 font-mono text-xs"
        />
        <Button variant="secondary" onClick={onCopy}>
          {copied ? "Copied" : "Copy"}
        </Button>
      </div>
    </div>
  );
}
