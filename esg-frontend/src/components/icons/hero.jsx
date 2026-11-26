// D49: Heroicons glyphs as MUI SvgIcons (see ./index.jsx).
import { forwardRef } from 'react';
import SvgIcon from '@mui/material/SvgIcon';

// Outline glyphs are stroked; MUI's SvgIcon fills with currentColor, which would flood them.
export const OUTLINE = { fill: 'none' };
export const SOLID = { fill: 'currentColor' };

export function hero(Glyph, name, style = OUTLINE) {
  const Icon = forwardRef(function Icon({ sx, titleAccess, ...props }, ref) {
    // Heroicons draw their own <title> from `title` (they drop MUI's child <title>); SvgIcon still sets role="img".
    return (
      <SvgIcon ref={ref} component={Glyph} data-testid={`${name}Icon`} titleAccess={titleAccess} title={titleAccess} {...props}
        sx={[style, ...(Array.isArray(sx) ? sx : [sx])]} />
    );
  });
  Icon.displayName = `${name}Icon`;
  Icon.muiName = 'SvgIcon';
  return Icon;
}
