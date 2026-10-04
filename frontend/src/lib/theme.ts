/** Theme preference (light/dark/device). Pure helpers; the cookie is read by the root layout. */

export type ThemeChoice = "light" | "dark";
export const THEME_COOKIE = "sc_theme";

/** Only the two literal values are honoured: the cookie is user-controlled input. */
export function parseTheme(raw: string | undefined | null): ThemeChoice | null {
  return raw === "light" || raw === "dark" ? raw : null;
}

/** Not sensitive, so readable by JS; one year; Lax; Secure on https. */
export function themeCookie(choice: ThemeChoice | null, secure: boolean): string {
  const base = `${THEME_COOKIE}=${choice ?? ""}; Path=/; SameSite=Lax${secure ? "; Secure" : ""}`;
  return choice ? `${base}; Max-Age=31536000` : `${base}; Max-Age=0`;
}
