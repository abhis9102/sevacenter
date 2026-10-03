import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "My seva",
  description: "Your puja bookings, event passes and seva at this temple.",
};

export default function MySevaLayout({ children }: { children: React.ReactNode }) {
  return children;
}
