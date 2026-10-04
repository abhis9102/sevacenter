# Staff app navigation

Decided 2026-10-04, revised the same day to the prototype's layout. Code: `frontend/src/lib/apps.ts`
(model, unit-tested), `frontend/src/components/AppSwitcher.tsx`, `frontend/src/app/(app)/layout.tsx`.

## Pattern: one bar, with the module switcher as the navigation

Decided in review (2026-10-04): a single header bar, no row of module tabs under it; the dropdown
switcher already lists every module. The bar holds the **module switcher** (bordered button: 9-dot
icon + the current module's name), the diya and SevaCenter wordmark, the trust's slug with a live
dot, a **Mandir Center ↗** link to the temple's public site, theme, language and the user menu.

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
- Phones: the bar keeps the switcher and user menu; theme,
  language and the Mandir Center link move into the switcher panel's footer.
