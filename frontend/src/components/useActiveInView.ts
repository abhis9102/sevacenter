"use client";

import { useEffect, useRef } from "react";

/**
 * For a sideways-scrolling tab row (`scroll-row`): keeps the tab marked aria-current="page" in
 * view, centred, whenever `key` (the path or view) changes. Scrolls the row only, never the page.
 */
export function useActiveInView<T extends HTMLElement>(key: unknown) {
  const ref = useRef<T>(null);
  useEffect(() => {
    const row = ref.current;
    const active = row?.querySelector<HTMLElement>('[aria-current="page"]');
    if (!row || !active || row.scrollWidth <= row.clientWidth) return;
    const r = row.getBoundingClientRect();
    const a = active.getBoundingClientRect();
    row.scrollTo({ left: Math.max(0, row.scrollLeft + a.left - r.left - (r.width - a.width) / 2) });
  }, [key]);
  return ref;
}
