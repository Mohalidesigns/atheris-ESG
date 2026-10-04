# AuditPro GRC — Design System

**Version:** 2.0  
**Last Updated:** April 8, 2026  
**Maintained By:** Product & Engineering Team

This design system is the single source of truth for all AuditPro GRC interfaces. Every new screen, component, or feature must reference this document to ensure visual and behavioral consistency across the platform.

---

## 1. Brand Identity

### 1.1 Product Name & Tagline

**Product:** AuditPro GRC  
**Tagline:** ENTERPRISE SUITE  
**Full Name:** AuditPro GRC Enterprise Suite

### 1.2 Logo Construction

The logo consists of two elements placed side by side with a 12px gap:

**Logo Icon:** A 36×36px square with 8px border-radius, filled with a 135° linear gradient from `#D4AF37` (Gold) to `#B8962E` (Dark Gold). The icon contains a white shield glyph (`fa-shield-halved`) centered at 18px font size.

**Logo Text:** Two lines stacked vertically — the product name "AuditPro GRC" in 16px / 700 weight white, and the tagline "ENTERPRISE SUITE" in 10px / 500 weight Gold (`#D4AF37`) with 1px letter-spacing.

```
┌──────────┐
│  Shield  │  AuditPro GRC        ← 16px, bold, white
│  Icon    │  ENTERPRISE SUITE    ← 10px, medium, gold
└──────────┘
  36×36px
  gradient gold
```

### 1.3 Brand Voice

Professional, authoritative, and precise. The interface communicates trust and institutional rigor — appropriate for internal audit, risk, and compliance professionals operating in regulated financial environments. Language should be concise and jargon-appropriate (IIA Standards, COSO, ISO terminology).

---

## 2. Color System

### 2.1 Primary Palette

| Token | Hex | RGB | Usage |
|---|---|---|---|
| `--color-primary` | `#1A365D` | 26, 54, 93 | Sidebar, headers, primary buttons, page titles, data emphasis |
| `--color-primary-light` | `#2A4A7F` | 42, 74, 127 | Hover states on primary elements |
| `--color-primary-dark` | `#0F2340` | 15, 35, 64 | Pressed states, deep backgrounds |
| `--color-primary-surface` | `#2D5F9A` | 45, 95, 154 | User avatar gradient endpoint, secondary blue |

### 2.2 Accent & Brand

| Token | Hex | RGB | Usage |
|---|---|---|---|
| `--color-accent` | `#D4AF37` | 212, 175, 55 | Active nav indicator, premium badges, section highlights, gold icons |
| `--color-accent-light` | `#E2C458` | 226, 196, 88 | Hover on accent elements |
| `--color-accent-dark` | `#B8962E` | 184, 150, 46 | Logo icon gradient end, pressed accent |

### 2.3 Semantic Colors

| Token | Hex | Usage |
|---|---|---|
| `--color-success` | `#2D7D46` | Compliant status, positive trends, low risk, plan completion |
| `--color-success-light` | `#38A169` | Hover on success elements |
| `--color-warning` | `#DD6B20` | High risk, partial compliance, fieldwork status, caution states |
| `--color-warning-light` | `#ED8936` | Hover/gradient end for warning bars |
| `--color-error` | `#C53030` | Critical risk, non-compliant, overdue, notification badges |
| `--color-error-light` | `#E53E3E` | Hover/gradient end for error bars |
| `--color-info` | `#319795` | Informational badges, teal accents |

### 2.4 Extended Semantic Colors

| Token | Hex | Usage |
|---|---|---|
| `--color-purple` | `#6B46C1` | Reporting status, systemic risk, IT service management |
| `--color-purple-bg` | `#FAF5FF` | Purple tinted backgrounds |
| `--color-yellow` | `#B7791F` | Medium risk (alternative), ICFR/financial controls |
| `--color-yellow-bg` | `#FFFFF0` | Yellow tinted backgrounds |

### 2.5 Neutral Colors

| Token | Hex | Usage |
|---|---|---|
| `--color-bg` | `#F7FAFC` | Page background, table header backgrounds |
| `--color-card` | `#FFFFFF` | Card surfaces, modal backgrounds, donut chart centers |
| `--color-text-primary` | `#2D3748` | Body text, primary readable content |
| `--color-text-secondary` | `#718096` | Breadcrumbs, labels, captions, secondary text |
| `--color-text-tertiary` | `#A0AEC0` | Placeholder text, disabled states |
| `--color-text-heading` | `#1A365D` | Same as primary — all headings use the navy |
| `--color-border` | `#E2E8F0` | Card borders, table dividers, input borders, topbar border |
| `--color-border-light` | `#F0F0F0` | Table row dividers, subtle separators |
| `--color-divider-sidebar` | `rgba(255,255,255,0.1)` | Sidebar section dividers |

### 2.6 Tinted Backgrounds (for icons, badges, and status chips)

These light backgrounds pair with their corresponding text color:

