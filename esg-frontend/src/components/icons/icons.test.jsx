import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Add, Star, Squares2X2 } from './index';

// jsdom resolves `fill: none` to transparent and `currentColor` to the inherited colour.
const unfilled = (el) => ['none', 'rgba(0, 0, 0, 0)'].includes(getComputedStyle(el).fill);

describe('icons (D49: Heroicons outline as MUI SvgIcons)', () => {
  it('draws the Heroicons outline glyph, stroked on the 24 px grid and not filled', () => {
    render(<Add titleAccess="Add" fontSize="small" />);
    const svg = screen.getByTestId('AddIcon');
    expect(svg).toHaveAttribute('viewBox', '0 0 24 24');
    expect(svg).toHaveAttribute('stroke', 'currentColor');
    expect(svg).toHaveAttribute('stroke-width', '1.5');
    expect(svg).toHaveClass('MuiSvgIcon-root', 'MuiSvgIcon-fontSizeSmall');
    expect(unfilled(svg)).toBe(true);
    expect(screen.getByTitle('Add')).toBeInTheDocument();
  });

  it('keeps solid glyphs filled and exports Thirdline-only names', () => {
    render(<><Star /><Squares2X2 /></>);
    expect(unfilled(screen.getByTestId('StarIcon'))).toBe(false);
    expect(screen.getByTestId('Squares2X2Icon')).toBeInTheDocument();
  });
});
