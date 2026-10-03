import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Book a puja",
  description: "Book a puja or sankalp at this temple.",
};

export default function BookPujaLayout({ children }: { children: React.ReactNode }) {
  return children;
}
