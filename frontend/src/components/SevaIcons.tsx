"use client";

import { SEVA_ICON_KEYS, SEVA_ICON_LABELS, sevaIcon, type SevaIconKey } from "@/lib/sevak";

/** Line icons for seva teams (ADR 0028): inline SVG, currentColor, 24×24, so they follow the theme. */
const PATHS: Record<SevaIconKey, React.ReactNode> = {
  hands: <><path d="M7 11V6.5a1.5 1.5 0 0 1 3 0V11" /><path d="M10 10V5a1.5 1.5 0 0 1 3 0v5" /><path d="M13 10V6.5a1.5 1.5 0 0 1 3 0V13" /><path d="M7 11a1.5 1.5 0 0 0-3 0v2a8 8 0 0 0 8 8h1a5 5 0 0 0 5-5v-5.5a1.5 1.5 0 0 0-3 0" /></>,
  kitchen: <><path d="M4 10h16v4a6 6 0 0 1-6 6h-4a6 6 0 0 1-6-6z" /><path d="M2 10h20" /><path d="M9 7c0-1.2 1-1.6 1-3M14 7c0-1.2 1-1.6 1-3" /></>,
  meal: <><circle cx="12" cy="13" r="4.5" /><path d="M3 4v5a2 2 0 0 0 2 2v9M5 4v4" /><path d="M21 4c-2 0-3 2-3 5v3h3v8" /></>,
  prasad: <><path d="M3 13h18a9 7 0 0 1-18 0z" /><circle cx="8.5" cy="10" r="2" /><circle cx="15.5" cy="10" r="2" /><circle cx="12" cy="6.5" r="2" /></>,
  cow: <><path d="M4 5c1 2.5 2.5 3.5 5 3.5h6c2.5 0 4-1 5-3.5" /><path d="M8 8.5V15a4 4 0 0 0 8 0V8.5" /><path d="M10 12h.01M14 12h.01" /><path d="M10.5 17h3" /></>,
  queue: <><circle cx="5" cy="7" r="2" /><circle cx="12" cy="7" r="2" /><circle cx="19" cy="7" r="2" /><path d="M3 20v-6a2 2 0 0 1 4 0v6M10 20v-6a2 2 0 0 1 4 0v6M17 20v-6a2 2 0 0 1 4 0v6" /></>,
  crowd: <><circle cx="9" cy="8" r="3.5" /><path d="M2.5 20c0-3.6 2.9-6 6.5-6s6.5 2.4 6.5 6" /><path d="M16 4.5a3.5 3.5 0 0 1 0 7" /><path d="M18 14.3c2 .7 3.5 2.7 3.5 5.7" /></>,
  elder: <><circle cx="9" cy="4.5" r="2" /><path d="M9 7v6l-2.5 8M9 13l2.5 8M9 9l3.5 2.5" /><path d="M15.5 11v10" /></>,
  wheelchair: <><circle cx="10" cy="4" r="1.8" /><path d="M10 7v6h6l2 5" /><path d="M14.5 19A5.5 5.5 0 1 1 7 11" /></>,
  child: <><circle cx="12" cy="6" r="3" /><path d="M8 21v-5H6l2-6h8l2 6h-2v5" /></>,
  flower: <><circle cx="12" cy="9" r="2.2" /><path d="M12 6.8a3 3 0 1 1 3 1.6M14.2 9a3 3 0 1 1-1.4 3M12 11.2a3 3 0 1 1-3-1.6M9.8 9a3 3 0 1 1 1.4-3" /><path d="M12 12v9M12 17c-2-2-4-2-5-1M12 18.5c2-2 4-2 5-1" /></>,
  garland: <><path d="M4 4c0 9 3.5 13.5 8 13.5S20 13 20 4" /><circle cx="5" cy="9" r="1.3" /><circle cx="7.6" cy="13.4" r="1.3" /><circle cx="12" cy="15.8" r="1.3" /><circle cx="16.4" cy="13.4" r="1.3" /><circle cx="19" cy="9" r="1.3" /><path d="M12 17.5V21" /></>,
  rangoli: <><path d="m12 3 3 6 6 3-6 3-3 6-3-6-6-3 6-3z" /><circle cx="12" cy="12" r="2" /></>,
  lamp: <><path d="M12 3c2 2.4 3 4.1 3 5.6a3 3 0 0 1-6 0C9 7.1 10 5.4 12 3z" /><path d="M3.5 14h17a8.5 6 0 0 1-17 0z" /></>,
  bell: <><path d="M6 16v-5a6 6 0 0 1 12 0v5l1.5 2h-15z" /><path d="M10 20a2 2 0 0 0 4 0" /><path d="M12 3v2" /></>,
  flag: <><path d="M5 21V4" /><path d="M5 4c4-2 6 2 10 0l3-1v9l-3 1c-4 2-6-2-10 0" /></>,
  temple: <><path d="M12 2.5v2.5" /><path d="M12 5 8.5 10h7z" /><path d="M6.5 10h11l1 3h-13z" /><path d="M6 13h12v7.5H6z" /><path d="M10.5 20.5v-4a1.5 1.5 0 0 1 3 0v4" /></>,
  music: <><path d="M9 18V5l11-2v13" /><circle cx="6" cy="18" r="3" /><circle cx="17" cy="16" r="3" /></>,
  mic: <><rect x="9" y="3" width="6" height="11" rx="3" /><path d="M5 11a7 7 0 0 0 14 0M12 18v3M8 21h8" /></>,
  footwear: <><path d="M3 16c0-3 2-4 4-4l3-5 3 1-1 4c3 0 9 1 9 4v1H3z" /><path d="M3 19.5h18" /></>,
  broom: <><path d="M15 3 10 12" /><path d="M6 12h8l2 9H4z" /><path d="M8 16v5M12 16v5" /></>,
  water: <path d="M12 3c3 4 6 7.5 6 11a6 6 0 0 1-12 0c0-3.5 3-7 6-11z" />,
  "first-aid": <><rect x="3" y="6" width="18" height="14" rx="2" /><path d="M9 6V4h6v2M12 10v6M9 13h6" /></>,
  shield: <><path d="M12 3 4.5 6v5.5c0 4.6 3.2 8.3 7.5 9.5 4.3-1.2 7.5-4.9 7.5-9.5V6z" /><path d="m9 12 2 2 4-4" /></>,
  parking: <><rect x="4" y="3" width="16" height="18" rx="3" /><path d="M10 17V8h3a2.5 2.5 0 0 1 0 5h-3" /></>,
  transport: <><rect x="4" y="3" width="16" height="15" rx="2" /><path d="M4 11h16M8 21v-3M16 21v-3" /><circle cx="8" cy="14.5" r="1" /><circle cx="16" cy="14.5" r="1" /></>,
  tent: <><path d="M3 20h18M5 20l7-15 7 15M9 20l3-6 3 6" /></>,
  light: <><path d="M9 18h6M10 21h4" /><path d="M12 3a6 6 0 0 0-4 10.5c.7.7 1 1.5 1 2.5h6c0-1 .3-1.8 1-2.5A6 6 0 0 0 12 3z" /></>,
  tools: <path d="M14.5 6.5a4 4 0 0 0-5.3 5.3L3.5 17.5a2 2 0 0 0 3 3l5.7-5.7a4 4 0 0 0 5.3-5.3l-2.5 2.5-2.5-.5-.5-2.5z" />,
  camera: <><path d="M4 8h3l2-3h6l2 3h3v11H4z" /><circle cx="12" cy="13" r="3.5" /></>,
  book: <><path d="M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2z" /><path d="M4 19V5M8 7h7" /></>,
  info: <><circle cx="12" cy="12" r="9" /><path d="M12 11v5M12 8h.01" /></>,
  phone: <path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z" />,
  clipboard: <><rect x="5" y="4" width="14" height="17" rx="2" /><path d="M9 4V3h6v1M9 10h6M9 14h6M9 18h3" /></>,
  rupee: <path d="M6 4h12M6 9h12M14.5 20 7 13h2.5a4.5 4.5 0 0 0 0-9" />,
  gift: <><rect x="3" y="8" width="18" height="4" /><path d="M5 12v9h14v-9M12 8v13" /><path d="M12 8c-1.5-3-5-3-5-1s3 1 5 1c2 0 5 1 5-1s-3.5-2-5 1" /></>,
  leaf: <><path d="M5 19C5 10 11 5 20 5c0 9-5 15-14 15" /><path d="m5 19 7-7" /></>,
  star: <path d="m12 3 2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z" />,
};

