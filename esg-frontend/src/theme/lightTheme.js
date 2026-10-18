import { createTheme } from '@mui/material/styles';
import { badge, brand, chart, font, gray, layout, radius, shadow } from './tokens';

// D47: the theme carries Thirdline's design system (tokens.js); pages should not restate these values.

// MUI's 25 elevations mapped onto Tailwind's six shadows (cards 1, menus 8, drawers 16, dialogs 24).
const shadows = [
  'none', shadow.sm, shadow.base, shadow.md, shadow.md, shadow.md, shadow.lg, shadow.lg, shadow.lg, shadow.lg, shadow.lg,
  shadow.lg, shadow.xl, shadow.xl, shadow.xl, shadow.xl, shadow.xl, shadow.xl, shadow.xl, shadow.xl, shadow.xxl, shadow.xxl,
  shadow.xxl, shadow.xxl, shadow.xxl,
];

// Filled chips are Thirdline's soft badges (pale background, dark text) rather than MUI's solid ones.
const softChip = (key) => ({ backgroundColor: badge[key][0], color: badge[key][1] });

const lightTheme = createTheme({
  palette: {
    primary: { main: brand.primary, light: brand.primaryLight, dark: brand.primaryDark, contrastText: '#FFFFFF' },
    secondary: { main: brand.secondary, light: brand.secondaryLight, dark: '#1E5C32', contrastText: '#FFFFFF' },
    success: { main: brand.secondary, light: brand.secondaryLight, dark: '#1E5C32', contrastText: '#FFFFFF' },
    warning: { main: brand.warning, light: '#ED8936', dark: '#C05621', contrastText: '#FFFFFF' },
    error: { main: brand.error, light: '#E53E3E', dark: '#9B2C2C', contrastText: '#FFFFFF' },
    info: { main: brand.info, light: '#38B2AC', dark: '#237574', contrastText: '#FFFFFF' },
    accent: { main: brand.accent, light: brand.accentLight, dark: '#B8961E', contrastText: brand.primary },
    orange: { main: brand.warning },
    grey: gray,
    background: { default: brand.background, paper: brand.card },
    text: {
      primary: brand.textPrimary,
      // WP-53: gray-500 is 4.8:1 on white (WCAG AA needs 4.5:1); Thirdline's #718096 is 4.0:1, so it is not used for text.
      secondary: gray[500],
    },
    divider: gray[200],
    action: { hover: gray[50] },
  },
  typography: {
    fontFamily: font.sans,
    fontSize: 14,
    // Tailwind text-3xl / 2xl / xl / lg / base / sm as Thirdline uses them: page title 24/700, card title 14/600.
    h1: { fontWeight: 700, fontSize: '1.875rem', lineHeight: '2.25rem', color: brand.textPrimary },
    h2: { fontWeight: 700, fontSize: '1.5rem', lineHeight: '2rem', color: brand.textPrimary },
    h3: { fontWeight: 600, fontSize: '1.25rem', lineHeight: '1.75rem', color: brand.textPrimary },
    h4: { fontWeight: 700, fontSize: '1.5rem', lineHeight: '2rem', color: brand.textPrimary },
    h5: { fontWeight: 600, fontSize: '1.125rem', lineHeight: '1.75rem', color: brand.textPrimary },
    h6: { fontWeight: 600, fontSize: '0.875rem', lineHeight: '1.25rem', color: gray[800] },
    subtitle1: { fontWeight: 500, fontSize: '0.875rem', lineHeight: '1.25rem' },
    subtitle2: { fontWeight: 600, fontSize: '0.875rem', lineHeight: '1.25rem' },
    body1: { fontSize: '0.875rem', lineHeight: '1.25rem' },
    body2: { fontSize: '0.875rem', lineHeight: '1.25rem' },
    caption: { fontSize: '0.75rem', lineHeight: '1rem', color: gray[500] },
    overline: { fontSize: '0.75rem', lineHeight: '1rem', fontWeight: 600, letterSpacing: '0.05em', textTransform: 'uppercase' },
    button: { fontSize: '0.875rem', lineHeight: '1.25rem', fontWeight: 600, textTransform: 'none' },
    mono: { fontFamily: font.mono },
  },
  shape: { borderRadius: radius.md },
  shadows,
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: { fontFamily: font.sans, backgroundColor: brand.background, color: brand.textPrimary },
        // Plain links (record titles in tables) as Thirdline's: primary navy, underlined on hover only.
        a: { color: brand.primary, textDecoration: 'none' },
        'a:not(.MuiButtonBase-root):hover': { textDecoration: 'underline' },
        // Recharts, styled once for every chart: Thirdline's light grid and small slate axis labels.
        '.recharts-cartesian-grid line': { stroke: chart.grid, strokeDasharray: 0 },
        '.recharts-cartesian-axis-line, .recharts-cartesian-axis-tick-line': { stroke: chart.grid },
        '.recharts-cartesian-axis-tick-value, .recharts-polar-angle-axis-tick-value, .recharts-polar-radius-axis-tick-value': {
          fill: chart.axis, fontSize: chart.axisFontSize, fontFamily: font.sans,
        },
        '.recharts-label': { fill: gray[500], fontSize: chart.axisFontSize },
        '.recharts-legend-item-text': { color: `${gray[600]} !important`, fontSize: 12 },
        '.recharts-default-tooltip': {
          borderRadius: `${radius.md}px`, border: `1px solid ${gray[200]} !important`, boxShadow: shadow.lg, fontSize: 12,
          padding: '8px 12px !important',
        },
        '.recharts-tooltip-label': { color: gray[800], fontWeight: 600, marginBottom: '4px !important' },
      },
    },
    MuiButton: {
      defaultProps: { disableElevation: true },
      variants: [
        // WP-53: text in the warning orange or info teal fails WCAG AA on white; darker shades are ≥ 5.4:1.
        { props: { variant: 'text', color: 'warning' }, style: { color: badge.warning[1] } },
        { props: { variant: 'outlined', color: 'warning' }, style: { color: badge.warning[1] } },
        { props: { variant: 'text', color: 'info' }, style: { color: '#237574' } },
      ],
      styleOverrides: {
        // .btn-primary / .btn-secondary: 8 px radius, 8 × 16 padding, 14/600.
        root: { borderRadius: radius.md, padding: '8px 16px', fontWeight: 600, textTransform: 'none', lineHeight: '1.25rem' },
        sizeSmall: { padding: '6px 12px', fontSize: '0.8125rem' },
        sizeLarge: { padding: '10px 20px', fontSize: '0.9375rem' },
        containedPrimary: { '&:hover': { backgroundColor: brand.primaryLight } },
        containedSecondary: { '&:hover': { backgroundColor: brand.secondaryLight } },
        containedSuccess: { '&:hover': { backgroundColor: brand.secondaryLight } },
        outlined: {
          padding: '7px 15px', backgroundColor: '#FFFFFF', borderColor: gray[300],
          '&:hover': { backgroundColor: gray[50], borderColor: gray[300] },
        },
        outlinedSizeSmall: { padding: '5px 11px' },
        textSizeSmall: { padding: '4px 8px' },
      },
    },
    MuiIconButton: {
      styleOverrides: { root: { borderRadius: radius.md }, sizeSmall: { padding: 6 } },
    },
    MuiToggleButtonGroup: {
      styleOverrides: { root: { backgroundColor: '#FFFFFF' } },
    },
    MuiToggleButton: {
      styleOverrides: {
        root: {
          textTransform: 'none', fontWeight: 500, fontSize: '0.8125rem', color: gray[600], borderColor: gray[300], padding: '5px 12px',
          '&.Mui-selected': { backgroundColor: 'rgba(26,54,93,0.08)', color: brand.primary },
          '&.Mui-selected:hover': { backgroundColor: 'rgba(26,54,93,0.12)' },
        },
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: { backgroundImage: 'none' },
        outlined: { borderColor: gray[200] },
        rounded: { borderRadius: radius.lg },
      },
    },
    // .card / .stat-card: 12 px radius, gray-100 border, shadow-sm; hover lifts to shadow-md on clickable cards.
    MuiCard: {
      styleOverrides: {
        root: {
          borderRadius: radius.lg, border: `1px solid ${gray[100]}`, boxShadow: shadow.sm,
          '&:has(> .MuiCardActionArea-root:hover)': { boxShadow: shadow.md },
        },
      },
    },
    MuiCardContent: {
      styleOverrides: { root: { padding: 20, '&:last-child': { paddingBottom: 20 } } },
    },
    // .card-header: 16 × 24 padding under a gray-100 rule, 14/600 title.
    MuiCardHeader: {
      styleOverrides: {
        root: { padding: '16px 24px', borderBottom: `1px solid ${gray[100]}` },
        title: { fontSize: '0.875rem', fontWeight: 600, color: gray[800] },
        subheader: { fontSize: '0.75rem', color: gray[500] },
      },
    },
    MuiCardActionArea: {
      styleOverrides: { root: { borderRadius: radius.lg } },
    },
    MuiAccordion: {
      styleOverrides: {
        root: {
          border: `1px solid ${gray[100]}`, boxShadow: shadow.sm, '&:before': { display: 'none' },
          '&:first-of-type': { borderTopLeftRadius: radius.lg, borderTopRightRadius: radius.lg },
          '&:last-of-type': { borderBottomLeftRadius: radius.lg, borderBottomRightRadius: radius.lg },
        },
      },
    },
    MuiAppBar: {
      defaultProps: { elevation: 0, color: 'inherit' },
      styleOverrides: { root: { backgroundColor: '#FFFFFF', borderBottom: `1px solid ${gray[200]}` } },
    },
    MuiDrawer: {
      styleOverrides: { paper: { borderRight: 'none' } },
    },
    // Phase 2 (item 8): tables scroll sideways inside their card rather than being cut off at 1024–1280 px, cells are a
    // little tighter on narrower screens, and a table marked data-sticky-actions keeps its last (Actions) column pinned.
    MuiTableContainer: {
      styleOverrides: { root: { overflowX: 'auto', maxWidth: '100%' } },
    },
    MuiTable: {
      styleOverrides: {
        root: {
          '&[data-sticky-actions] tr > *:last-child': {
            position: 'sticky', right: 0, zIndex: 1, background: '#FFFFFF', boxShadow: '-6px 0 6px -6px rgba(0,0,0,0.15)',
          },
          '&[data-sticky-actions] thead tr > *:last-child': { background: gray[50], zIndex: 2 },
        },
      },
    },
    // Thirdline tables: 12 × 16 cells, gray-100 row rules; head on gray-50 in 12/600 uppercase gray-500.
    MuiTableCell: {
      styleOverrides: {
        root: {
          padding: '12px 16px', fontSize: '0.875rem', lineHeight: '1.25rem', borderBottom: `1px solid ${gray[100]}`,
          color: brand.textPrimary,
          '@media (max-width: 1280px)': { paddingLeft: 8, paddingRight: 8 },
        },
        sizeSmall: { padding: '10px 16px' },
        head: {
          backgroundColor: gray[50], color: gray[500], fontSize: '0.75rem', lineHeight: '1rem', fontWeight: 600,
          letterSpacing: '0.05em', textTransform: 'uppercase', borderBottom: `1px solid ${gray[200]}`,
        },
      },
    },
    MuiTableRow: {
      styleOverrides: { root: { '&.MuiTableRow-hover:hover': { backgroundColor: gray[50] } } },
    },
    MuiTableSortLabel: {
      styleOverrides: { root: { '&.Mui-active': { color: gray[700] } } },
    },
    MuiTablePagination: {
      styleOverrides: { root: { color: gray[600] }, selectLabel: { fontSize: '0.8125rem' }, displayedRows: { fontSize: '0.8125rem' } },
    },
    // Thirdline dialogs: 16 px radius, shadow-2xl over a blurred slate-900/70 backdrop.
    MuiDialog: {
      styleOverrides: {
        root: { '& .MuiBackdrop-root': { backgroundColor: 'rgba(15,23,42,0.7)', backdropFilter: 'blur(4px)' } },
        paper: {
          borderRadius: radius.xl, boxShadow: shadow.xxl,
          // Phase 2 (item 9): below 600 px every dialog fills the screen.
          '@media (max-width: 599.95px)': { margin: 0, width: '100%', maxWidth: '100%', height: '100%', maxHeight: '100%', borderRadius: 0 },
        },
      },
    },
    MuiDialogTitle: {
      styleOverrides: {
        root: { padding: '16px 24px', fontSize: '1rem', lineHeight: '1.5rem', fontWeight: 600, color: gray[800], borderBottom: `1px solid ${gray[100]}` },
      },
    },
    MuiDialogContent: {
      styleOverrides: { root: { padding: '20px 24px', '.MuiDialogTitle-root + &': { paddingTop: 20 } } },
    },
    MuiDialogActions: {
      styleOverrides: { root: { padding: '16px 24px', gap: 8, borderTop: `1px solid ${gray[100]}`, '& > :not(style) ~ :not(style)': { marginLeft: 0 } } },
    },
    // Form fields (.form-input / .form-label): label above the field in 14/500 gray-700, 38 px field, gray-300 border,
    // 8 px radius, shadow-sm; focus turns the border primary.
    MuiInputLabel: {
      defaultProps: { shrink: true },
      styleOverrides: {
        root: { fontSize: '0.875rem', fontWeight: 500, color: gray[700], '&.Mui-focused': { color: gray[700] } },
        outlined: {
          position: 'relative', transform: 'none', marginBottom: 4, lineHeight: '1.25rem', maxWidth: '100%',
          '&.MuiInputLabel-shrink': { transform: 'none' },
        },
      },
    },
    MuiFormLabel: {
      styleOverrides: {
        root: { fontSize: '0.875rem', fontWeight: 500, color: gray[700], '&.Mui-focused': { color: gray[700] } },
        asterisk: { color: '#EF4444' },
      },
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          borderRadius: radius.md, backgroundColor: '#FFFFFF', fontSize: '0.875rem', boxShadow: shadow.sm,
          '& .MuiOutlinedInput-notchedOutline': { borderColor: gray[300], top: 0 },
          '& .MuiOutlinedInput-notchedOutline legend': { display: 'none' },
          '&:hover:not(.Mui-disabled):not(.Mui-error) .MuiOutlinedInput-notchedOutline': { borderColor: gray[400] },
          '&.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: brand.primary, borderWidth: 2 },
          '&.Mui-disabled': { backgroundColor: gray[50] },
        },
        input: { padding: '9px 12px', height: '1.25rem', lineHeight: '1.25rem' },
        inputSizeSmall: { padding: '9px 12px' },
        multiline: { padding: '9px 12px' },
        inputMultiline: { padding: 0, height: 'auto' },
        adornedStart: { paddingLeft: 12 },
        adornedEnd: { paddingRight: 12 },
      },
    },
    MuiSelect: {
      styleOverrides: { select: { minHeight: '1.25rem' } },
    },
    MuiAutocomplete: {
      styleOverrides: {
        inputRoot: { padding: '4px 9px !important' },
        input: { padding: '5px 4px !important' },
        paper: { borderRadius: radius.md, border: `1px solid ${gray[200]}`, boxShadow: shadow.lg },
      },
    },
    MuiFormHelperText: {
      styleOverrides: { root: { marginLeft: 0, marginRight: 0, marginTop: 4, fontSize: '0.75rem', lineHeight: '1rem' } },
    },
    // .badge: pill, 2 × 10 padding, 12/600; filled chips use the soft status colours.
    MuiChip: {
      styleOverrides: {
        root: { fontWeight: 600, fontSize: '0.75rem', borderRadius: radius.pill, height: 24 },
        label: { paddingLeft: 10, paddingRight: 10 },
        sizeSmall: { height: 20, fontSize: '0.75rem' },
        labelSmall: { paddingLeft: 10, paddingRight: 10 },
        icon: { fontSize: 14, marginLeft: 6, marginRight: -4 },
        deleteIcon: { fontSize: 14 },
        filled: softChip('neutral'),
        filledPrimary: softChip('info'),
        filledSecondary: softChip('success'),
        filledSuccess: softChip('success'),
        filledError: softChip('error'),
        filledWarning: softChip('warning'),
        filledInfo: softChip('teal'),
        outlined: { borderColor: gray[300], color: gray[700] },
      },
      variants: [
        { props: { variant: 'outlined', color: 'primary' }, style: { color: brand.primary, borderColor: 'rgba(26,54,93,0.4)' } },
        { props: { variant: 'outlined', color: 'success' }, style: { color: badge.success[1], borderColor: '#86EFAC' } },
        { props: { variant: 'outlined', color: 'secondary' }, style: { color: badge.success[1], borderColor: '#86EFAC' } },
        { props: { variant: 'outlined', color: 'error' }, style: { color: badge.error[1], borderColor: '#FCA5A5' } },
        // WP-53: orange and teal text fail WCAG AA on white; outlined chips use the badge text shades (≥ 5.4:1).
        { props: { variant: 'outlined', color: 'warning' }, style: { color: badge.warning[1], borderColor: '#FDBA74' } },
        { props: { variant: 'outlined', color: 'info' }, style: { color: '#237574', borderColor: '#5EEAD4' } },
      ],
    },
    MuiBadge: {
      styleOverrides: { badge: { fontSize: '0.625rem', fontWeight: 700, minWidth: 20, height: 20, padding: '0 5px' } },
    },
    MuiAvatar: {
      styleOverrides: { root: { fontSize: '0.875rem', fontWeight: 600 }, colorDefault: { backgroundColor: brand.primary, color: '#FFFFFF' } },
    },
    MuiTabs: {
      styleOverrides: {
        root: { minHeight: 44, borderBottom: `1px solid ${gray[200]}` },
        indicator: { height: 2, backgroundColor: brand.primary },
      },
    },
    MuiTab: {
      styleOverrides: {
        root: {
          textTransform: 'none', fontWeight: 500, fontSize: '0.875rem', minHeight: 44, padding: '10px 16px', color: gray[500],
          '&:hover': { color: gray[700] },
          '&.Mui-selected': { color: brand.primary, fontWeight: 600 },
        },
      },
    },
    // White on the info teal is 3.5:1; filled info alerts (toasts) use the darker shade (5.4:1, WCAG AA). Found by journey 28.
    MuiAlert: {
      styleOverrides: {
        root: { borderRadius: radius.md, fontSize: '0.875rem', alignItems: 'center' },
        standardSuccess: { backgroundColor: '#F0FDF4', color: '#166534', border: '1px solid #BBF7D0' },
        standardError: { backgroundColor: '#FEF2F2', color: '#991B1B', border: '1px solid #FECACA' },
        standardWarning: { backgroundColor: '#FFF7ED', color: '#9A3412', border: '1px solid #FED7AA' },
        standardInfo: { backgroundColor: '#EFF6FF', color: '#1E40AF', border: '1px solid #BFDBFE' },
        filled: { boxShadow: shadow.lg },
        filledInfo: { backgroundColor: '#237574' },
        filledWarning: { backgroundColor: '#C05621' },
      },
    },
    MuiSnackbarContent: {
      styleOverrides: { root: { borderRadius: radius.md, backgroundColor: gray[800], boxShadow: shadow.lg } },
    },
    MuiTooltip: {
      styleOverrides: {
        tooltip: { backgroundColor: gray[900], fontSize: '0.75rem', lineHeight: '1rem', fontWeight: 500, borderRadius: radius.sm, padding: '6px 10px' },
        arrow: { color: gray[900] },
      },
    },
    MuiPopover: {
      styleOverrides: { paper: { borderRadius: radius.lg, border: `1px solid ${gray[200]}`, boxShadow: shadow.lg } },
    },
    MuiMenu: {
      styleOverrides: { paper: { borderRadius: radius.md, border: `1px solid ${gray[200]}`, boxShadow: shadow.lg, marginTop: 4 } },
    },
    MuiMenuItem: {
      styleOverrides: { root: { fontSize: '0.875rem', minHeight: 36, color: gray[700], '&:hover': { backgroundColor: gray[50] } } },
    },
    MuiListItemText: {
      styleOverrides: { primary: { fontSize: '0.875rem' }, secondary: { fontSize: '0.75rem', color: gray[500] } },
    },
    MuiDivider: {
      styleOverrides: { root: { borderColor: gray[200] } },
    },
    // Thirdline progress bars: 8 px, rounded, gray-200 track.
    MuiLinearProgress: {
      styleOverrides: { root: { height: 8, borderRadius: radius.pill, backgroundColor: gray[200] }, bar: { borderRadius: radius.pill } },
    },
    MuiLink: {
      defaultProps: { underline: 'hover' },
      styleOverrides: { root: { color: brand.primary, fontWeight: 500 } },
    },
    MuiBreadcrumbs: {
      styleOverrides: { root: { fontSize: '0.75rem', color: gray[500] }, separator: { marginLeft: 6, marginRight: 6 } },
    },
    MuiStepLabel: {
      styleOverrides: { label: { fontSize: '0.875rem' } },
    },
    MuiToolbar: {
      styleOverrides: { regular: { '@media (min-width: 600px)': { minHeight: layout.topBarHeight } } },
    },
  },
});

export default lightTheme;
