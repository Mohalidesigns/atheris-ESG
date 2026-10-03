import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import CarbonMarketPage from './carbonMarket/components/CarbonMarketPage';
import TrainingPage from './training/components/TrainingPage';

// Modules without a backend (WP-16 quarantine): each says it isn't available and which WP builds it; no figures.
describe.each([
  ['Carbon Market', CarbonMarketPage, 'WP-41'],
  ['Training & Capacity Building', TrainingPage, 'WP-42'],
])('%s page', (title, Page, wp) => {
  it('is an honest placeholder', () => {
    render(<Page />);
    expect(screen.getByRole('heading', { name: title })).toBeInTheDocument();
    expect(screen.getByRole('note')).toHaveTextContent('Not yet available');
    expect(screen.getByRole('note')).toHaveTextContent(wp);
    expect(document.body.textContent.replace(/WP-\d+|Phase 5|10|TRD §8\.6|CAMA 2020|Scope 3/g, '')).not.toMatch(/\d/);
  });
});