| Background | Text Color | Context |
|---|---|---|
| `#EBF4FF` | `#1A365D` | Blue/info icon backgrounds, planning badges |
| `#F0FFF4` | `#2D7D46` | Success icon backgrounds, compliant badges, active tags |
| `#FFFAF0` | `#DD6B20` | Warning icon backgrounds, fieldwork badges |
| `#FFF5F5` | `#C53030` | Error icon backgrounds, critical/non-compliant badges |
| `#FAF5FF` | `#6B46C1` | Purple icon backgrounds, reporting badges |
| `#FFFFF0` | `#B7791F` | Yellow/medium risk backgrounds |

### 2.7 Gradient Definitions

| Name | Value | Usage |
|---|---|---|
| Logo Icon | `linear-gradient(135deg, #D4AF37, #B8962E)` | Logo icon fill |
| User Avatar | `linear-gradient(135deg, #1A365D, #2D5F9A)` | Circular avatar background |
| AI Card Header | `linear-gradient(135deg, #1A365D, #2D5F9A)` | AI feature card headers |
| Error Bar | `linear-gradient(180deg, #C53030, #E53E3E)` | Chart bars — critical findings |
| Warning Bar | `linear-gradient(180deg, #DD6B20, #ED8936)` | Chart bars — high findings |

### 2.8 Heat Map Color Scale

Used for risk heat maps (5×5 grid):

| Level | Hex | CSS Class | Risk Range |
|---|---|---|---|
| Very Low | `#48BB78` | `.hm-green` | 1–4 |
| Low | `#ECC94B` | `.hm-yellow` | 5–9 |
| Medium | `#ED8936` | `.hm-orange` | 10–14 |
| High | `#E53E3E` | `.hm-red` | 15–19 |
| Critical | `#9B2C2C` | `.hm-darkred` | 20–25 |

---

## 3. Typography

### 3.1 Font Stack

| Role | Font | Fallback | Weight Range |
|---|---|---|---|
| Primary (UI) | Inter | system-ui, -apple-system, sans-serif | 300–800 |
| Secondary (config) | Figtree | system-ui, sans-serif | 400–700 |
| Monospace | Roboto Mono | monospace | 400–600 |

**Import:** `https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap`

### 3.2 Type Scale

| Element | Size | Weight | Color | Letter-Spacing | Usage |
|---|---|---|---|---|---|
| Page Title | 20px (`text-xl`) | 700 | `#1A365D` | — | Main content area heading |
| Section Title | 14px (`text-sm`) | 600 | `#1A365D` | — | Card titles, chart titles, table headers |
| KPI Value | 28px | 700 | `#1A365D` | — | Large metric numbers on stat cards |
| Stat Value (compact) | 24px | 700 | `#1A365D` | — | Smaller stat numbers in mini KPI cards |
| Stat Value (card) | `text-2xl` | 700 (bold) | contextual | — | StatCard component values |
| Body Text | 13px | 400 | `#2D3748` | — | Table cells, descriptions |
| Small Body | 12px (`text-sm`) | 400 | `#718096` | — | Breadcrumbs, descriptions, table data |
| Label | 12px | 500 | `#718096` | — | KPI labels, stat labels |
| Caption | 11px | 400–600 | `#718096` | — | Legend items, sub-labels |
| Nav Label | 10px | 600 | `rgba(255,255,255,0.4)` | 1.5px | Sidebar section labels (uppercase) |
| Nav Item | 13px | 400 | `rgba(255,255,255,0.7)` | — | Sidebar navigation items |
| Tag / Badge | 9–11px | 600 | contextual | 0.5px | Framework tags, status badges |
| Overline | 10–11px | 600 | `#718096` | 0.5–1.5px | Table headers, uppercase labels |
| Filter Label | 10px | 600 | `#9CA3AF` (gray-400) | wider | Uppercase tracking-wider labels above filter inputs |
| Button Text | 12px | 600 | contextual | widest (primary) | Uppercase for primary/danger, sentence for secondary |
| Logo Text | 16px | 700 | `#FFFFFF` | 0.5px | Product name |
| Logo Subtitle | 10px | 500 | `#D4AF37` | 1px | Tagline (uppercase) |

### 3.3 Line Heights

Body content uses a default line-height of 1.5–1.6. Headings and values use a tighter line-height of 1.2. Badges and chips use 1.0.

---

## 4. Spacing & Layout

### 4.1 Base Unit

The spacing system is built on a **4px base unit**. All spacing values should be multiples of 4.

### 4.2 Spacing Scale

| Token | Value | Tailwind | Common Usage |
|---|---|---|---|
| xs | 4px | `p-1`, `gap-1` | Icon–text gap in tight spaces |
| sm | 8px | `p-2`, `gap-2` | Badge padding, stat inner spacing |
| md | 12px | `p-3`, `gap-3` | Navigation item padding, card internal gaps |
| base | 16px | `p-4`, `gap-4` | Card padding, section spacing, grid gaps |
| lg | 20px | `p-5`, `gap-5` | Card body padding, main content padding-x |
| xl | 24px | `p-6`, `gap-6` | Content area vertical padding, section margins |
| 2xl | 32px | `p-8`, `gap-8` | Topbar horizontal padding, content area padding |

