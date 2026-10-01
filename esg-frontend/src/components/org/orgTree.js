/** Helpers for the organisation hierarchy (flat API list with parentOrgId → tree). */

/** Returns [{ org, depth }] in tree order (parents before children, siblings by name). Orphans become roots. */
export function flattenTree(orgs) {
  const ids = new Set(orgs.map((o) => o.id));
  const children = new Map();
  orgs.forEach((o) => {
    const parent = o.parentOrgId && ids.has(o.parentOrgId) ? o.parentOrgId : null;
    if (!children.has(parent)) children.set(parent, []);
    children.get(parent).push(o);
  });
  children.forEach((list) => list.sort((a, b) => a.name.localeCompare(b.name)));
  const out = [];
  const walk = (parent, depth) => (children.get(parent) ?? []).forEach((o) => {
    out.push({ org: o, depth });
    walk(o.id, depth + 1);
  });
  walk(null, 0);
  return out;
}

/** Ids of an organisation and everything below it (used to stop moving an org under itself). */
export function subtreeIds(orgs, rootId) {
  const result = new Set([rootId]);
  let added = true;
  while (added) {
    added = false;
    orgs.forEach((o) => {
      if (o.parentOrgId && result.has(o.parentOrgId) && !result.has(o.id)) {
        result.add(o.id);
        added = true;
      }
    });
  }
  return result;
}

export const ORG_TYPES = [
  { value: 'group', label: 'Group' },
  { value: 'headquarters', label: 'Headquarters' },
  { value: 'subsidiary', label: 'Subsidiary' },
  { value: 'business_unit', label: 'Business unit' },
  { value: 'facility', label: 'Facility' },
  { value: 'site', label: 'Site' },
];
