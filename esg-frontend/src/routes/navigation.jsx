import {
  Dashboard, CloudQueue, Factory, Gavel, Description, Warning, LocalShipping, People, AccountBalance,
  Analytics, TrendingUp, Storefront, School, AdminPanelSettings,
} from '@mui/icons-material';

/**
 * Navigation and route permissions (TRD §7.4). One source for the sidebar and the route guards, so a menu item
 * is visible exactly when its page is reachable. `anyOf`: the user needs at least one of these permissions.
 */
// no-mock-gate: config — navigation menu definition, not data
export const navSections = [
  { label: 'MAIN', items: [
    { text: 'Dashboard', icon: <Dashboard />, path: '/dashboard', anyOf: ['dashboard:read'] },
  ]},
  { label: 'ESG MODULES', items: [
    { text: 'Data Hub', icon: <CloudQueue />, path: '/data-hub', anyOf: ['data_points:read'] },
    { text: 'Carbon Accounting', icon: <Factory />, path: '/carbon', anyOf: ['carbon_data:read'] },
    { text: 'Compliance', icon: <Gavel />, path: '/compliance', anyOf: ['compliance:read'] },
    { text: 'Reporting', icon: <Description />, path: '/reporting', anyOf: ['reports:read'] },
    { text: 'Risk & Materiality', icon: <Warning />, path: '/risk', anyOf: ['risk:read'] },
    { text: 'Supply Chain', icon: <LocalShipping />, path: '/supply-chain', anyOf: ['supply_chain:read'] },
    { text: 'Social Impact', icon: <People />, path: '/social', anyOf: ['social:read'] },
    { text: 'Governance', icon: <AccountBalance />, path: '/governance', anyOf: ['governance:read'] },
  ]},
  { label: 'INSIGHTS', items: [
    { text: 'Analytics & AI', icon: <Analytics />, path: '/analytics', anyOf: ['analytics:read'] },
    { text: 'Investor Portal', icon: <TrendingUp />, path: '/investor', anyOf: ['investor:read'] },
    { text: 'Carbon Market', icon: <Storefront />, path: '/carbon-market', anyOf: ['carbon_market:read'] },
  ]},
  { label: 'PLATFORM', items: [
    { text: 'Training', icon: <School />, path: '/training', anyOf: ['training:read'] },
    { text: 'Administration', icon: <AdminPanelSettings />, path: '/admin', anyOf: ['users:read', 'roles:read', 'tenant_settings:read', 'tenants:read', 'audit_logs:read'] },
  ]},
];

export const allNavItems = navSections.flatMap((s) => s.items);

/** Sections with only the items the user may open; empty sections are dropped. */
export function visibleSections(permissions) {
  return navSections
    .map((s) => ({ ...s, items: s.items.filter((i) => i.anyOf.some((p) => permissions.includes(p))) }))
    .filter((s) => s.items.length > 0);
}

/** First page the user may open (landing page after login), or null if none. */
export function firstAllowedPath(permissions) {
  return allNavItems.find((i) => i.anyOf.some((p) => permissions.includes(p)))?.path ?? null;
}
