# Staff app navigation

Decided 2026-10-04, revised the same day to the prototype's layout. Code: `frontend/src/lib/apps.ts`
(model, unit-tested), `frontend/src/components/AppSwitcher.tsx`, `frontend/src/app/(app)/layout.tsx`.

## Pattern: one bar, with the module switcher as the navigation

Decided in review (2026-10-04): a single header bar, no row of module tabs under it; the dropdown
switcher already lists every module. The bar holds the **module switcher** (bordered button: 9-dot
icon + the current module's name), the diya and SevaCenter wordmark, the trust's slug with a live
dot, a **Mandir Center ↗** link to the temple's public site, theme, language and the user menu.

**Related modules live together** (decided in review): the switcher lists six sections, not ten
modules, and a section's pages switch from a small control at the top of the page.

| Section | Pages |
|---|---|
| Dashboard | — |
| People | Devotees · Sevak Hub |
| Finance & 80G | Donations · Payment setup |
| Pujas & Utsavs | Pujas & Sankalp · Utsavs & Passes |
| Mandir Center | — |
| Administration | Staff & permissions · Audit Trail |

Each switcher tile has the section's icon on its tint, name, a one-line description and its pages
as chips (en/hi). The header button names the current section. Inside a page, its own views (e.g.
Sankalp roster / Puja catalog) are a segmented switcher beside the title.

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
