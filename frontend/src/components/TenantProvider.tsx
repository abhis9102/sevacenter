"use client";

import { createContext, useContext } from "react";

const TenantContext = createContext<string | null>(null);

/** The tenant slug from the Host (resolved on the server for this request); null = none. */
export function TenantProvider({ tenant, children }: { tenant: string | null; children: React.ReactNode }) {
  return <TenantContext.Provider value={tenant}>{children}</TenantContext.Provider>;
}

export function useTenant(): string | null {
  return useContext(TenantContext);
}
