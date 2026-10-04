import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { NotYetAvailable } from './States';
import ModuleNotYetAvailable from './ModuleNotYetAvailable';

describe('NotYetAvailable', () => {
  it('names the feature and the work package that builds it', () => {
    render(<NotYetAvailable title="Reduction targets" plannedIn="WP-21">Base and target years.</NotYetAvailable>);
    const note = screen.getByRole('note', { name: 'Reduction targets: not yet available' });
    expect(note).toHaveTextContent('Not yet available');
    expect(note).toHaveTextContent('WP-21');
    expect(note).toHaveTextContent('Base and target years.');
  });

  it('module placeholder lists planned capabilities without any figures', () => {
    render(<ModuleNotYetAvailable title="Risk" subtitle="s" plannedIn="WP-32" capabilities={['Risk register']} />);
    expect(screen.getByRole('heading', { name: 'Risk' })).toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'Planned capabilities' })).toHaveTextContent('Risk register');
    expect(document.body.textContent.replace('WP-32', '')).not.toMatch(/\d/);
  });
});
