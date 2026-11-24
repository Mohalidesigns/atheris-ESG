import { Box } from '@mui/material';

const SOURCES = {
  white: '/Meridian/Meridian-white.svg', // on the navy sidebar and app bars
  dark: '/Meridian/Meridian-dark.svg', // on light surfaces
  mark: '/Meridian/Meridian-favicon.svg', // the mark alone (collapsed sidebar rail)
};

/** The Meridian logo (public/Meridian). The SVGs keep their ratio (wordmark 371×112, mark square), so only the height needs setting. */
export default function BrandLogo({ variant = 'white', height = 36, sx }) {
  return <Box component="img" src={SOURCES[variant]} alt="Meridian" sx={{ height, width: 'auto', display: 'block', ...sx }} />;
}
