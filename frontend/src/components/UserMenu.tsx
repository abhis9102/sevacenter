"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";

import { useLanguage } from "@/components/LanguageProvider";
import { Badge } from "@/components/ui";
import type { Me, Role } from "@/lib/types";

export function UserMenu({
  me,
  onSignOut,
  signingOut,
}: {
  me: Me;
  onSignOut: () => void;
  signingOut: boolean;
}) {
  const { t } = useLanguage();
  const [open, setOpen] = useState(false);
  const [avatarTimestamp, setAvatarTimestamp] = useState<number>(() => Date.now());
  const [imageError, setImageError] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const role = me.role as Role;

  useEffect(() => {
    function handleUserUpdated() {
      setAvatarTimestamp(Date.now());
      setImageError(false);
    }
    window.addEventListener("user-updated", handleUserUpdated);
    return () => window.removeEventListener("user-updated", handleUserUpdated);
  }, []);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (ref.current && !ref.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    function handleEscape(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setOpen(false);
      }
    }
    if (open) {
      document.addEventListener("mousedown", handleClickOutside);
      document.addEventListener("keydown", handleEscape);
    }
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleEscape);
    };
  }, [open]);

  const initial = me.displayName ? me.displayName.charAt(0).toUpperCase() : "U";

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen(!open)}
        aria-expanded={open}
        aria-haspopup="menu"
        aria-label="User menu"
        className="flex items-center gap-2 rounded-[10px] border border-line bg-surface px-3 py-1.5 text-sm font-medium hover:bg-surface-2 transition-colors focus:outline-none focus:ring-2 focus:ring-primary/20"
      >
        {me.hasAvatar && !imageError ? (
          <img
            src={`/api/v1/profile/avatar?t=${avatarTimestamp}`}
            alt=""
            onError={() => setImageError(true)}
            className="size-7 rounded-full object-cover border border-line"
          />
        ) : (
          <div className="flex size-7 items-center justify-center rounded-full bg-primary/15 text-xs font-bold text-primary-strong">
            {initial}
          </div>
        )}
        <span className="hidden max-w-[120px] truncate sm:inline">{me.displayName}</span>
        <svg
          className={`size-4 text-muted transition-transform ${open ? "rotate-180" : ""}`}
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth="2"
        >
          <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      {open ? (
        <div
          role="menu"
          className="absolute right-0 mt-2 w-64 origin-top-right rounded-[12px] border border-line bg-surface p-2 shadow-lg z-50 animate-in fade-in zoom-in-95 duration-100"
        >
          <div className="px-3 py-2 flex items-start gap-3">
            {me.hasAvatar && !imageError ? (
              <img
                src={`/api/v1/profile/avatar?t=${avatarTimestamp}`}
                alt=""
                onError={() => setImageError(true)}
                className="size-10 rounded-full object-cover border border-line shrink-0"
              />
            ) : (
              <div className="flex size-10 items-center justify-center rounded-full bg-primary/15 text-sm font-bold text-primary-strong shrink-0">
                {initial}
              </div>
            )}
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold text-fg truncate">{me.displayName}</p>
              <p className="text-xs text-muted truncate mt-0.5">{me.email}</p>
              <div className="mt-2 flex items-center gap-1.5 flex-wrap">
                <Badge tone="primary">{t.roles[role] ?? me.role}</Badge>
                {me.tenant ? (
                  <span className="rounded-full bg-surface-2 px-2 py-0.5 font-mono text-[10px] text-muted">
                    {me.tenant}
                  </span>
                ) : null}
              </div>
            </div>
          </div>

          <div className="my-1.5 border-t border-line" />

          <Link
            href="/profile"
            role="menuitem"
            onClick={() => setOpen(false)}
            className="flex w-full items-center gap-2.5 rounded-[8px] px-3 py-2 text-sm text-fg hover:bg-surface-2 transition-colors"
          >
            <svg className="size-4 text-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
              <path strokeLinecap="round" strokeLinejoin="round" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
            </svg>
            <span>{t.nav.profile}</span>
          </Link>

          <div className="my-1.5 border-t border-line" />

          <button
            type="button"
            role="menuitem"
            disabled={signingOut}
            onClick={() => {
              setOpen(false);
              onSignOut();
            }}
            className="flex w-full items-center gap-2.5 rounded-[8px] px-3 py-2 text-sm text-danger hover:bg-danger/10 transition-colors disabled:opacity-50"
          >
            <svg className="size-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
              <path strokeLinecap="round" strokeLinejoin="round" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
            </svg>
            <span>{signingOut ? t.common.loading : t.common.signOut}</span>
          </button>
        </div>
      ) : null}
    </div>
  );
}