### 4.3 Layout Dimensions

| Element | Value |
|---|---|
| Sidebar width | 250px (HTML) / 260px (React `--sidebar-width`) |
| Sidebar collapsed | 72px (`--sidebar-collapsed-width`) |
| Topbar height | ~56px (16px padding × 2 + content) |
| Content padding | 24px top/bottom, 32px left/right |
| Grid gap (cards) | 16px |
| Grid gap (sections) | 20px |
| Maximum card width | Fluid within grid columns |

### 4.4 Grid Systems

**KPI Grid:** 4 equal columns, 16px gap  
**Charts Grid:** 2fr + 1fr, 20px gap  
**Framework Grid:** 3 equal columns, 16px gap  
**Bottom Section Grid:** 1fr + 1fr, 20px gap  
**Heat Map Grid:** 1fr + 1fr, 20px gap  
**AI Features Grid:** 1fr + 1fr, 20px gap  
**KPI Row (Risk):** 5 equal columns, 14px gap

---

## 5. Border Radius

| Token | Value | Tailwind | Usage |
|---|---|---|---|
| sm | 4px | `rounded` | Small tags, report section tags |
| md | 6px | `rounded-md` | Chart bars (top corners), buttons (generic) |
| base | 8px | `rounded-lg` | Logo icon, inputs, search box, filter inputs, buttons |
| lg | 10px | `rounded-[10px]` | KPI icon boxes, framework icon boxes, stat card icons |
| xl | 12px | `rounded-xl` | Cards, table cards, modal dialogs |
| full | 9999px | `rounded-full` | Badges, status pills, avatar circles, legend dots, progress bars |

---

## 6. Shadows & Elevation

| Level | Value | Usage |
|---|---|---|
| None | `none` | Flat elements, sidebar |
| Subtle | `0 1px 3px rgba(0,0,0,0.05)` | Topbar |
| Card | `0 1px 3px rgba(0,0,0,0.08)` | All cards, table wrappers |
| Elevated | `0 4px 12px rgba(0,0,0,0.12)` | Hovered cards |
| Dropdown | Standard Tailwind `shadow-lg` | Dropdown menus |
| Modal Overlay | `shadow-2xl` | Modal and dialog panels |
| Input | `shadow-sm` | Form inputs, secondary buttons |

---

## 7. Iconography

### 7.1 Icon Library

The system uses two icon sets:

**Font Awesome 6 (Solid)** — for the HTML prototype screens  
CDN: `https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css`

**Custom Heroicons-style SVGs** — for the React production application  
Source: `/resources/js/utils/icons.jsx`  
Format: 24×24 viewBox, stroke-based, 1.5px stroke width

### 7.2 Icon Sizing

| Context | Size | Notes |
|---|---|---|
| Navigation | 14px / `w-[18px]` | Fixed width for alignment in sidebar |
| KPI Icon Box | 18px (icon inside 40×40 box) | Centered in tinted background |
| Framework Icon Box | 20px (icon inside 44×44 box) | Centered in tinted background |
| Table Inline | 10–12px | Risk dots (8×8), inline indicators |
| Analysis/Feature Icon | 12px (inside 28×28 box) | AI feature analysis items |
| Chart/Section Label | 14px | Next to card titles |
| Notification Badge Icon | 18px | Bell icon in topbar |

### 7.3 Complete Icon Inventory

#### Navigation Icons

| Icon | FA Class | SVG Name | Context |
|---|---|---|---|
| Dashboard | `fa-th-large` | — | Main nav |
| Analytics | `fa-chart-line` | `chartBar` | Main nav |
| Notifications | `fa-bell` | `bellAlert` | Main nav / topbar |
| Audit Universe | `fa-globe` | `globeAlt` | Audit management |
| Annual Plans | `fa-calendar-alt` | `calendar` | Audit management |
| Engagements | `fa-clipboard-check` | `clipboardList` | Audit management |
| Findings | `fa-exclamation-triangle` | `exclamationTriangle` | Audit management |
| Reports | `fa-file-alt` | `documentText` | Audit management |
| Follow-up | `fa-redo` | `arrowPath` | Audit management |
| Risk Register | `fa-shield-alt` | `shieldCheck` | Risk & compliance |
| Control Library | `fa-tasks` | — | Risk & compliance |
| Regulatory | `fa-balance-scale` | `scale` | Risk & compliance |
| ISO Frameworks | `fa-certificate` | — | Compliance frameworks |
| PCI DSS | `fa-credit-card` | `creditCard` | Compliance frameworks |
| SWIFT CSCF | `fa-exchange-alt` | — | Compliance frameworks |
| NDPA | `fa-user-shield` | — | Compliance frameworks |
| ICFR | `fa-file-invoice-dollar` | `bankNotes` | Compliance frameworks |
| AI Insights | `fa-robot` | — | AI assistant |
| AI History | `fa-history` | — | AI assistant |
| Settings | `fa-cog` | `cog6Tooth` | Settings |

