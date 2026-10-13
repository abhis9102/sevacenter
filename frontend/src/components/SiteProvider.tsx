"use client";

import { createContext, useContext } from "react";

import type { Brand } from "@/lib/brand";

export interface SiteValue {
  brand: Brand | null;
  slug: string | null;
  staffBase: string;
  mandirBase: string;
  singleDomain: boolean;
}

const SiteContext = createContext<SiteValue>({
  brand: null,
  slug: null,
  staffBase: "",
  mandirBase: "",
  singleDomain: true,
});

/** The brand/domain context for this request, resolved on the server (see lib/brand.ts). */
export function SiteProvider({ value, children }: { value: SiteValue; children: React.ReactNode }) {
  return <SiteContext.Provider value={value}>{children}</SiteContext.Provider>;
}

export function useSite(): SiteValue {
  return useContext(SiteContext);
}

function hrefFor(base: string, slug: string | null, singleDomain: boolean, path: string): string {
  // Single-domain/dev: stay relative, so the link behaves exactly as before the two-domain split.
  if (singleDomain || !base) return path;
  const host = slug ? `${slug}.${base}` : base;
  const proto = typeof window !== "undefined" ? window.location.protocol : "https:";
  return `${proto}//${host}${path}`;
}

/** Absolute URL to the MandirCenter (public) side for the current trust; relative in single-domain mode. */
export function useMandirHref(path = "/"): string {
  const s = useSite();
  return hrefFor(s.mandirBase, s.slug, s.singleDomain, path);
}

/** Absolute URL to the SevaCenter (staff) side for the current trust; relative in single-domain mode. */
export function useStaffHref(path = "/"): string {
  const s = useSite();
  return hrefFor(s.staffBase, s.slug, s.singleDomain, path);
}
