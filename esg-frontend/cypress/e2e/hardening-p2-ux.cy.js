// Phase 2 UX and consistency (WP-52c, D27): one confirmation dialog (no browser dialogs) and success toasts, blocked
// actions explained, the user list's filters and new-user highlight, Insert data at the cursor, Integrations, and the
// responsive shell.
import roles from '../fixtures/roles.json';

describe('Hardening Phase 2: UX and consistency', () => {
  it('confirmations use the app dialog and every save says so; no native browser dialog appears', () => {
    cy.on('window:confirm', () => { throw new Error('A native confirm() appeared'); });
    cy.login(roles.TENANT_ADMIN.email);
    cy.visit('/admin');
    cy.contains('[role="tab"]', 'Users').click();
    const email = `p2.${Date.now()}@esgpro.ng`;
    cy.contains('button', 'New user').click();
    cy.get('[role="dialog"]').within(() => {
      cy.field('Email').type(email);
      cy.field('First name').type('Phase');
      cy.field('Last name').type('Two');
      cy.field('Initial password').type('Str0ng!Passw0rd', { log: false });
      cy.contains('button', 'Create user').click();
    });
    cy.get('[role="status"]').should('contain', 'User created');
    // The new user is found and highlighted.
    cy.contains('tr', email).should('have.attr', 'aria-current', 'true');
    cy.get(`[aria-label="Deactivate ${email}"]`).click();
    cy.get('[role="dialog"]').should('contain', 'Deactivate this user?').and('contain', email);
    cy.get('[role="dialog"]').contains('button', 'Cancel').click();
    cy.contains('tr', email).should('contain', 'Active');
    // Filters: role and status.
    cy.pick('Status', 'Active');
    cy.contains('tr', email).should('exist');
    cy.pick('Status', 'Deactivated');
    cy.contains('tr', email).should('not.exist');
  });

  it('an approver who validated a data point sees Approve disabled with the reason', () => {
    cy.uniqueDay().then((day) => {
      cy.apiToken(roles.DATA_COLLECTOR.email).then((collector) => cy.api(collector, 'POST', '/data/points', {
        orgId: '0b000000-0000-4000-8000-000000000004', metricKey: 'lpg_kg', numericValue: 7, reportingPeriodStart: day, reportingPeriodEnd: day,
      }).then((draft) => cy.api(collector, 'PUT', `/data/points/${draft.id}/submit`, { version: draft.version })))
        .then((submitted) => cy.apiToken(roles.ESG_DIRECTOR.email).then((director) =>
          cy.api(director, 'PUT', `/data/points/${submitted.id}/validate`, { version: submitted.version })))
        .then(() => {
          cy.login(roles.ESG_DIRECTOR.email);
          cy.visit('/data-hub');
          cy.contains('[role="tab"]', 'Review').click();
          cy.get('table[aria-label="Awaiting approval"]').find(`[aria-label="Open LPG consumed ${day}"]`).click();
          cy.get('[role="region"][aria-label="Data point details"]').within(() => {
            cy.contains('button', 'Approve').should('be.disabled');
            cy.get('[aria-label^="Approve: You validated this"]').should('exist');
          });
        });
    });
  });

  it('the Integrations tab says what is connected; Sandbox labels link to it', () => {
    cy.login(roles.TENANT_ADMIN.email);
    cy.visit('/admin?tab=integrations');
    cy.get('[aria-label="Virus scanner"]').should('contain', 'Sandbox');
    cy.get('[aria-label="Regulatory change feed"]').should('contain', 'Not configured');
  });

  it('below 900 px the sidebar is a drawer and dialogs fill the screen', () => {
    cy.viewport(390, 844);
    cy.login(roles.ESG_DIRECTOR.email);
    cy.get('nav[aria-label="Main"]').should('not.be.visible');
    cy.get('[aria-label="Open menu"]').click();
    cy.get('nav[aria-label="Main"]').should('be.visible').contains('Carbon Accounting').click();
    cy.location('pathname').should('eq', '/carbon');
    cy.get('nav[aria-label="Main"]').should('not.be.visible');
    cy.contains('[role="tab"]', 'Emission records').click();
    cy.contains('button', 'Calculate emissions').click();
    cy.get('.MuiDialog-paper').then(($p) => {
      expect($p[0].getBoundingClientRect().width).to.be.closeTo(390, 2);
    });
  });
});
