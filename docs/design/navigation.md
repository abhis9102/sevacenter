# Staff app navigation

Decided 2026-10-04, revised the same day to the prototype's layout. Code: `frontend/src/lib/apps.ts`
(model, unit-tested), `frontend/src/components/AppSwitcher.tsx`, `frontend/src/app/(app)/layout.tsx`.

## Pattern: a module switcher over a full module bar

The header has two tiers:

1. **Top bar** — the **module switcher** (bordered button: 9-dot icon + the current module's name),
   the diya and SevaCenter wordmark, the trust's slug with a live dot, a **Mandir Center ↗** link to
   the temple's public site, theme, language, user menu.
2. **Module bar** — one tab per module the user can open, always visible:
   Dashboard · Devotees · Donations · Payments · Pujas & Sankalp · Utsavs & Passes · Sevak Hub ·
   Mandir Center · Staff · Audit Trail.

The switcher panel shows the same modules as tiles: icon on a category tint, name, category badge
(Operations, People, Finance & 80G, Pujas & Utsavs, Public portal, Administration) and a one-line
description, in English and Hindi. Inside a module, its own views (e.g. Sankalp roster / Puja
catalog) are a segmented switcher on the page, not more nav tiers.

"Temple site/page" is called **Mandir Center** everywhere on the staff side: it is the product name
of the public temple site.

## Rules

- **Visibility = access.** A module shows only if the user's role *and* module limits allow it
  (`canOpen`, the same rules the API enforces). This is a hint; the server still checks every call.
- Switcher: disclosure button (`aria-expanded`, `aria-controls`); closes on Escape (focus back to
  the button), outside click and navigation. Icons are inline SVG on theme-token tints, never
  emoji, so they work in dark mode.
- Phones: the top bar keeps the switcher and user menu; the module bar scrolls sideways; theme,
  language and the Mandir Center link move into the switcher panel's footer.
