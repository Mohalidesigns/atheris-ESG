import { describe, it, expect } from 'vitest';
import { flattenTree, subtreeIds } from './orgTree';

const orgs = [
  { id: 'hq', name: 'Lagos HQ', parentOrgId: null },
  { id: 'b', name: 'Port Harcourt', parentOrgId: 'hq' },
  { id: 'a', name: 'Abuja', parentOrgId: 'hq' },
  { id: 'a1', name: 'Abuja Annex', parentOrgId: 'a' },
  { id: 'orphan', name: 'Orphan', parentOrgId: 'gone' },
];

describe('org tree helpers', () => {
  it('orders parents before children, siblings by name, orphans as roots', () => {
    expect(flattenTree(orgs).map(({ org, depth }) => `${depth}:${org.id}`))
      .toEqual(['0:hq', '1:a', '2:a1', '1:b', '0:orphan']);
  });
  it('finds an organisation and everything below it', () => {
    expect([...subtreeIds(orgs, 'a')].sort()).toEqual(['a', 'a1']);
    expect([...subtreeIds(orgs, 'hq')].sort()).toEqual(['a', 'a1', 'b', 'hq']);
  });
});
