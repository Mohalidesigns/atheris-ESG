import { describe, it, expect } from 'vitest';
import { humanize, roleLabel } from './labels';

describe('labels (Phase 2: one label map for forms and tables)', () => {
  it.each([
    ['lpg', 'LPG'], ['petrol_pms', 'Petrol (PMS)'], ['iot', 'IoT'], ['diesel_generator', 'Diesel generator'],
    ['electricity_grid', 'Electricity grid'], ['DEADLINE_OVERDUE', 'Deadline overdue'], ['ghg_reduction', 'GHG reduction'],
    ['', ''], [null, ''],
  ])('%s → %s', (code, label) => expect(humanize(code)).toBe(label));
  it('roles', () => expect(roleLabel('ESG_ANALYST')).toBe('ESG ANALYST'));
});
