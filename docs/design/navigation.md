# Staff app navigation

Decided 2026-10-04. Code: `frontend/src/lib/apps.ts` (model, unit-tested),
`frontend/src/components/AppSwitcher.tsx`, `frontend/src/app/(app)/layout.tsx`.

## Pattern: apps, then pages

Staff pages are grouped into a small set of **apps**. The header has two tiers:

1. **Top bar** — diya (home), the **app switcher** naming the current app, the trust's slug,
   a "Temple site ↗" link, theme, language, user menu.
2. **Second bar** — tabs for the **current app's pages** only.

| App | Pages |
|---|---|
| Overview | Dashboard |
| People | Devotees, Volunteers |
| Giving | Donations, Payments |
| Pujas & Events | Pujas, Events |
| Temple Site | Temple page |
| Administration | Staff, Audit log |

Why: a single row of every page stopped scaling at ~10 items and mixed daily work (pujas,
donations) with settings. Grouping keeps each bar short and gives every area a name, colour and
icon staff can recognise. New pages join an existing app; a new app needs a real area of work.

## Rules

- **Visibility = access.** An app shows only the pages the user's role *and* module limits allow
  (`canOpen`, the same rules the API enforces), and an app with no openable pages is hidden. This
  is a hint; the server still checks every call.
- Pages outside any app (e.g. Profile) show the switcher with no second bar.
- Switcher: disclosure button (`aria-expanded`, `aria-controls`); closes on Escape (focus back to
  the button), outside click and navigation. Tiles carry name + one-line description, in English
  and Hindi. Icons are inline SVG on theme-token tints, never emoji, so they work in dark mode.
- Phones: top bar keeps only home, switcher and user menu; theme, language and the temple-site
  link move into the switcher panel's footer.
