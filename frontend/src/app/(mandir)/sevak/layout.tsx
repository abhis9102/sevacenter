import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Offer seva",
  description: "Volunteer at this temple.",
};

export default function SevakLayout({ children }: { children: React.ReactNode }) {
  return children;
}
