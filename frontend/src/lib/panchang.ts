/**
 * Daily panchang (pure, unit-tested; no React here): tithi, paksha, nakshatra and lunar month,
 * each with when it ends.
 *
 * Sun and Moon positions use Meeus, "Astronomical Algorithms" (2nd ed.): ch. 25 (low-accuracy
 * Sun, ~0.01°) and ch. 47 (Moon, the main periodic terms, ~0.01°). Tithi and nakshatra are global
 * instants, so no temple location is needed. Sidereal positions use the Lahiri ayanamsa. At the
 * Moon's ~0.5°/hour, the end times are good to a few minutes, so the UI rounds them.
 */

const RAD = Math.PI / 180;
const DAY_MS = 86_400_000;

const norm = (deg: number) => ((deg % 360) + 360) % 360;
/** -180..180, for distances around the circle. */
const wrap = (deg: number) => norm(deg + 180) - 180;
const sin = (deg: number) => Math.sin(deg * RAD);

export const julianDay = (at: Date) => at.getTime() / DAY_MS + 2440587.5;
const centuries = (jd: number) => (jd - 2451545.0) / 36525;

/** Apparent geocentric ecliptic longitude of the Sun, degrees (Meeus 25.2–25.8). */
export function sunLongitude(jd: number): number {
  const T = centuries(jd);
  const L0 = 280.46646 + 36000.76983 * T + 0.0003032 * T * T;
  const M = 357.52911 + 35999.05029 * T - 0.0001537 * T * T;
  const C = (1.914602 - 0.004817 * T - 0.000014 * T * T) * sin(M) + (0.019993 - 0.000101 * T) * sin(2 * M)
    + 0.000289 * sin(3 * M);
  const omega = 125.04 - 1934.136 * T;
  return norm(L0 + C - 0.00569 - 0.00478 * sin(omega));
}

// Meeus table 47.A, longitude terms: [D, M, M', F, coefficient in 1e-6 degrees].
const MOON_TERMS: ReadonlyArray<readonly [number, number, number, number, number]> = [
  [0, 0, 1, 0, 6288774], [2, 0, -1, 0, 1274027], [2, 0, 0, 0, 658314], [0, 0, 2, 0, 213618],
  [0, 1, 0, 0, -185116], [0, 0, 0, 2, -114332], [2, 0, -2, 0, 58793], [2, -1, -1, 0, 57066],
  [2, 0, 1, 0, 53322], [2, -1, 0, 0, 45758], [0, 1, -1, 0, -40923], [1, 0, 0, 0, -34720],
  [0, 1, 1, 0, -30383], [2, 0, 0, -2, 15327], [0, 0, 1, 2, -12528], [0, 0, 1, -2, 10980],
  [4, 0, -1, 0, 10675], [0, 0, 3, 0, 10034], [4, 0, -2, 0, 8548], [2, 1, -1, 0, -7888],
  [2, 1, 0, 0, -6766], [1, 0, -1, 0, -5163], [1, 1, 0, 0, 4987], [2, -1, 1, 0, 4036],
  [2, 0, 2, 0, 3994], [4, 0, 0, 0, 3861], [2, 0, -3, 0, 3665], [0, 1, -2, 0, -2689],
  [2, 0, -1, 2, -2602], [2, -1, -2, 0, 2390], [1, 0, 1, 0, -2348], [2, -2, 0, 0, 2236],
  [0, 1, 2, 0, -2120], [0, 2, 0, 0, -2069], [2, -2, -1, 0, 2048], [2, 0, 1, -2, -1773],
  [2, 0, 0, 2, -1595], [4, -1, -1, 0, 1215], [0, 0, 2, 2, -1110], [3, 0, -1, 0, -892],
  [2, 1, 1, 0, -810], [4, -1, -2, 0, 759], [0, 2, -1, 0, -713], [2, 2, -1, 0, -700],
  [2, 1, -2, 0, 691], [2, -1, 0, -2, 596], [4, 0, 1, 0, 549], [0, 0, 4, 0, 537],
  [4, -1, 0, 0, 520], [1, 0, -2, 0, -487], [2, 1, 0, -2, -399], [0, 0, 2, -2, -381],
  [1, 1, 1, 0, 351], [3, 0, -2, 0, -340], [4, 0, -3, 0, 330], [2, -1, 2, 0, 327],
  [0, 2, 1, 0, -323], [1, 1, -1, 0, 299], [2, 0, 3, 0, 294],
];

