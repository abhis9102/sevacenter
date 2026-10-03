"use client";

import { createContext, useContext } from "react";

import type { Me } from "@/lib/types";

const SessionContext = createContext<Me | null>(null);

export const SessionProvider = SessionContext.Provider;

/** The signed-in staff member. Only usable inside the authenticated shell. */
export function useMe(): Me {
  const me = useContext(SessionContext);
  if (!me) {
    throw new Error("useMe() outside the signed-in app shell");
  }
  return me;
}
