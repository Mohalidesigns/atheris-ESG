/**
 * D47: design tokens shared with Thirdline (Atheris internal audit), measured from its live app on 18 Oct 2026 — see
 * docs/esg-completion/UI_THIRDLINE_TOKENS.md for where each value comes from. Thirdline is Tailwind-based, so the
 * neutrals are Tailwind's gray scale; brand colours are its CSS variables (--color-*). Change them here, not in pages.
 */

export const brand = {
  primary: '#1A365D', // --color-primary (sidebar, primary buttons, avatar)
  primaryLight: '#2A4A7F', // --color-primary-light (primary button hover)
  primaryDark: '#0F2340', // --color-primary-dark
  accent: '#D4AF37', // --color-accent (gold: active nav bar, highlights, chart line)
  accentLight: '#E2C458', // --color-accent-light
  secondary: '#2D7D46', // --color-secondary / --color-success
  secondaryLight: '#38A169', // --color-secondary-light
  error: '#C53030', // --color-error
  warning: '#DD6B20', // --color-warning
  info: '#319795', // --color-info
  textPrimary: '#2D3748', // --color-text-primary
  textSecondary: '#718096', // --color-text-secondary (Thirdline's own; body copy uses gray-500 below for WCAG AA)
  background: '#F7FAFC', // --color-bg
  card: '#FFFFFF', // --color-card
};

/** Tailwind gray (Thirdline's neutrals: borders, table heads, secondary text). */
export const gray = {
  50: '#F9FAFB',
  100: '#F3F4F6',
  200: '#E5E7EB',
  300: '#D1D5DB',
  400: '#9CA3AF',
  500: '#6B7280',
  600: '#4B5563',
  700: '#374151',
  800: '#1F2937',
  900: '#111827',
};

/** Soft status colours of Thirdline's .badge-* classes: [background, text]. */
export const badge = {
  neutral: ['#F3F4F6', '#374151'], // badge-status-draft
  info: ['#DBEAFE', '#1D4ED8'], // badge-status-pending
  success: ['#DCFCE7', '#15803D'], // badge-status-active
  completed: ['#F3E8FF', '#7E22CE'], // badge-status-completed
  error: ['#FEE2E2', '#991B1B'], // badge-critical
  warning: ['#FFEDD5', '#9A3412'], // badge-high
  caution: ['#FEF9C3', '#854D0E'], // badge-medium
  teal: ['#CCFBF1', '#115E59'],
};

/** Tailwind shadow scale (Thirdline cards use sm, hover md, menus lg, modals 2xl). */
export const shadow = {
  sm: '0 1px 2px 0 rgba(0,0,0,0.05)',
  base: '0 1px 3px 0 rgba(0,0,0,0.1), 0 1px 2px -1px rgba(0,0,0,0.1)',
  md: '0 4px 6px -1px rgba(0,0,0,0.1), 0 2px 4px -2px rgba(0,0,0,0.1)',
  lg: '0 10px 15px -3px rgba(0,0,0,0.1), 0 4px 6px -4px rgba(0,0,0,0.1)',
  xl: '0 20px 25px -5px rgba(0,0,0,0.1), 0 8px 10px -6px rgba(0,0,0,0.1)',
  xxl: '0 25px 50px -12px rgba(0,0,0,0.25)',
};

export const radius = { sm: 6, md: 8, lg: 12, xl: 16, pill: 9999 };

export const font = {
  sans: '"Figtree", ui-sans-serif, system-ui, sans-serif, "Apple Color Emoji", "Segoe UI Emoji"',
  mono: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace',
};

export const layout = {
  sidebarWidth: 260, // --sidebar-width
  topBarHeight: 64, // h-16
};

/** Charts: Thirdline's SVG charts (grid #E2E8F0, axis text #94A3B8 10 px) and its series order. */
export const chart = {
  grid: '#E2E8F0',
  axis: '#94A3B8',
  axisFontSize: 11,
  series: [brand.primary, brand.secondary, brand.accent, brand.info, brand.warning, brand.error, '#7E22CE', gray[500]],
  // Severity colours of Thirdline's "Findings by rating" donut (Tailwind red/orange/amber/green 600).
  severity: { critical: '#DC2626', high: '#EA580C', medium: '#D97706', low: '#16A34A' },
};
