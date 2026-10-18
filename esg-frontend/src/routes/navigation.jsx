// D47: outlined icons, the closest MUI match to Thirdline's outline (Heroicons) set.
import {
  GridViewOutlined, StorageOutlined, FactoryOutlined, GavelOutlined, DescriptionOutlined, WarningAmberOutlined,
  LocalShippingOutlined, PeopleOutline, AccountBalanceOutlined, BarChartOutlined, TrendingUpOutlined, StorefrontOutlined,
  SchoolOutlined, AdminPanelSettingsOutlined,
} from '@mui/icons-material';

/**
 * Navigation and route permissions (TRD §7.4). One source for the sidebar and the route guards, so a menu item
 * is visible exactly when its page is reachable. `anyOf`: the user needs at least one of these permissions.
 * `module`: the subscription module (WP-19, reference.modules); the server already drops permissions of modules
 * outside the tenant's plan, and the UI also checks `user.modules` so it can say "not in your plan".
 */
// no-mock-gate: config — navigation menu definition, not data
export const navSections = [
  { label: 'MAIN', items: [
    { text: 'Dashboard', icon: <GridViewOutlined />, path: '/dashboard', module: 'platform', anyOf: ['dashboard:read'] },
  ]},
  { label: 'ESG MODULES', items: [
    { text: 'Data Hub', icon: <StorageOutlined />, path: '/data-hub', module: 'data_hub', anyOf: ['data_points:read'] },
    { text: 'Carbon Accounting', icon: <FactoryOutlined />, path: '/carbon', module: 'carbon', anyOf: ['carbon_data:read'] },
    { text: 'Compliance', icon: <GavelOutlined />, path: '/compliance', module: 'compliance', anyOf: ['compliance:read'] },
    { text: 'Reporting', icon: <DescriptionOutlined />, path: '/reporting', module: 'reporting', anyOf: ['reports:read'] },
    { text: 'Risk & Materiality', icon: <WarningAmberOutlined />, path: '/risk', module: 'risk', anyOf: ['risk:read'] },
    { text: 'Supply Chain', icon: <LocalShippingOutlined />, path: '/supply-chain', module: 'supply_chain', anyOf: ['supply_chain:read'] },
    { text: 'Social Impact', icon: <PeopleOutline />, path: '/social', module: 'social', anyOf: ['social:read'] },
    { text: 'Governance', icon: <AccountBalanceOutlined />, path: '/governance', module: 'governance', anyOf: ['governance:read'] },
  ]},
  { label: 'INSIGHTS', items: [
    { text: 'Analytics & AI', icon: <BarChartOutlined />, path: '/analytics', module: 'analytics', anyOf: ['analytics:read'] },
    { text: 'Investor Portal', icon: <TrendingUpOutlined />, path: '/investor', module: 'investor', anyOf: ['investor:read'] },
    { text: 'Carbon Market', icon: <StorefrontOutlined />, path: '/carbon-market', module: 'carbon_market', anyOf: ['carbon_market:read'] },
  ]},
  { label: 'PLATFORM', items: [
    { text: 'Training', icon: <SchoolOutlined />, path: '/training', module: 'training', anyOf: ['training:read'] },
    { text: 'Administration', icon: <AdminPanelSettingsOutlined />, path: '/admin', module: 'platform', anyOf: ['users:read', 'roles:read', 'tenant_settings:read', 'subscription:read', 'tenants:read', 'audit_logs:read', 'privacy:read', 'integrations:read'] },
  ]},
];

export const allNavItems = navSections.flatMap((s) => s.items);

/** True if the tenant is entitled to the module. `modules` null/undefined = unknown (older session) → allow. */
export const moduleEnabled = (modules, module) => !modules || module === 'platform' || modules.includes(module);

const allowed = (item, permissions, modules) =>
  moduleEnabled(modules, item.module) && item.anyOf.some((p) => permissions.includes(p));

/** Sections with only the items the user may open; empty sections are dropped. */
export function visibleSections(permissions, modules) {
  return navSections
    .map((s) => ({ ...s, items: s.items.filter((i) => allowed(i, permissions, modules)) }))
    .filter((s) => s.items.length > 0);
}

/** First page the user may open (landing page after login), or null if none. */
export function firstAllowedPath(permissions, modules) {
  return allNavItems.find((i) => allowed(i, permissions, modules))?.path ?? null;
}
