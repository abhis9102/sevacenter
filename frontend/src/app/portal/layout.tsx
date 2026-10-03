import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Mandir Seva & Devotee Portal",
  description: "Offer online seva, make sacred donations, and download official receipts.",
};

export default function PortalLayout({ children }: { children: React.ReactNode }) {
  return <div className="min-h-dvh bg-surface text-fg selection:bg-primary/20">{children}</div>;
}
