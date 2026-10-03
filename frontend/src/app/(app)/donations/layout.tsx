import type { Metadata } from "next";

export const metadata: Metadata = { title: "Donations · SevaCenter" };

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