#### Functional Icons

| Icon | FA Class | SVG Name | Context |
|---|---|---|---|
| Search | `fa-search` | `magnifyingGlass` | Search box |
| Filter | `fa-filter` | — | Filter toolbar |
| Download/Export | `fa-download` | — | Export buttons |
| Plus/Add | `fa-plus` | — | Create new actions |
| Eye/View | `fa-eye` | — | View detail buttons |
| Arrow Up | `fa-arrow-up` | — | Positive trend indicator |
| Arrow Down | `fa-arrow-down` | — | Negative trend indicator |
| Arrow Right | `fa-arrow-right` | — | "View All" links |
| Check | `fa-check` | `check` | Compliant badges |
| Check Circle | `fa-check-circle` | `checkCircle` | Success/completion |
| Times/X | `fa-times` | — | Non-compliant badges, close |
| Minus | `fa-minus` | — | Partial compliance |
| Circle | `fa-circle` | — | N/A status indicator |
| Clock | `fa-clock` | — | Overdue/time-related |
| Link | `fa-link` | `link` | Mapped controls count |
| List | `fa-list` | — | Table/list views |
| Table | `fa-table` | — | Matrix views |

#### AI Feature Icons

| Icon | FA Class | Context |
|---|---|---|
| Report Writer | `fa-file-signature` | AI report generation |
| Duplicate Detection | `fa-clone` | Semantic search |
| Risk Classification | `fa-brain` | AI classification |
| Deep Analysis | `fa-microscope` | Finding analysis |
| Sparkles | `fa-sparkles` | Generate/AI action buttons |
| Lightbulb | `fa-lightbulb` | Recommendations |
| Project Diagram | `fa-project-diagram` | Systemic assessment |
| Reply | `fa-reply` | Management response |

#### Framework-Specific Icons

| Icon | FA Class | Framework |
|---|---|---|
| Lock | `fa-lock` | ISO 27001 (Information Security) |
| Shield Virus | `fa-shield-virus` | ISO 22301 (Business Continuity) |
| Server | `fa-server` | ISO 20000 (IT Service) |
| Hard Hat | `fa-hard-hat` | ISO 45001 (Health & Safety) |
| Laptop Code | `fa-laptop-code` | ITGC / COBIT |
| Layer Group | `fa-layer-group` | All Frameworks view |
| Gavel | `fa-gavel` | Compliance risk chip |
| Microchip | `fa-microchip` | Technology risk chip |

### 7.4 Custom SVG Icon Library

The production React app defines 38 reusable SVG icons in `/resources/js/utils/icons.jsx`. All use the Heroicons outline style with `viewBox="0 0 24 24"`, `stroke="currentColor"`, and `strokeWidth="1.5"`.

```
document          pencilSquare       chartBar          clipboardList
presentation      photo              archiveBox        documentText
commandLine       wrench             paperClip         magnifyingGlass
exclamationTriangle  checkCircle     arrowPath         chatBubbleLeft
lockClosed        cog6Tooth          bankNotes         globeAlt
creditCard        devicePhoneMobile  circleStack       computerDesktop
link              check              buildingLibrary   shieldCheck
scale             folder             calendar          envelope
bookOpen          scroll             lockOpen          bellAlert
arrowUpTray       serverStack
```

---

## 8. Component Library

### 8.1 Sidebar Navigation

**Container:** 250px wide, background `#1A365D`, full viewport height, flex column layout.

**Logo Area:** 20px padding all around, bottom border `rgba(255,255,255,0.1)`. Contains logo icon + text stack with 12px gap.

**Nav Sections:** 16px vertical padding. Each section has an uppercase label and a list of items.

**Nav Label:** 8px vertical / 20px horizontal padding, 10px uppercase text, 1.5px letter-spacing, color `rgba(255,255,255,0.4)`, 600 weight.

**Nav Item (default):** 10px vertical / 20px horizontal padding, 13px text, color `rgba(255,255,255,0.7)`, 3px transparent left border, 12px icon–text gap. Icon fixed at 18px width.

**Nav Item (active):** Background `rgba(255,255,255,0.1)`, text white, left border color `#D4AF37` (gold).

**Nav Item (hover):** Background `rgba(255,255,255,0.05)`, text white.

```css
/* React production variant */
.sidebar-nav-item {
  display: flex;
  align-items: center;
  gap: 12px;       /* gap-3 */
  padding: 10px 16px; /* px-4 py-2.5 */
  font-size: 14px; /* text-sm */
  font-weight: 500; /* font-medium */
  border-radius: 8px; /* rounded-lg */
  transition: all 200ms;
}
.sidebar-nav-item.active {
  background: rgba(255,255,255,0.1); /* bg-white/10 */
  color: white;
  border-left: 3px solid var(--color-accent);
}
```

