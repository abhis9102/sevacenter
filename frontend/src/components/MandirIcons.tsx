/** Line icons for the public temple site: inline SVG, currentColor, so they follow the theme. */

const PATHS = {
  home: <><path d="M3 11 12 4l9 7" /><path d="M5.5 9.5V20h13V9.5" /><path d="M10 20v-5h4v5" /></>,
  puja: <><path d="M12 3c2 2.4 3 4.1 3 5.6a3 3 0 0 1-6 0C9 7.1 10 5.4 12 3z" /><path d="M3.5 14h17a8.5 6 0 0 1-17 0z" /></>,
  donate: <><path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z" /></>,
  utsav: <><path d="M5 21V4" /><path d="M5 4c4-2 6 2 10 0l3-1v9l-3 1c-4 2-6-2-10 0" /></>,
  sevak: <><path d="M7 11V6.5a1.5 1.5 0 0 1 3 0V11" /><path d="M10 10V5a1.5 1.5 0 0 1 3 0v5" /><path d="M13 10V6.5a1.5 1.5 0 0 1 3 0V13" /><path d="M7 11a1.5 1.5 0 0 0-3 0v2a8 8 0 0 0 8 8h1a5 5 0 0 0 5-5v-5.5a1.5 1.5 0 0 0-3 0" /></>,
  user: <><circle cx="12" cy="8" r="4" /><path d="M4 21c0-4 3.6-7 8-7s8 3 8 7" /></>,
  phone: <path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z" />,
  pin: <><path d="M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21z" /><circle cx="12" cy="9.5" r="2.5" /></>,
  bell: <><path d="M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z" /><path d="M10 20a2 2 0 0 0 4 0" /></>,
  moon: <path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z" />,
  clock: <><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></>,
} as const;

export type MandirIconName = keyof typeof PATHS;

export function MandirIcon({ name, className = "size-5" }: { name: MandirIconName; className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"
         strokeLinejoin="round" className={className} aria-hidden="true">
      {PATHS[name]}
    </svg>
  );
}
