# Thirdline design tokens → Atheris ESG theme (D47)

Measured from the live Thirdline app (https://thirdline.atherislimited.com, signed-in session, 1440 × 900 and 375 px)
on 18 Oct 2026 with computed styles and its stylesheet. Thirdline is Laravel + Tailwind; ESG stays on MUI (D2), so each
Tailwind value is carried by the MUI theme.

Source of truth in code: `esg-frontend/src/theme/tokens.js` (values) and `esg-frontend/src/theme/lightTheme.js` (how MUI
uses them). Pages should not restate these values.

## Typography

| Token | Thirdline | ESG (MUI) |
|---|---|---|
| Font | Figtree 400/500/600 (fonts.bunny.net), system fallback | Figtree 400/500/600/700 via `@fontsource/figtree` (self-hosted, WP-52) |
| Mono | `ui-monospace, SFMono-Regular, Menlo…` (IDs such as `AUD-2026-0008`, 12 px gray-500) | same stack (`theme.typography.mono`; replaces Roboto Mono) |
| Base | 14 px (`text-sm`) body, 20 px line | `body1`, `body2`: 0.875rem / 1.25rem |
| Page title | `.page-title` 24 px / 700, `#2D3748` | `h2` (PageHeader renders `h2` as `<h1>`) |
| Card title | 14 px / 600, gray-800 | `h6`, `CardHeader` title |
| KPI value | 24 px / 700 | `h4` |
| KPI label | 14 px / 500, gray-500 | `body2` + weight 500, `text.secondary` |
| Secondary / caption | 12 px, gray-500 | `caption` |
| Table head | 12 px / 600, uppercase, 0.05em, gray-500 | `MuiTableCell.head` |
| Form label | `.form-label` 14 px / 500, gray-700, 4 px below | `MuiInputLabel` / `MuiFormLabel`, label above the field |
| Button | 14 px / 600, no transform | `typography.button` |

## Colour

| Token | Thirdline (`--color-*`) | ESG palette |
|---|---|---|
| Primary | `#1A365D` (light `#2A4A7F`, dark `#0F2340`) | `primary` |
| Accent (gold) | `#D4AF37` (light `#E2C458`) | `accent` (new), active nav bar, chart series 3 |
| Secondary / success | `#2D7D46` (light `#38A169`) | `secondary`, `success` |
| Error | `#C53030` | `error` |
| Warning | `#DD6B20` | `warning` (was gold `#D4AF37`) |
| Info | `#319795` | `info` |
| Text | `#2D3748` / `#718096` | `text.primary` `#2D3748`; `text.secondary` gray-500 `#6B7280` (Thirdline's `#718096` is 4.0:1 on white — below WCAG AA, see WP-53) |
| Page background | `#F7FAFC` | `background.default` |
| Card | `#FFFFFF` | `background.paper` |
| Neutrals | Tailwind gray 50–900 | `palette.grey` = Tailwind gray |
| Borders | cards gray-100 `#F3F4F6`; inputs gray-300 `#D1D5DB`; rules gray-200 `#E5E7EB` | `divider` gray-200; component overrides |

Status badges (`.badge-*`, pale background + dark text) → filled `Chip` colours:

| Thirdline class | Background / text | ESG Chip |
|---|---|---|
| `badge-status-draft` | `#F3F4F6` / `#374151` | `default` |
| `badge-status-pending` | `#DBEAFE` / `#1D4ED8` | `primary` |
| `badge-status-active`, `badge-low` | `#DCFCE7` / `#15803D` | `success`, `secondary` |
| `badge-critical` | `#FEE2E2` / `#991B1B` | `error` |
| `badge-high` | `#FFEDD5` / `#9A3412` | `warning` |
| `badge-medium` | `#FEF9C3` / `#854D0E` | `tokens.badge.caution` (ESG pillar G, audit "update") |
| `badge-status-completed` | `#F3E8FF` / `#7E22CE` | `tokens.badge.completed` |

## Shape, elevation, spacing

| Token | Thirdline | ESG |
|---|---|---|
| Radius | buttons/inputs 8 px (`rounded-lg`); cards 12 px (`rounded-xl`); modals 16 px (`rounded-2xl`); badges pill | `shape.borderRadius` 8; `MuiCard`/`MuiPaper` 12; `MuiDialog` 16; `MuiChip` pill |
| Card | `.card`/`.stat-card`: 1 px gray-100 border, `shadow-sm` `0 1px 2px rgba(0,0,0,.05)`, hover `shadow-md`, padding 20 px | `MuiCard`, `MuiCardContent` 20 px |
| Card header | `.card-header` 16 × 24 px, gray-100 bottom rule | `MuiCardHeader` |
| Shadows | Tailwind sm / base / md / lg / xl / 2xl | `theme.shadows` (1 = sm, 8 = lg for menus, 24 = 2xl for dialogs) |
| Top bar | 64 px, white, gray-200 bottom rule | `TopBar` (was 56 px) |
| Sidebar | 260 px, `#1A365D`, 64 px logo band, white/10 rules | `Sidebar` |
| Page | header band white (`px-6 py-4`), content `p-6` on `#F7FAFC` | `PageHeader` band + `main` padding 24 px |

## Components

| Pattern | Thirdline | ESG |
|---|---|---|
| Nav item | `.sidebar-nav-item`: 40 px, 10 × 16 padding, 12 px gap, 20 px outline icon, 14/500, white/70; hover white/5; active white/10 + 3 px gold left border | `Sidebar` `navItemSx` |
| Nav groups | collapsible groups (chevrons) | ESG keeps its four labelled sections (10 px uppercase white/50) — the permission model already groups pages |
| Top bar | search (left), bell with red `9+` badge, 32 px navy avatar + name (14/500 gray-700) + role (12 gray-500) + chevron | same, without search (not built — FIELD_INVENTORY "Global search") |
| Notifications | bell links to a notifications page | ESG keeps its popover; styled as a Thirdline card (header rule, blue-50 unread rows, `9+` badge) |
| Button primary | navy, white 14/600, 8 × 16, hover primary-light | `MuiButton` contained |
| Button secondary | white, gray-300 border, navy text, hover gray-50 | `MuiButton` outlined |
| Input / select | 38 px, 8 × 12 padding, gray-300 border, `shadow-sm`, focus primary border + ring | `MuiOutlinedInput` (no floating label, no notch) |
| Table | head gray-50, 12/600 uppercase gray-500; cells 12 × 16, gray-100 rules; row hover gray-50 | `MuiTableCell`, `MuiTableRow` |
| Modal | slate-900/70 backdrop with blur, white 16 px radius, `shadow-2xl` | `MuiDialog` + title/actions rules |
| Charts | hand-built SVG: grid `#E2E8F0` (horizontal only), axis text `#94A3B8` 10 px, series error/success/gold; donut red/orange/amber/green-600 | Recharts styled once in `MuiCssBaseline` (grid, ticks, legend, tooltip) + `tokens.chart.series` / `.severity`; column charts horizontal grid only, 4 px rounded bar tops |
| Icons | Heroicons v2 outline, 1.5 stroke, 20 px | MUI `*Outlined` icons (closest outline set in `@mui/icons-material`; no second icon library under D2) |
| Progress bar | rounded, gray-200 track | `MuiLinearProgress` 8 px |
| Links | navy, underline on hover | `a` and `MuiLink` |

## Not carried over

- Thirdline's app is light only: its stylesheet has one `prefers-color-scheme: dark` block with a handful of `dark:` utilities
  (Laravel starter pages), but the shell, cards and tables use fixed light colours and there is no theme switch. ESG
  stays light only.
- Thirdline's "Online" status pill and collapsible sidebar are app features, not tokens; ESG's offline status and fixed
  sidebar are unchanged.

## Comparison screenshots

ESG after the change: `docs/esg-completion/ui-thirdline/` (dashboard, Data Hub table, Data Hub dialog, Carbon charts,
mobile). The matching Thirdline captures show live audit data from that product, so they are not committed to this
repository; they were shared with the reviewer outside it.
