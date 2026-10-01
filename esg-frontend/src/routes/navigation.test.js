import { describe, it, expect } from 'vitest';
import { firstAllowedPath, visibleSections } from './navigation';

const paths = (perms) => visibleSections(perms).flatMap((s) => s.items.map((i) => i.path));

describe('navigation filtered by permission', () => {
  it('a data collector sees only the Data Hub and Carbon', () => {
    expect(paths(['data_points:read', 'data_points:create', 'carbon_data:read', 'org_structure:read']))
      .toEqual(['/data-hub', '/carbon']);
  });
  it('administration appears with users:read or roles:read', () => {
    expect(paths(['roles:read'])).toEqual(['/admin']);
    expect(paths(['users:read'])).toEqual(['/admin']);
  });
  it('drops empty sections and has no landing page without permissions', () => {
    expect(visibleSections([])).toEqual([]);
    expect(firstAllowedPath([])).toBeNull();
  });
  it('lands on the first permitted page', () => {
    expect(firstAllowedPath(['dashboard:read', 'data_points:read'])).toBe('/dashboard');
    expect(firstAllowedPath(['training:read'])).toBe('/training');
  });
});
