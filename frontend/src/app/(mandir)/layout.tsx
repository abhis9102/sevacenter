import { MandirShell } from "@/components/MandirShell";

/** Every public temple page shares the MandirCenter frame (header, darshan status, tabs, footer). */
export default function MandirLayout({ children }: { children: React.ReactNode }) {
  return <MandirShell>{children}</MandirShell>;
}
