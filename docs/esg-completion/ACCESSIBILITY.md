# Accessibility and slow networks (WP-53)

Design: D41. Checks: `esg-frontend/cypress/e2e/wp-53-accessibility.cy.js` (axe-core, `cy.a11y`), `scripts/check-bundle-size.mjs` (CI).

## WCAG 2.1 AA — automated

axe-core with every A and AA rule of WCAG 2.0 and 2.1, run after each page settles: 15 module pages (each as a role
that uses it), 5 tabs (training catalogue, retirements, AI forecasts, privacy, users), the public pages (sign-in,
whistleblowing, certificate check) and two dialogs (data point, course). **22 checks, 0 violations.** Lighthouse
accessibility score on the sign-in page: 100.

Found and fixed (first run: 17 of 17 failing):

| Issue | Where | Fix |
|---|---|---|
| Secondary text 4.0:1 | everywhere (theme `text.secondary`, captions, sign-in) | #5F6B7A (5.4:1) |
| Sidebar section captions 3.3:1, tagline 4.3:1 | app shell | 65 % white on navy (6.1:1) |
| Avatar initials 2.0:1 (white on gold) | sidebar | navy initials (5.8:1) |
| Outlined warning/info text 1.9–3.5:1 | Sandbox labels, chips, text buttons | dark amber #7A5B00, dark teal #237574 |
| "Active" status 4.47:1 | Administration › Users | darker green |
| Progress bars without a name | dashboard SDG, compliance, targets, budgets, bonds, imports, training, AI | aria-labels |
| No page heading | Administration | `h1` |

Also found: a duplicate `MuiChip` key in the theme silently discarded one of the two definitions.

## Not automated (OPEN, WP-54 UAT)

Keyboard-only and screen-reader (NVDA, VoiceOver, TalkBack) walkthroughs per persona; zoom to 200 % and reflow at
320 px; charts' text alternatives beyond their titles and tables.

## Slow networks (BRD §7.1: page load < 3 s on 3G)

Sign-in page (the first page anyone loads), production build, Lighthouse 13.5:

| Profile | First contentful paint | Largest contentful paint | Bytes |
|---|---|---|---|
| Lighthouse mobile (1.6 Mbps, 150 ms RTT, 4× CPU — the former "fast 3G") | 1.8 s (was 2.3 s) | 3.0 s (was 3.1 s) | 285 KiB (was 312) |
| Regular 3G (700 kbps, 300 ms RTT, 4× CPU) | 3.9 s (was 4.8 s) | 6.3 s (was 6.6 s) | 285 KiB |

Done: the app shell and zod moved out of the entry bundle (140 → 86 KB gz), fonts self-hosted, CI budget (entry
≤ 95 KB gz, sign-in JavaScript ≤ 190 KB gz). The target holds on fast 3G; on regular 3G the first visit needs about
285 KiB, which can't arrive in 3 s at 700 kbps. Repeat visits load from the device since WP-43 (the service worker
precaches the app after the first visit, D43). Next (OPEN): Brotli at the edge (WP-54), and an HTML sign-in shell that
paints before JavaScript.
