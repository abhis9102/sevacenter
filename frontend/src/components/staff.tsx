"use client";

import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";

import { Button, Dialog } from "@/components/ui";

/**
 * Shared pieces of the staff screens (docs/design/navigation.md): a title with its category pill
 * and the module's own views as a segmented switcher, stat cards, empty states, a confirm dialog.
 */

export function StaffTitle({ title, pill, description, views, actions }: {
  title: string;
  pill?: string;
  description?: string;
  views?: React.ReactNode;
  actions?: React.ReactNode;
}) {
  return (
    <div className="flex flex-col gap-4 border-b border-line pb-5 lg:flex-row lg:items-start lg:justify-between">
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">{title}</h1>
          {pill ? (
            <span className="rounded-full border border-primary/25 bg-primary/10 px-2.5 py-0.5 text-xs font-semibold text-primary-strong">
              {pill}
            </span>
          ) : null}
        </div>
        {description ? <p className="mt-1 max-w-3xl text-sm text-muted">{description}</p> : null}
      </div>
      {views || actions ? (
        <div className="flex flex-wrap items-center gap-2 lg:shrink-0">{views}{actions}</div>
      ) : null}
    </div>
  );
}

/** The current view from ?view=, if it is one of `ids`; else the first. */
export function useView<T extends string>(ids: readonly T[]): T {
  const v = useSearchParams().get("view");
  return (ids as readonly string[]).includes(v ?? "") ? (v as T) : ids[0]!;
}

/** A module's views as links (?view=…), so each view has its own address and back works. */
export function ViewSwitcher<T extends string>({ views, current, label }: {
  views: ReadonlyArray<{ id: T; label: string }>;
  current: T;
  label: string;
}) {
  const pathname = usePathname();
  return (
    <nav aria-label={label} className="inline-flex flex-wrap gap-1 rounded-[12px] border border-line bg-surface-2 p-1">
      {views.map((v, i) => (
        <Link
          key={v.id}
          href={i === 0 ? pathname : `${pathname}?view=${v.id}`}
          aria-current={current === v.id ? "page" : undefined}
          className={`rounded-[9px] px-3 py-1.5 text-sm font-semibold transition-colors ${
            current === v.id ? "bg-surface text-primary-strong shadow-xs" : "text-muted hover:text-fg"
          }`}
        >
          {v.label}
        </Link>
      ))}
    </nav>
  );
}

const STAT_TONES = {
  primary: "text-primary-strong",
  success: "text-success",
  info: "text-info",
  warning: "text-warning",
  maroon: "text-maroon",
  neutral: "text-muted",
} as const;

export function StatCard({ label, value, note, tone = "neutral", badge }: {
  label: string;
  value: React.ReactNode;
  note?: React.ReactNode;
  tone?: keyof typeof STAT_TONES;
  badge?: React.ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1 rounded-[12px] border border-line bg-surface p-4 shadow-xs">
      <div className="flex items-start justify-between gap-2">
        <p className={`text-xs font-semibold uppercase tracking-wider ${STAT_TONES[tone]}`}>{label}</p>
        {badge ? <span className="shrink-0 rounded-full bg-surface-2 px-2 py-0.5 text-[10px] font-semibold text-muted">{badge}</span> : null}
      </div>
      <p className="font-mono text-2xl font-semibold tabular-nums">{value}</p>
      {note ? <p className="text-xs text-muted">{note}</p> : null}
    </div>
  );
}

export function EmptyState({ title, children }: { title: string; children?: React.ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-2 rounded-[12px] border border-dashed border-line bg-surface px-6 py-12 text-center">
      <p className="font-medium">{title}</p>
      {children ? <div className="text-sm text-muted">{children}</div> : null}
    </div>
  );
}

export function Pill({ tone = "neutral", children }: {
  tone?: "neutral" | "primary" | "success" | "warning" | "danger" | "info";
  children: React.ReactNode;
}) {
  const t = {
    neutral: "bg-surface-2 text-muted",
    primary: "bg-primary/12 text-primary-strong",
    success: "bg-success/12 text-success",
    warning: "bg-warning/15 text-warning",
    danger: "bg-danger/12 text-danger",
    info: "bg-info/12 text-info",
  }[tone];
  return <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${t}`}>{children}</span>;
}

/** "Are you sure?" for anything that can't be undone or that people outside will notice. */
export function ConfirmDialog({ open, title, children, confirm, danger = true, busy, onConfirm, onClose }: {
  open: boolean;
  title: string;
  children: React.ReactNode;
  confirm: string;
  danger?: boolean;
  busy?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <Dialog open={open} onClose={onClose} title={title}>
      <div className="flex flex-col gap-4">
        <div className="text-sm">{children}</div>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>Keep it</Button>
          <Button type="button" variant={danger ? "danger" : "primary"} busy={busy} onClick={onConfirm}>{confirm}</Button>
        </div>
      </div>
    </Dialog>
  );
}
