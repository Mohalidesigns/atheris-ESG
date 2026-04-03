import { createTheme } from '@mui/material/styles';

const lightTheme = createTheme({
  palette: {
    primary: {
      main: '#1A365D',
      light: '#2A4A7F',
      dark: '#0F2340',
      contrastText: '#FFFFFF',
    },
    secondary: {
      main: '#2D7D46',
      light: '#3A9D5A',
      dark: '#1E5C32',
      contrastText: '#FFFFFF',
    },
    warning: {
      main: '#D4AF37',
      light: '#E0C55A',
      dark: '#B8961E',
    },
    error: {
      main: '#C53030',
      light: '#E05050',
      dark: '#9B2020',
    },
    info: {
      main: '#319795',
      light: '#4DB8B6',
      dark: '#237574',
    },
    orange: {
      main: '#DD6B20',
    },
    background: {
      default: '#F7FAFC',
      paper: '#FFFFFF',
    },
    text: {
      primary: '#2D3748',
      secondary: '#718096',
    },
    divider: '#E2E8F0',
  },
  typography: {
    fontFamily: '"Inter", "SF Pro Display", "Segoe UI", Roboto, sans-serif',
    h1: { fontWeight: 700, fontSize: '2rem', color: '#2D3748' },
    h2: { fontWeight: 700, fontSize: '1.5rem', color: '#2D3748' },
    h3: { fontWeight: 600, fontSize: '1.25rem', color: '#2D3748' },
    h4: { fontWeight: 600, fontSize: '1.125rem', color: '#2D3748' },
    h5: { fontWeight: 600, fontSize: '1rem', color: '#2D3748' },
    h6: { fontWeight: 600, fontSize: '0.875rem', color: '#2D3748' },
    body1: { fontFamily: '"Inter", "SF Pro Text", sans-serif', fontSize: '0.875rem' },
    body2: { fontFamily: '"Inter", "SF Pro Text", sans-serif', fontSize: '0.8125rem' },
    caption: { fontSize: '0.75rem', color: '#718096' },
    mono: { fontFamily: '"Roboto Mono", monospace' },
  },
  shape: {
    borderRadius: 8,
  },
  components: {
    MuiButton: {
      styleOverrides: {
        root: {
          textTransform: 'none',
          fontWeight: 600,
          borderRadius: 8,
          padding: '8px 20px',
        },
        containedPrimary: {
          background: '#1A365D',
          '&:hover': { background: '#2A4A7F' },
        },
        containedSecondary: {
          background: '#2D7D46',
          '&:hover': { background: '#3A9D5A' },
        },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          borderRadius: 12,
          boxShadow: '0 1px 3px rgba(0,0,0,0.08), 0 1px 2px rgba(0,0,0,0.06)',
          border: '1px solid #E2E8F0',
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: { fontWeight: 500 },
      },
    },
    MuiTableHead: {
      styleOverrides: {
        root: {
          '& .MuiTableCell-head': {
            fontWeight: 600,
            background: '#F7FAFC',
            color: '#2D3748',
          },
        },
      },
    },
  },
});

export default lightTheme;
