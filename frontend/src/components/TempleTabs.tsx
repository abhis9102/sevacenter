"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { useMe } from "@/components/Session";
import { useActiveInView } from "@/components/useActiveInView";
import { hasRole } from "@/lib/types";

/** The Mandir Center section's pages: the temple, its About page, and its own domain (admins). */
export function TempleTabs() {
  const me = useMe();
  const path = usePathname();
  const row = useActiveInView<HTMLElement>(path);
  const tabs = [
    { href: "/temple", label: "Temple & darshan" },
    { href: "/temple/about", label: "About & history" },
    ...(hasRole(me.role, "TRUST_ADMIN") ? [{ href: "/temple/domain", label: "Your own domain" }] : []),
  ];
  return (
    <nav ref={row} aria-label="Mandir Center sections" className="scroll-row flex gap-1 sm:flex-wrap rounded-[12px] border border-line bg-surface-2 p-1">
      {tabs.map((t) => {
        const active = path === t.href;
        return (
          <Link key={t.href} href={t.href} aria-current={active ? "page" : undefined}
                className={`shrink-0 whitespace-nowrap rounded-[10px] px-3 py-1.5 text-sm font-medium transition-colors ${
                  active ? "bg-surface text-primary-strong shadow-xs" : "text-muted hover:text-fg"}`}>
            {t.label}
          </Link>
        );
      })}
    </nav>
  );
}