### 8.2 Top Bar

**Container:** White background, 16px vertical / 32px horizontal padding, flex row with space-between, bottom border `#E2E8F0`, subtle shadow `0 1px 3px rgba(0,0,0,0.05)`.

**Left side:** Page title (20px, bold, navy) + breadcrumb (12px, `#718096`).

**Right side:** Search box + notification bell + user avatar, 20px gap.

**Search Box:** Background `#F7FAFC`, border `#E2E8F0`, 8px radius, 8px vertical / 14px horizontal padding, 240px width. Contains search icon (`#A0AEC0`) + text input (13px, `#718096`).

**Notification Button:** 18px icon, `#718096` color, relative positioning. Badge is absolute positioned (-4px top, -4px right), background `#C53030`, white text, 9px font, 10px border-radius.

**User Avatar:** 36×36px circle, gradient background `#1A365D` → `#2D5F9A`, white initials at 14px / 600 weight.

### 8.3 Cards

**Standard Card:**
```css
.card {
  background: #FFFFFF;
  border-radius: 12px;        /* rounded-xl */
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
  border: 1px solid #E2E8F0;
}
.card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.12);
}
```

**Card Header:** 16px vertical / 20px horizontal padding, bottom border `#E2E8F0`. Title 14px / 600 weight / navy.

**Card Body:** 20px padding (or 16px horizontal / 16px bottom in framework cards).

### 8.4 KPI / Stat Cards

**Layout:** White card with 20px padding. Header row with icon box + trend badge (flex, space-between).

**Icon Box:** 40×40px, 10px border-radius. Background and text use semantic tinted pairs (see Section 2.6). Icon centered at 18px.

**Trend Badge:** 11px text, 3px vertical / 8px horizontal padding, 20px border-radius, 600 weight. Up = green tint + green text. Down = red tint + red text.

**Value:** 28px / 700 weight / navy, below icon row with 12px top margin.

**Label:** 12px / `#718096`, 4px top margin.

**React StatCard variant:** `p-5`, icon is `w-10 h-10`, value is `text-2xl font-bold`, label is `text-sm font-medium text-gray-500`. Optional trend with green/red color and "vs last quarter" suffix.

### 8.5 Buttons

**Primary Button:**
```css
.btn-primary {
  background: #1A365D;       /* var(--color-primary) */
  color: white;
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 12–13px;
  font-weight: 600;
  border: none;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  transition: all 150ms ease-in-out;
}
.btn-primary:hover { background: #2A4A7F; }
.btn-primary:active { background: #0F2340; }
.btn-primary:focus { ring: 2px #6366F1 offset-2; }
.btn-primary:disabled { opacity: 0.25; }
```

**Secondary / Outline Button:**
```css
.btn-outline {
  background: white;
  color: #1A365D;
  border: 1px solid #E2E8F0;
  /* all other properties same as primary */
}
.btn-outline:hover { background: #F7FAFC; }
```

**Danger Button:**
```css
.btn-danger {
  background: #C53030;       /* var(--color-error) */
  color: white;
}
.btn-danger:hover { background: #E53E3E; }
.btn-danger:active { background: #9B2C2C; }
```

**Success Button:**
```css
.btn-success {
  background: #2D7D46;       /* var(--color-secondary) */
  color: white;
}
```

**Warning Button:**
```css
.btn-warning {
  background: #DD6B20;       /* var(--color-warning) */
  color: white;
}
```

**AI Generate Button:** Same as primary but uses `#2D7D46` background when in "generating" state.

### 8.6 Status Badges

All badges: `display: inline-block`, `padding: 3px 10px`, `border-radius: 20px`, `font-size: 10–11px`, `font-weight: 600`.

| Status | Background | Text Color | Class |
|---|---|---|---|
| Active / In Progress | `#F0FFF4` | `#2D7D46` | `.status-badge.active` |
| Planning / Pending | `#EBF4FF` | `#1A365D` | `.status-badge.planning` |
| Fieldwork | `#FFFAF0` | `#DD6B20` | `.status-badge.fieldwork` |
| Reporting | `#FAF5FF` | `#6B46C1` | `.status-badge.reporting` |
| Completed | purple-100 | purple-700 | `.badge-status-completed` |
| Draft | gray-100 | gray-700 | `.badge-status-draft` |
| Overdue | red-100 | red-700 | `.badge-status-overdue` |
| Closed | gray-100 | gray-700 | `.badge-status-closed` |

### 8.7 Risk Rating Badges

| Rating | Background | Text | Dot Color |
|---|---|---|---|
| Critical | `#FFF5F5` | `#C53030` | `#C53030` (8×8 dot) |
| High | `#FFFAF0` | `#DD6B20` | `#DD6B20` |
| Medium | `#FFFFF0` | `#B7791F` | `#D4AF37` |
| Low | `#F0FFF4` | `#2D7D46` | `#2D7D46` |

