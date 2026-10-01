# SevaCenter Design System

Visual identity drawn from the temple itself — saffron (marigold garland), maroon (sanctum/
sindoor), gold (the lamp) — balanced with the restraint a platform handling devotee PII,
donations, and 80G receipts needs to feel trustworthy. Two surfaces share the system:

- **Admin console** (trust staff): dense, scannable, state visible at a glance.
- **Public page** (donors): warm, welcoming, credible — it takes money.

Live preview: `styleguide.html` (published as the "SevaCenter Design System" artifact).
These tokens become the Tailwind/CSS theme at the Next.js frontend milestone.

## Colour tokens

| Token | Light | Dark | Use |
|---|---|---|---|
| `--saffron` (primary) | `#D9691A` | `#F2913D` | Primary actions, brand |
| `--saffron-strong` | `#A84D0D` | `#FBB065` | Primary text on light, hover |
| `--maroon` (secondary) | `#7E2232` | `#D6788A` | Headers, depth |
| `--gold` (accent) | `#B98C2E` | `#E0BC6A` | Badges, highlights (sparing) |
| `--bg` | `#FAF5EE` | `#191410` | Page background (warm sand) |
| `--surface` | `#FFFFFF` | `#221B14` | Cards, panels |
| `--surface-2` | `#F2E9DB` | `#2C241B` | Raised/secondary fills |
| `--fg` | `#241B14` | `#F4EADD` | Text (warm near-black) |
| `--muted` | `#7C6F62` | `#B2A595` | Secondary text |
| `--border` | `#E7DBC8` | `#3A2F24` | Hairlines, dividers |
| `--success` | `#2F7A52` | `#5CB487` | Paid, verified |
| `--warning` | `#C2861A` | `#E4B04A` | Pending, attention |
| `--danger` | `#BC3B30` | `#E8766B` | Failed, destructive |
| `--info` | `#2B6E86` | `#6FB6CE` | Informational |

**Rules:** saffron leads, maroon grounds, gold accents sparingly. Semantic colours are kept
separate from the brand hue so status never reads as decoration. Neutrals are warm (sandstone),
never cold grey. Every colour is a token with a light and dark value; nothing is theme-locked.

## Typography

| Role | Face | Notes |
|---|---|---|
| Display / headings | **Spectral** (serif) | Editorial, respectful voice |
| Body / UI | **Mukta** (sans) | Drawn for Indian scripts — grounded, not generic Inter |
| Data / amounts | **IBM Plex Mono** | Rupee figures, receipts; always `tabular-nums` |

All three are on Google Fonts. Keep running text near 65 characters; uppercase labels get
`letter-spacing`.

## Primitives

- Radius `12px` (cards), `10px` (controls); radius/shadow applied by role, not everywhere.
- Spacing via flex/grid `gap`; 16px+ side gutter at every width; stacks to one column ~400px.
- Focus-visible: 2px saffron outline. Respect `prefers-reduced-motion`.

## Brand mark

A **diya** (oil lamp) with a saffron flame over a maroon bowl — see the inline SVG in
`styleguide.html`. Wordmark: "Seva" in `--fg`, "Center" in `--saffron-strong`.

## Open for feedback

- Primary saffron vs. a deeper vermilion — react to the live preview.
- Serif (Spectral) display vs. an all-sans option, if the admin console should feel more utilitarian.
- Whether the diya mark or a lotus reads better as the logo.
