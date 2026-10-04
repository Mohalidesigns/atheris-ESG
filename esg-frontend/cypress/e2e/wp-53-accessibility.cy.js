// WP-53: WCAG 2.1 AA (axe-core) on every module page as a role that can use it, the public pages and key dialogs.
import roles from '../fixtures/roles.json';

// no-mock-gate: config — pages to check, the role that sees each, and what shows when it has loaded
const PAGES = [
  ['/dashboard', 'ESG_MANAGER', 'h1'],
  ['/data-hub', 'ESG_MANAGER', 'h1'],
  ['/carbon', 'ESG_MANAGER', 'h1'],
  ['/compliance', 'ESG_MANAGER', 'h1'],
  ['/reporting', 'ESG_MANAGER', 'h1'],
  ['/risk', 'ESG_MANAGER', 'h1'],
  ['/supply-chain', 'ESG_MANAGER', 'h1'],
  ['/social', 'ESG_MANAGER', 'h1'],
  ['/analytics', 'ESG_MANAGER', 'h1'],
  ['/carbon-market', 'ESG_MANAGER', 'h1'],
  ['/training', 'ESG_MANAGER', 'h1'],
  ['/governance', 'COMPLIANCE_OFFICER', 'h1'],
  ['/admin', 'COMPLIANCE_OFFICER', 'h1'],
  ['/my-policies', 'ESG_ANALYST', 'h1'],
  ['/investor', 'INVESTOR_VIEWER', 'h1'],
  ['/training?tab=catalogue', 'ESG_ANALYST', 'h1'],
  ['/carbon-market?tab=retirements', 'ESG_MANAGER', 'h1'],
  ['/analytics?tab=forecasts', 'ESG_MANAGER', 'h1'],
  ['/admin?tab=privacy', 'COMPLIANCE_OFFICER', 'h1'],
  ['/admin?tab=users', 'TENANT_ADMIN', 'h1'],
];

describe('WP-53: WCAG 2.1 AA', () => {
  it('public pages: sign-in, whistleblowing, certificate check', () => {
    cy.clearCookies();
    cy.visit('/login');
    cy.contains('button', 'Sign In');
    cy.a11y('/login');
    cy.visit('/whistleblower/esg-demo');
    cy.contains('What happened?');
    cy.a11y('/whistleblower/esg-demo');
    cy.visit('/verify-certificate/ABCDEFGH23');
    cy.contains('No certificate has this code');
    cy.a11y('/verify-certificate');
  });

  PAGES.forEach(([path, role, ready]) => {
    it(`${path} (${role})`, () => {
      cy.login(roles[role].email);
      cy.visit(path);
      cy.get(ready).should('be.visible');
      cy.get('.MuiCircularProgress-root').should('not.exist'); // loading spinners (bars showing data stay)
      cy.a11y(path);
    });
  });

  it('dialogs: adding a data point, a course', () => {
    cy.login(roles.ESG_MANAGER.email);
    cy.visit('/data-hub');
    cy.contains('button', /Add data point|Add data/).click();
    cy.get('[role="dialog"]').should('be.visible');
    cy.get('.MuiBackdrop-root').should('have.css', 'opacity', '1'); // after the fade-in, or colours are measured mid-blend
    cy.a11y('Data point dialog');
    cy.login(roles.TRAINING_ADMIN.email);
    cy.visit('/training?tab=courses');
    cy.contains('button', 'New course').click();
    cy.get('[role="dialog"]').should('be.visible');
    cy.get('.MuiBackdrop-root').should('have.css', 'opacity', '1');
    cy.a11y('Course dialog');
  });
});
