import { describe, it, expect } from 'vitest';
import { firstAllowedPath, moduleEnabled, visibleSections } from './navigation';

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

describe('navigation filtered by subscription modules (WP-19)', () => {
  const director = ['dashboard:read', 'data_points:read', 'carbon_data:read', 'risk:read', 'investor:read', 'users:read'];
  const paths = (perms, modules) => visibleSections(perms, modules).flatMap((s) => s.items.map((i) => i.path));

  it('hides a module the tenant is not entitled to, even if a role grants it', () => {
    expect(paths(director, ['platform', 'data_hub', 'carbon'])).toEqual(['/dashboard', '/data-hub', '/carbon', '/admin']);
    expect(paths(director, ['platform', 'data_hub', 'carbon', 'risk', 'investor']))
      .toEqual(['/dashboard', '/data-hub', '/carbon', '/risk', '/investor', '/admin']);
  });
  it('platform pages are always available; unknown modules list (older session) allows everything', () => {
    expect(moduleEnabled([], 'platform')).toBe(true);
    expect(moduleEnabled(undefined, 'risk')).toBe(true);
    expect(moduleEnabled(['data_hub'], 'risk')).toBe(false);
  });
  it('lands on the first page of an entitled module', () => {
    expect(firstAllowedPath(['data_points:read', 'carbon_data:read'], ['platform', 'carbon'])).toBe('/carbon');
  });
});