**Risk Dot:** 8×8px circle, inline-block, 6px right margin, used in table cells before risk label text.

**Rating Badge (React):** `inline-flex px-2.5 py-0.5 rounded-full text-xs font-semibold` with a 6×6 colored dot (`w-1.5 h-1.5 rounded-full`) preceding the label.

### 8.8 Compliance Badges

| State | Background | Text | Icon |
|---|---|---|---|
| Compliant | `#F0FFF4` | `#2D7D46` | `fa-check` |
| Partial | `#FFFAF0` | `#DD6B20` | `fa-minus` |
| Non-Compliant | `#FFF5F5` | `#C53030` | `fa-times` |
| Not Assessed | `#F7FAFC` | `#718096` | `fa-circle` |

Style: `inline-flex items-center gap-4px`, `padding: 3px 10px`, `border-radius: 20px`, `font-size: 10px`, `font-weight: 600`.

### 8.9 Risk Classification Chips

Used in AI Risk Classification for categorizing risk types:

| Type | Background | Text | Icon |
|---|---|---|---|
| Technology | `#EBF4FF` | `#1A365D` | `fa-microchip` |
| Operational | `#FFFAF0` | `#DD6B20` | `fa-cog` |
| Compliance | `#FAF5FF` | `#6B46C1` | `fa-gavel` |
| Financial | `#FFF5F5` | `#C53030` | — |

Style: `padding: 6px 12px`, `border-radius: 20px`, `font-size: 11px`, `font-weight: 600`, `inline-flex items-center gap-4px`.

### 8.10 Data Tables

**Table Container:** White card wrapper with card styles (12px radius, shadow, border).

**Table Header Row:** Background `#F7FAFC`, padding `10px 14–20px`, text `10–11px`, weight 600, color `#718096`, uppercase, letter-spacing `0.5px`.

**Table Body Cell:** Padding `10–12px 14–20px`, font-size `12–13px`, top border `#F0F0F0`.

**Row Hover (React):** `hover:bg-gray-50 transition-colors`.

**Table Card Header:** 16px vertical / 20px horizontal padding, flex with space-between. Title on left (14px, 600 weight, navy), action link on right (12px, `#2D5F9A`, 500 weight).

**Sortable Columns (React DataTable):** Click headers to toggle sort. Active sort indicated by directional arrow SVG icon.

### 8.11 Progress Bars

**Standard Progress Bar:**
```css
.progress-bar {
  width: 80px;          /* or 100% in framework cards */
  height: 6px;
  background: #E2E8F0;
  border-radius: 3px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  border-radius: 3px;
  background: /* contextual color */;
  transition: width 500ms;
}
```

**Progress Label (Framework cards):** Flex row with space-between above the bar, 11px text. Label on left, percentage on right in contextual color with 600 weight.

**Progress Ring (React):** Circular SVG, default 80px size / 6px stroke, green `#2D7D46`. Animated with `transition-all duration-1000`. Center percentage text in bold.

### 8.12 Framework Cards

**Structure:** Card with hover lift effect (`translateY(-2px)`, elevated shadow). Contains:

1. **Header:** 16px padding, flex row with 12px gap. Icon box (44×44px, 10px radius, tinted background) + title stack (14px bold navy title, 11px gray subtitle).
2. **Stats Row:** 3 equal columns, `#F7FAFC` backgrounds, 8px padding, 8px radius. Value 16px / 700 / navy, label 9px uppercase gray.
3. **Progress Bar:** Full width, 6px height.
4. **Tags:** Flex wrap with 6px gap. Tags are 9px, 2px vertical / 8px horizontal padding, 10px radius, 600 weight. `.active` = green tint. `.count` = blue tint.

### 8.13 Donut Chart

**Container:** 140×140px circle. Background uses `conic-gradient` with four segments (Critical red, High orange, Medium gold, Low green).

**Center Hole:** 80×80px white circle centered via absolute positioning + flex. Contains total number (22px, bold, navy) and "Total" label (10px, gray).

**Legend:** Flex row, wrap, 12px gap, center-justified. Each item has an 8×8px color dot + 11px text (`#4A5568`).

### 8.14 Heat Map

**Grid:** CSS grid, `40px` first column (Y-axis label) + 5 equal columns, 5 data rows + 1 label row, 3px gap, 280px height.

**Cells:** 6px border-radius, centered content, white text (except yellow which uses `#744210`). Count shown in semi-transparent badge (`rgba(255,255,255,0.3)` background, 10px text, 10px radius).

**Axis Labels:** 10px text, `#718096`, 500–600 weight. Y-axis uses `writing-mode: vertical-rl; transform: rotate(180deg)`.

### 8.15 Forms

**Text Input:**
```css
.form-input {
  width: 100%;
  border-radius: 8px;       /* rounded-lg */
  border: 1px solid #D1D5DB; /* border-gray-300 */
  box-shadow: 0 1px 2px rgba(0,0,0,0.05); /* shadow-sm */
  padding: 10px 12px;       /* py-2.5 px-3 */
  font-size: 14px;
}
.form-input:focus {
  border-color: #1A365D;    /* var(--color-primary) */
  ring: 2px #1A365D;
  outline: none;
}
```

