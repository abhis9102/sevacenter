import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Donate",
  description: "Make a donation to this temple trust.",
};

export default function DonateLayout({ children }: { children: React.ReactNode }) {
  return children;
}
