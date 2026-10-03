export function formatDate(iso: string | null | undefined): string {
  if (!iso) {
    return "";
  }
  const d = new Date(iso);
  return Number.isNaN(d.getTime())
    ? ""
    : d.toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) {
    return "";
  }
  const d = new Date(iso);
  return Number.isNaN(d.getTime())
    ? ""
    : d.toLocaleString("en-IN", { day: "numeric", month: "short", year: "numeric", hour: "numeric", minute: "2-digit" });
}

/** "Showing 26–50 of 112" bounds for a zero-based page. */
export function pageRange(page: number, size: number, total: number): { from: number; to: number; pages: number } {
  const pages = Math.max(1, Math.ceil(total / Math.max(1, size)));
  if (total === 0) {
    return { from: 0, to: 0, pages };
  }
  const from = page * size + 1;
  const to = Math.min(total, (page + 1) * size);
  return { from, to, pages };
}
