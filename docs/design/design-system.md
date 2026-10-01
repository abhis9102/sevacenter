# SevaCenter + MandirCenter Design System

One design language, two brands (like Planning Center / Church Center):

| Brand | Role | Domain | Feel |
|---|---|---|---|
| **SevaCenter** | Admin platform, for trust staff | `app.sevacenter.app` | Restrained, data-dense, calm for all-day work |
| **MandirCenter** | Public devotee site, per-temple subdomains | `yourtemple.mandircenter.app` | Vibrant, devotional, welcoming |

Both share typography, neutrals, spacing, and the diya mark. They differ in the accent palette:
SevaCenter uses a muted saffron; MandirCenter uses the full Hindu devotional palette.

Live preview: `styleguide.html` ("SevaCenter Design System" artifact). These tokens become the
Tailwind/CSS theme at the Next.js milestone.

## SevaCenter palette (admin)

| Token | Light | Dark | Use |
|---|---|---|---|
| `--sc-primary` | `#C96A1E` | `#E6903F` | Primary actions, brand (muted saffron) |
| `--sc-primary-strong` | `#9A4E10` | `#F3AC68` | Primary text on light, hover |
| `--sc-maroon` | `#7E2232` | `#D6788A` | Headers, depth |
| `--sc-gold` | `#B98C2E` | `#E0BC6A` | Badges, highlights (sparing) |

## MandirCenter palette (public — Hindu devotional)

| Token | Light | Dark | Use |
|---|---|---|---|
| `--mc-primary` (bhagwa) | `#E85D04` | `#FB8521` | Primary actions, brand saffron |
| `--mc-primary-strong` | `#B8450A` | `#FCA14C` | Text/hover |
| `--mc-kumkum` (red) | `#C1121F` | `#E8565F` | Auspicious accent, alerts |
| `--mc-haldi` (turmeric) | `#EFA00B` | `#F3BC4E` | Highlights, festive accent |
| `--mc-maroon` | `#6A0F1A` | `#E0808C` | Depth |
| `--mc-gold` | `#CBA135` | `#E3C06A` | Accent |
| `--mc-bg` (saffron cream) | `#FFF5E6` | `#241A12` | Public page background |

## Shared neutrals & semantic

| Token | Light | Dark |
|---|---|---|
| `--bg` | `#FAF5EE` | `#191410` |
| `--surface` | `#FFFFFF` | `#221B14` |
| `--surface-2` | `#F2E9DB` | `#2C241B` |
| `--fg` | `#241B14` | `#F4EADD` |
| `--muted` | `#7C6F62` | `#B2A595` |
| `--border` | `#E7DBC8` | `#3A2F24` |
| `--success` | `#2F7A52` | `#5CB487` |
| `--warning` | `#C2861A` | `#E4B04A` |
| `--danger` | `#BC3B30` | `#E8766B` |
| `--info` | `#2B6E86` | `#6FB6CE` |

**Rules:** the brand saffron leads; maroon grounds; gold/haldi accent sparingly. Semantic colours
stay separate from brand hues so status never reads as decoration. Neutrals are warm (sandstone).
Every colour has a light and dark value; nothing is theme-locked.

## Typography (shared)

| Role | Face | Notes |
|---|---|---|
| Display / headings | **Spectral** (serif) | Editorial, respectful |
| Body / UI | **Mukta** (sans) | Drawn for Indian scripts — grounded, not generic Inter |
| Data / amounts | **IBM Plex Mono** | Rupee figures, receipts; `tabular-nums` |

## Primitives

Radius `12px` cards / `10px` controls; radius/shadow by role, not everywhere. Spacing via flex/grid
`gap`; 16px+ side gutter; stacks to one column ~400px. Focus-visible: 2px saffron outline. Respect
`prefers-reduced-motion`.

## Brand mark

A **diya** (oil lamp): saffron flame over a maroon bowl (inline SVG in `styleguide.html`).
Per-temple accent theming on MandirCenter subdomains is a later possibility (tenant branding).

## Open for feedback

- Any MandirCenter colours too strong/soft against the live preview?
- Diya mark vs. a lotus for the logo.