**Input Label:** `block text-sm font-medium text-gray-700 mb-1`

**Input Error:** `text-sm text-red-600`, conditionally rendered below the input.

**Checkbox:** `rounded border-gray-300 text-indigo-600 shadow-sm focus:ring-indigo-500`

**Select:** Same as text input with `appearance-none`.

### 8.16 Filter Bar

```css
.filter-bar {
  background: rgba(249, 250, 251, 0.8); /* bg-gray-50/80 */
  border-radius: 12px;                   /* rounded-xl */
  border: 1px solid #E5E7EB;            /* border-gray-200 */
  padding: 16px 20px;                    /* px-5 py-4 */
  margin-bottom: 24px;                   /* mb-6 */
}
```

**Filter inputs:** White background, no border, `ring-1 ring-gray-200`, `focus:ring-2 focus:ring-primary`.

**Reset Button:** White background, `ring-1 ring-gray-200`, 600 weight, `hover:bg-gray-100`.

### 8.17 Modals & Dialogs

**Overlay:** Fixed inset-0, z-50, `bg-gray-500/75` backdrop.

**Dialog Panel:** Centered, white background, `rounded-lg`, max-width variants (sm: 448px, md: 672px, lg: 896px, xl: 1120px, 2xl: 1344px).

**Transitions:** Enter `ease-out 300ms` (opacity 0→100, scale 95→100). Leave `ease-in 200ms` (reverse).

**Confirm Dialog Variants:** Each has colored icon background — danger (red), warning (amber), primary (blue), success (green). Full-width confirm/cancel button layout with processing spinner state.

### 8.18 Notifications / Toasts

**Position:** Fixed, `top: 80px`, `right: 16px`, z-50.

**Stack:** Flex column, 12px gap between toasts.

**Types & Colors:**

| Type | Icon BG | Border Accent |
|---|---|---|
| Success | Green | Green |
| Error | Red | Red |
| Warning | Amber | Amber |
| Info | Blue | Blue |

**Auto-dismiss:** 5000ms with animated progress bar (`h-1 w-full`) that fills in the type color.

**Animation:** Slide-in from right + opacity fade.

### 8.19 Dropdown Menus

**Trigger:** Toggles open state on click.

**Panel:** Absolute positioned, white background, rounded-md, shadow-lg, ring-1 ring-black/5. Alignment: left or right.

**Items:** `w-full px-4 py-2 text-sm leading-5`, `hover:bg-gray-100`.

**Transition:** Enter `ease-out 200ms`, leave `ease-in 75ms`, scale 95→100.

### 8.20 Page Header / Breadcrumbs

**Layout:** Flex row (responsive column on mobile), space-between, items-center, 24px bottom margin.

**Breadcrumbs:** Flex row, 6px gap, 14px text, `text-gray-500`. Separator is a small right-arrow SVG (14×14). Current page item is `text-gray-700 font-medium`.

**Title:** `text-2xl font-bold` in `--color-text-primary`.

**Subtitle:** `text-sm text-gray-500 mt-1`.

### 8.21 Pagination

**Container:** Flex row, space-between, `px-4 py-3`, top border `border-gray-100`.

**Page Links:** `px-3 py-1.5 text-sm rounded-md transition-colors`.

- Active: `bg-primary text-white font-semibold`
- Inactive: `text-gray-600 hover:bg-gray-100`
- Disabled: `text-gray-300 cursor-not-allowed`

### 8.22 Empty State

**Container:** Centered text, `py-12 px-6`.

**Icon:** 64×64px (`w-16 h-16`) circle, `bg-gray-100`, centered inline-flex, 16px bottom margin.

**Title:** `text-sm font-semibold text-gray-800 mb-1`.

**Description:** `text-sm text-gray-500 mb-4 max-w-sm mx-auto`.

**Action:** Primary button style with icon and `gap-2`.

### 8.23 AI Feature Cards

**Header:** Full-width gradient background (`135deg, #1A365D → #2D5F9A`), white text, 16px vertical / 20px horizontal padding. Contains icon (20px, gold `#D4AF37`), title (14px, 600 weight, white), and AI badge (auto margin-left, `rgba(212,175,55,0.2)` background, gold text, 10px, 12px radius).

**Body:** 20px padding.

**AI Status Indicator:** Green-tinted row (`#F0FFF4` background, 8px radius, 10px vertical / 16px horizontal padding). Contains pulsing green dot (8×8, `animation: pulse 2s infinite`), status text (11px, green, 500 weight), model name right-aligned (10px, gray).

**Report Preview Box:** `#F7FAFC` background, 1px border `#E2E8F0`, 8px radius, 14px padding. Section tag in `#EBF4FF` / `#1A365D`, 10px, 4px radius.

---

## 9. Animation & Transitions

### 9.1 Standard Transitions

