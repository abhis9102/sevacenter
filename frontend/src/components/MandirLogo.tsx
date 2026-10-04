"use client";

import { useId } from "react";

/**
 * MandirCenter mark: a temple shikhara with dhwaja, kalash, mandapam and a lit sanctum (public
 * temple sites; the staff app keeps the diya). Gradient ids are per instance (useId), so two marks
 * on one page never share or clash.
 */
export function MandirLogo({
  className = "size-8",
  tone = "primary",
}: {
  className?: string;
  tone?: "primary" | "current" | "gold";
}) {
  const uid = useId().replace(/:/g, "");
  return (
    <svg
      className={`${className} shrink-0`}
      viewBox="0 0 64 64"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-label="MandirCenter"
      role="img"
    >
      <defs>
        <linearGradient id={`${uid}-mandirSaffron`} x1="16" y1="2" x2="48" y2="60" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#FA8C16" />
          <stop offset="45%" stopColor="#E85D04" />
          <stop offset="100%" stopColor="#C0392B" />
        </linearGradient>
        <linearGradient id={`${uid}-mandirGold`} x1="24" y1="2" x2="40" y2="40" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#FFD666" />
          <stop offset="50%" stopColor="#FAAD14" />
          <stop offset="100%" stopColor="#D48806" />
        </linearGradient>
        <radialGradient id={`${uid}-jyotiGlow`} cx="32" cy="46" r="6" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#FFE58F" stopOpacity="1" />
          <stop offset="50%" stopColor="#FFA940" stopOpacity="0.8" />
          <stop offset="100%" stopColor="#FA541C" stopOpacity="0" />
        </radialGradient>
      </defs>

      {/* Fluttering Sacred Saffron Dhwaja (Flag) atop the temple pinnacle */}
      <path
        d="M32 2 L32 8"
        stroke="#E85D04"
        strokeWidth="1.8"
        strokeLinecap="round"
      />
      <path
        d="M32 2 L42 5 L32 8 Z"
        fill="#FA541C"
        stroke="#D4380D"
        strokeWidth="0.8"
        strokeLinejoin="round"
      />

      {/* Kalash & Stupi (Sacred Pot finial) */}
      <path
        d="M30 7.5 C30 6 34 6 34 7.5 C34 9 30 9 30 7.5 Z"
        fill={tone === "current" ? "currentColor" : `url(#${uid}-mandirGold)`}
      />
      {/* Amalaka (Grooved disc crowning the Shikhara) */}
      <path
        d="M27 9 C27 8 37 8 37 9 L38.5 11.5 C38.5 12.5 25.5 12.5 25.5 11.5 Z"
        fill={tone === "current" ? "currentColor" : `url(#${uid}-mandirGold)`}
      />

      {/* Stepped Shikhara (Sacred Spire) - Tier 1 (Top) */}
      <path
        d="M26 12 L38 12 L40 17 L24 17 Z"
        fill={tone === "current" ? "currentColor" : `url(#${uid}-mandirSaffron)`}
        className="opacity-95"
      />
      {/* Tier 1 decorative band */}
      <path d="M29 14.5 L35 14.5" stroke="#FFE58F" strokeWidth="1" strokeLinecap="round" />

      {/* Shikhara - Tier 2 (Middle) with Urushringa (side spires) */}
      <path
        d="M23 17 L41 17 L43.5 24 L20.5 24 Z"
        fill={tone === "current" ? "currentColor" : `url(#${uid}-mandirSaffron)`}
      />
      {/* Tier 2 decorative carvings */}
      <path d="M27 20.5 L37 20.5" stroke="#FFE58F" strokeWidth="1.2" strokeLinecap="round" />
      <path d="M32 17 L32 24" stroke="#D4380D" strokeWidth="1" strokeDasharray="1 1.5" />

      {/* Shikhara - Tier 3 (Main Base of Spire) */}
      <path
        d="M19.5 24 L44.5 24 L47.5 32 L16.5 32 Z"
        fill={tone === "current" ? "currentColor" : `url(#${uid}-mandirSaffron)`}
      />
      {/* Tier 3 Gavaksha / arched medallion */}
      <path
        d="M32 25.5 C30 25.5 28.5 27 28.5 29 C28.5 30.5 30 31.5 32 31.5 C34 31.5 35.5 30.5 35.5 29 C35.5 27 34 25.5 32 25.5 Z"
        fill="#FFE58F"
        className="opacity-80"
      />

      {/* Mandapam Cornice / Chhajja (Eaves over Sanctum) */}
      <path
        d="M13 32 L51 32 L53.5 35.5 L10.5 35.5 Z"
        fill={tone === "current" ? "currentColor" : `url(#${uid}-mandirGold)`}
      />

      {/* Inner Sanctum (Garbhagriha) Background */}
      <rect x="14" y="35.5" width="36" height="17.5" fill="#2A1005" className="dark:fill-[#120803]" />

      {/* Glowing Jyoti (Aura inside Sanctum) */}
      <circle cx="32" cy="45" r="7" fill={`url(#${uid}-jyotiGlow)`} />

      {/* Sacred Sanctum Torana (Arched Doorway) */}
      <path
        d="M26 53 L26 43 C26 39.5 38 39.5 38 43 L38 53 Z"
        fill="#4A1E0E"
        stroke="#FAAD14"
        strokeWidth="1.2"
      />

      {/* Akhand Diya Flame in the central sanctum */}
      <path
        d="M32 42.5 C33.5 44.5 33 46.5 32 47.5 C31 46.5 30.5 44.5 32 42.5 Z"
        fill="#FFF566"
      />
      <ellipse cx="32" cy="48" rx="2.5" ry="1" fill="#D4380D" />

      {/* Carved Temple Pillars (Stambhas) */}
      {/* Left Outer Pillar */}
      <rect x="14" y="35.5" width="4" height="17.5" fill={`url(#${uid}-mandirGold)`} rx="0.5" />
      <rect x="13" y="35.5" width="6" height="2" fill="#D48806" />
      <rect x="13" y="51" width="6" height="2" fill="#D48806" />

      {/* Left Inner Pillar */}
      <rect x="22" y="35.5" width="3.5" height="17.5" fill={`url(#${uid}-mandirGold)`} rx="0.5" />
      <rect x="21" y="35.5" width="5.5" height="2" fill="#D48806" />
      <rect x="21" y="51" width="5.5" height="2" fill="#D48806" />

      {/* Right Inner Pillar */}
      <rect x="38.5" y="35.5" width="3.5" height="17.5" fill={`url(#${uid}-mandirGold)`} rx="0.5" />
      <rect x="37.5" y="35.5" width="5.5" height="2" fill="#D48806" />
      <rect x="37.5" y="51" width="5.5" height="2" fill="#D48806" />

      {/* Right Outer Pillar */}
      <rect x="46" y="35.5" width="4" height="17.5" fill={`url(#${uid}-mandirGold)`} rx="0.5" />
      <rect x="45" y="35.5" width="6" height="2" fill="#D48806" />
      <rect x="45" y="51" width="6" height="2" fill="#D48806" />

      {/* Adhisthana (Temple Plinth Foundation Tier 1) */}
      <path
        d="M9 53 L55 53 L56 56.5 L8 56.5 Z"
        fill={tone === "current" ? "currentColor" : `url(#${uid}-mandirSaffron)`}
      />

      {/* Adhisthana (Temple Plinth Foundation Tier 2 / Jagati) */}
      <rect
        x="6"
        y="56.5"
        width="52"
        height="4.5"
        rx="1"
        fill={tone === "current" ? "currentColor" : "#871400"}
        className="dark:fill-[#5C0D00]"
      />

      {/* Central Entrance Steps (Sopana) */}
      <rect x="24" y="53" width="16" height="1.8" fill="#FFE58F" />
      <rect x="22" y="54.8" width="20" height="1.8" fill="#FFD666" />
      <rect x="20" y="56.6" width="24" height="2" fill="#FAAD14" />
      <rect x="18" y="58.6" width="28" height="2.4" rx="0.5" fill="#D48806" />
    </svg>
  );
}

