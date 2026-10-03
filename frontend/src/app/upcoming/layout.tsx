import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Upcoming events",
  description: "Upcoming festivals and darshan at this temple. Register for a pass.",
};

export default function UpcomingLayout({ children }: { children: React.ReactNode }) {
  return children;
}
