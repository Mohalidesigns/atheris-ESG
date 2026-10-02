import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import AnalyticsPage from './analytics/components/AnalyticsPage';
import CarbonMarketPage from './carbonMarket/components/CarbonMarketPage';
import InvestorPage from './investor/components/InvestorPage';
import SupplyChainPage from './supplyChain/components/SupplyChainPage';
import TrainingPage from './training/components/TrainingPage';

// Modules without a backend (WP-16 quarantine): each says it isn't available and which WP builds it; no figures.
describe.each([
  ['Analytics', AnalyticsPage, 'WP-35'], ['Carbon Market', CarbonMarketPage, 'WP-41'],
  ['Investor Portal', InvestorPage, 'WP-40'],
  ['Supply Chain ESG', SupplyChainPage, 'WP-31'], ['Training & Capacity Building', TrainingPage, 'WP-42'],
])('%s page', (title, Page, wp) => {
  it('is an honest placeholder', () => {
    render(<Page />);
    expect(screen.getByRole('heading', { name: title })).toBeInTheDocument();
    expect(screen.getByRole('note')).toHaveTextContent('Not yet available');
    expect(screen.getByRole('note')).toHaveTextContent(wp);
    expect(document.body.textContent.replace(/WP-\d+|Phase 5|10|TRD §8\.6|CAMA 2020|Scope 3/g, '')).not.toMatch(/\d/);
  });
});