export function SevaIcon({ icon, className = "size-5" }: { icon: string | null | undefined; className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"
         strokeLinejoin="round" className={className} aria-hidden="true">
      {PATHS[sevaIcon(icon)]}
    </svg>
  );
}

/** A grid of every team icon to choose from; arrow keys move within the radio group. */
export function SevaIconPicker({ value, onChange }: { value: SevaIconKey; onChange: (icon: SevaIconKey) => void }) {
  return (
    <fieldset>
      <legend className="mb-1.5 text-sm font-medium">Icon <span className="font-normal text-muted">· {SEVA_ICON_LABELS[value]}</span></legend>
      <div role="radiogroup" aria-label="Team icon" className="grid max-h-48 grid-cols-6 gap-1.5 overflow-y-auto rounded-[10px] border border-line bg-surface-2 p-2 sm:grid-cols-8">
        {SEVA_ICON_KEYS.map((k) => (
          <button key={k} type="button" role="radio" aria-checked={value === k} aria-label={SEVA_ICON_LABELS[k]} title={SEVA_ICON_LABELS[k]}
                  tabIndex={value === k ? 0 : -1}
                  onClick={() => onChange(k)}
                  onKeyDown={(e) => {
                    const i = SEVA_ICON_KEYS.indexOf(k);
                    const step = e.key === "ArrowRight" || e.key === "ArrowDown" ? 1 : e.key === "ArrowLeft" || e.key === "ArrowUp" ? -1 : 0;
                    if (step) {
                      e.preventDefault();
                      const next = SEVA_ICON_KEYS[(i + step + SEVA_ICON_KEYS.length) % SEVA_ICON_KEYS.length]!;
                      onChange(next);
                      (e.currentTarget.parentElement?.querySelector(`[aria-label="${SEVA_ICON_LABELS[next]}"]`) as HTMLElement | null)?.focus();
                    }
                  }}
                  className={`flex aspect-square items-center justify-center rounded-[8px] border transition-colors ${
                    value === k ? "border-primary bg-primary/15 text-primary-strong" : "border-transparent bg-surface text-muted hover:border-line hover:text-fg"
                  }`}>
            <SevaIcon icon={k} className="size-5" />
          </button>
        ))}
      </div>
    </fieldset>
  );
}