/** Apparent geocentric ecliptic longitude of the Moon, degrees (Meeus ch. 47 + nutation in longitude). */
export function moonLongitude(jd: number): number {
  const T = centuries(jd);
  const T2 = T * T, T3 = T2 * T, T4 = T3 * T;
  const Lp = 218.3164477 + 481267.88123421 * T - 0.0015786 * T2 + T3 / 538841 - T4 / 65194000;
  const D = 297.8501921 + 445267.1114034 * T - 0.0018819 * T2 + T3 / 545868 - T4 / 113065000;
  const M = 357.5291092 + 35999.0502909 * T - 0.0001536 * T2 + T3 / 24490000;
  const Mp = 134.9633964 + 477198.8675055 * T + 0.0087414 * T2 + T3 / 69699 - T4 / 14712000;
  const F = 93.272095 + 483202.0175233 * T - 0.0036539 * T2 - T3 / 3526000 + T4 / 863310000;
  const E = 1 - 0.002516 * T - 0.0000074 * T2;
  let sum = 0;
  for (const [d, m, mp, f, c] of MOON_TERMS) {
    const e = Math.abs(m) === 1 ? E : Math.abs(m) === 2 ? E * E : 1;
    sum += c * e * sin(d * D + m * M + mp * Mp + f * F);
  }
  const A1 = 119.75 + 131.849 * T, A2 = 53.09 + 479264.29 * T;
  sum += 3958 * sin(A1) + 1962 * sin(Lp - F) + 318 * sin(A2);
  // Nutation in longitude (Meeus 22, main terms), so Sun and Moon share the same (true) equinox.
  const omega = 125.04452 - 1934.136261 * T;
  const L = 280.4665 + 36000.7698 * T, L1 = 218.3165 + 481267.8813 * T;
  const dPsi = (-17.2 * sin(omega) - 1.32 * sin(2 * L) - 0.23 * sin(2 * L1) + 0.21 * sin(2 * omega)) / 3600;
  return norm(Lp + sum / 1e6 + dPsi);
}

/** Lahiri (Chitrapaksha) ayanamsa, degrees: 23°51'11" at J2000.0 plus general precession. */
export function lahiri(jd: number): number {
  const T = centuries(jd);
  return 23.853056 + 1.396886 * T + 0.000308 * T * T;
}

/** Moon − Sun, 0..360. Each 12° is one tithi. */
export const elongation = (jd: number) => norm(moonLongitude(jd) - sunLongitude(jd));
const tithiAt = (jd: number) => Math.floor(elongation(jd) / 12);
const nakshatraAt = (jd: number) => Math.floor(norm(moonLongitude(jd) - lahiri(jd)) / (360 / 27));
const sunRashiAt = (jd: number) => Math.floor(norm(sunLongitude(jd) - lahiri(jd)) / 30);

/** When fn(jd) next differs from fn(start): hourly steps, then bisection to under a minute. */
function nextChange(fn: (jd: number) => number, start: number): number {
  const now = fn(start);
  let lo = start, hi = start + 1 / 24;
  for (let i = 0; i < 24 * 3 && fn(hi) === now; i++) {
    lo = hi;
    hi += 1 / 24;
  }
  while (hi - lo > 0.5 / 1440) {
    const mid = (lo + hi) / 2;
    if (fn(mid) === now) lo = mid; else hi = mid;
  }
  return hi;
}

/** The new moon nearest to `guess` (Newton steps on the wrapped elongation). */
function newMoonNear(guess: number): number {
  let jd = guess;
  for (let i = 0; i < 6; i++) {
    jd -= wrap(elongation(jd)) / 12.1907;
  }
  return jd;
}

export const TITHIS = {
  en: ["Pratipada", "Dwitiya", "Tritiya", "Chaturthi", "Panchami", "Shashthi", "Saptami", "Ashtami", "Navami",
    "Dashami", "Ekadashi", "Dwadashi", "Trayodashi", "Chaturdashi", "Purnima", "Amavasya"],
  hi: ["प्रतिपदा", "द्वितीया", "तृतीया", "चतुर्थी", "पंचमी", "षष्ठी", "सप्तमी", "अष्टमी", "नवमी", "दशमी", "एकादशी",
    "द्वादशी", "त्रयोदशी", "चतुर्दशी", "पूर्णिमा", "अमावस्या"],
} as const;