/**
 * Compact Indian Mandir Icon with Dhwaja & Shikhara (for inline labels, tabs, and buttons)
 */
export function IndianMandirIcon({ className = "size-4 text-primary" }: { className?: string }) {
  return (
    <svg
      className={`${className} shrink-0 inline-block`}
      viewBox="0 0 24 24"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
      {/* Saffron Dhwaja (Temple Flag) */}
      <path d="M12 1 L12 4 L16 2.5 Z" fill="#FA541C" />
      <path d="M12 1 L12 4.5" stroke="#FA541C" strokeWidth="1" strokeLinecap="round" />

      {/* Kalash */}
      <circle cx="12" cy="4.5" r="1" fill="#FAAD14" />

      {/* Stepped Shikhara (Spire) */}
      <path d="M10 6 L14 6 L15 9 L9 9 Z" fill="currentColor" />
      <path d="M8 9 L16 9 L17.5 13 L6.5 13 Z" fill="currentColor" />

      {/* Cornice */}
      <rect x="5" y="13" width="14" height="1.5" rx="0.5" fill="#FAAD14" />

      {/* Sanctum Doorway with Arched Portal */}
      <rect x="6" y="14.5" width="12" height="6.5" fill="currentColor" opacity="0.15" />
      {/* Left & Right Pillars */}
      <rect x="6" y="14.5" width="1.5" height="6.5" fill="currentColor" />
      <rect x="16.5" y="14.5" width="1.5" height="6.5" fill="currentColor" />

      {/* Central Sanctum Arch */}
      <path d="M10 21 L10 17.5 C10 16.5 14 16.5 14 17.5 L14 21 Z" fill="currentColor" />
      {/* Inner flame */}
      <circle cx="12" cy="18.5" r="0.8" fill="#FFE58F" />

      {/* Base Plinth & Steps */}
      <rect x="4" y="21" width="16" height="1.5" rx="0.5" fill="currentColor" />
      <rect x="3" y="22.5" width="18" height="1" rx="0.5" fill="#FAAD14" />
    </svg>
  );
}

/**
 * Wordmark with Indian Mandir Mark
 */
export function MandirCenterWordmark({ className = "" }: { className?: string }) {
  return (
    <div className={`flex items-center gap-2 ${className}`}>
      <MandirLogo className="size-7 sm:size-8" />
      <span className="font-spectral text-base sm:text-lg font-bold tracking-tight text-fg">
        Mandir<span className="text-primary-strong">Center</span>
      </span>
    </div>
  );
}