| Context | Duration | Easing | Properties |
|---|---|---|---|
| Buttons | 150ms | ease-in-out | all |
| Nav items / Links | 200ms | ease | all / colors |
| Cards (hover) | 200ms | ease | transform, box-shadow |
| Modal enter | 300ms | ease-out | opacity, transform |
| Modal leave | 200ms | ease-in | opacity, transform |
| Dropdown enter | 200ms | ease-out | opacity, transform |
| Dropdown leave | 75ms | ease-in | opacity, transform |
| Progress bars | 500ms | ease | width |
| Progress rings | 1000ms | ease | all |

### 9.2 Keyframe Animations

**Pulse (AI status dot):**
```css
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
```

**Bell Ring (notification):**
```css
@keyframes bellRing {
  0% { transform: rotate(0deg); }
  10% { transform: rotate(14deg); }
  20% { transform: rotate(-14deg); }
  30% { transform: rotate(10deg); }
  40% { transform: rotate(-6deg); }
  50% { transform: rotate(0deg); }
  100% { transform: rotate(0deg); }
}
```

**Slide In (toasts):**
```css
@keyframes slideIn {
  from { opacity: 0; transform: translateX(100%); }
  to { opacity: 1; transform: translateX(0); }
}
```

**Spin (loading):** Standard CSS `animation: spin 1s linear infinite`.

---

## 10. Z-Index Scale

| Layer | z-index | Usage |
|---|---|---|
| Base content | 0 | Normal document flow |
| Sticky headers | 10 | Table sticky headers |
| Dropdowns | 50 | Dropdown menus |
| Modals | 50 | Modal overlay + panel |
| Offline button | 90 | Bottom-right status |
| Offline details | 90 | Sync details panel |
| Offline banner | 100 | Top banner |
| Toasts | 110 | Flash notifications |

---

## 11. Responsive Behavior

### 11.1 Breakpoints

Follow Tailwind CSS default breakpoints:

| Name | Width | Usage |
|---|---|---|
| sm | 640px | Mobile landscape |
| md | 768px | Tablet |
| lg | 1024px | Desktop (sidebar collapse point) |
| xl | 1280px | Wide desktop |
| 2xl | 1536px | Ultra-wide |

### 11.2 Sidebar Behavior

Below `lg` breakpoint, the sidebar collapses to 72px width showing only icons (no labels). On mobile, it becomes an overlay triggered by a hamburger menu.

### 11.3 Grid Collapse

KPI grids collapse from 4/5 columns to 2 columns at `md` and single column at `sm`. Chart grids stack vertically below `lg`.

---

## 12. Accessibility

### 12.1 Color Contrast

All text/background combinations must meet WCAG 2.1 AA contrast ratios (4.5:1 for normal text, 3:1 for large text). The primary palette has been selected to achieve this:

- Navy `#1A365D` on white: 10.2:1
- Body text `#2D3748` on `#F7FAFC`: 9.4:1
- Secondary text `#718096` on white: 4.6:1
- White on navy `#1A365D`: 10.2:1

### 12.2 Focus States

All interactive elements must have visible focus rings: `ring-2 ring-offset-2 ring-indigo-500` (or `ring-primary` equivalent). Never remove outlines without providing an alternative focus indicator.

### 12.3 Icon Accessibility

Decorative icons should have `aria-hidden="true"`. Functional icons (buttons with only an icon) must have `aria-label` or a visually hidden text label.

---

## 13. File & Asset References

| Asset | Path |
|---|---|
| Tailwind Config | `/tailwind.config.js` |
| Global CSS | `/resources/css/app.css` |
| SVG Icon Library | `/resources/js/utils/icons.jsx` |
| React Components | `/resources/js/Components/` |
| Page Components | `/resources/js/Pages/` |
| Layouts | `/resources/js/Layouts/` |
| Font Awesome CDN | `https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css` |
| Google Fonts (Inter) | `https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap` |

---

## 14. Design Principles

1. **Consistency over creativity.** Every new screen should feel like it belongs to the same product. Reuse existing components before creating new ones.

2. **Information density with clarity.** Audit professionals need dense data views. Use compact spacing but maintain clear visual hierarchy through color, weight, and whitespace.

3. **Semantic color always.** Never use red for non-error purposes, green for non-success, or orange for non-warning. Colors carry meaning in a GRC context.

4. **Navy is authority.** The primary navy blue conveys institutional trust and professionalism. It anchors every screen via the sidebar, headings, and primary actions.

5. **Gold is distinction.** The gold accent is reserved for active states, navigation indicators, and premium/AI features. Do not overuse it.

6. **Progressive disclosure.** Show summary data first (KPIs, badges, progress bars), let users drill into detail (tables, modals, full reports).

7. **Accessible by default.** Every component must meet WCAG AA. Every interactive element must be keyboard-navigable with visible focus states.

---

*This design system should be treated as a living document. Update it whenever new patterns are introduced to the AuditPro GRC platform.*