export const NAKSHATRAS = {
  en: ["Ashwini", "Bharani", "Krittika", "Rohini", "Mrigashira", "Ardra", "Punarvasu", "Pushya", "Ashlesha", "Magha",
    "Purva Phalguni", "Uttara Phalguni", "Hasta", "Chitra", "Swati", "Vishakha", "Anuradha", "Jyeshtha", "Mula",
    "Purva Ashadha", "Uttara Ashadha", "Shravana", "Dhanishta", "Shatabhisha", "Purva Bhadrapada",
    "Uttara Bhadrapada", "Revati"],
  hi: ["अश्विनी", "भरणी", "कृत्तिका", "रोहिणी", "मृगशिरा", "आर्द्रा", "पुनर्वसु", "पुष्य", "आश्लेषा", "मघा",
    "पूर्वा फाल्गुनी", "उत्तरा फाल्गुनी", "हस्त", "चित्रा", "स्वाति", "विशाखा", "अनुराधा", "ज्येष्ठा", "मूल",
    "पूर्वाषाढ़ा", "उत्तराषाढ़ा", "श्रवण", "धनिष्ठा", "शतभिषा", "पूर्वा भाद्रपद", "उत्तरा भाद्रपद", "रेवती"],
} as const;

export const MONTHS = {
  en: ["Chaitra", "Vaishakha", "Jyeshtha", "Ashadha", "Shravana", "Bhadrapada", "Ashwin", "Kartik",
    "Margashirsha", "Pausha", "Magha", "Phalguna"],
  hi: ["चैत्र", "वैशाख", "ज्येष्ठ", "आषाढ़", "श्रावण", "भाद्रपद", "आश्विन", "कार्तिक", "मार्गशीर्ष", "पौष", "माघ",
    "फाल्गुन"],
} as const;

export type LunarCalendar = "AMANTA" | "PURNIMANTA";

export interface Panchang {
  /** 0..29: 0–14 Shukla Pratipada..Purnima, 15–29 Krishna Pratipada..Amavasya. */
  tithi: number;
  tithiEnds: Date;
  paksha: "SHUKLA" | "KRISHNA";
  /** 0..26, Ashwini..Revati. */
  nakshatra: number;
  nakshatraEnds: Date;
  /** 0..11, Chaitra..Phalguna, in the temple's calendar. */
  month: number;
  adhik: boolean;
}

export function panchang(at: Date, calendar: LunarCalendar = "AMANTA"): Panchang {
  const jd = julianDay(at);
  const tithi = tithiAt(jd);
  const nakshatra = nakshatraAt(jd);
  // An amanta month runs new moon to new moon and is named for the Sun's sign at its start;
  // a lunation with no sankranti (Sun in the same sign at both ends) is adhik.
  const start = newMoonNear(jd - elongation(jd) / 12.1907);
  const end = newMoonNear(start + 29.53);
  const startRashi = sunRashiAt(start);
  let month = (startRashi + 1) % 12;
  const adhik = startRashi === sunRashiAt(end);
  // Purnimanta months end on the full moon: the Krishna paksha belongs to the next month.
  if (calendar === "PURNIMANTA" && tithi >= 15 && !adhik) {
    month = (month + 1) % 12;
  }
  return {
    tithi,
    tithiEnds: fromJd(nextChange(tithiAt, jd)),
    paksha: tithi < 15 ? "SHUKLA" : "KRISHNA",
    nakshatra,
    nakshatraEnds: fromJd(nextChange(nakshatraAt, jd)),
    month,
    adhik,
  };
}

const fromJd = (jd: number) => new Date(Math.round((jd - 2440587.5) * DAY_MS));

/** Name of tithi 0..29 within its paksha ("Ashtami"; Purnima at 14, Amavasya at 29). */
export function tithiName(tithi: number, lang: "en" | "hi"): string {
  if (tithi === 29) return TITHIS[lang][15];
  return TITHIS[lang][tithi % 15]!;
}
