// Journey 1 (master plan §7.3): a tenant admin onboards an org tree, users and roles; the new user signs in and
// sees exactly what the org-scoped role allows.
import roles from '../fixtures/roles.json';

describe('Journey 1: tenant admin onboards org tree, users and roles', () => {
  it('builds the org tree, creates a user, scopes a role to the new site, and the user signs in with it', () => {
    // Fresh names on every attempt, so a retry never collides with the first attempt's records.
    const run = Date.now().toString(36);
    const region = `E2E Region ${run}`;
    const site = `E2E Site ${run}`;
    const email = `e2e.collector.${run}@esgpro.ng`;
    const password = `E2e!Collector${run}`;

    cy.login(roles.TENANT_ADMIN.email);
    cy.visit('/admin');

    // 1. Org tree: a region under Lagos HQ, then a site under the region.
    cy.contains('[role="tab"]', 'Organisations').click();
    cy.contains('button', 'New organisation').click();
    cy.get('[role="dialog"]').within(() => {
      cy.field('Name').type(region);
      cy.field('Country (ISO)').clear().type('NG');
      cy.field('State / region').type('Lagos');
    });
    cy.pick('Parent', 'Lagos HQ', { within: '[role="dialog"]' });
    cy.pick('Type', 'Business unit', { within: '[role="dialog"]' });
    cy.contains('[role="dialog"] button', 'Create organisation').click();
    cy.get('[role="dialog"]').should('not.exist');
    cy.contains(region).should('be.visible');

    cy.contains('button', 'New organisation').click();
    cy.get('[role="dialog"]').within(() => {
      cy.field('Name').type(site);
      cy.field('Latitude').type('6.5244');
      cy.field('Longitude').type('3.3792');
    });
    cy.pick('Parent', region, { within: '[role="dialog"]' });
    cy.contains('[role="dialog"] button', 'Create organisation').click();
    cy.get('[role="dialog"]').should('not.exist');
    cy.contains(site).should('be.visible');

    // Edit reloads what was saved (UI reload, master plan §7.2).
    cy.get(`[aria-label="Edit ${site}"]`).click();
    cy.get('[role="dialog"]').within(() => {
      cy.field('Latitude').should('have.value', '6.5244');
      cy.field('Longitude').should('have.value', '3.3792');
      cy.contains('[role="combobox"]', region);
      cy.contains('button', 'Cancel').click();
    });

    // 2. A user, with client-side validation mirroring the server first.
    cy.contains('[role="tab"]', 'Users').click();
    cy.contains('button', 'New user').click();
    cy.get('[role="dialog"]').within(() => {
      cy.field('Email').type('not-an-email');
      cy.contains('button', 'Create user').click();
      cy.contains(/valid email/i).should('be.visible');
      cy.field('Email').clear().type(email);
      cy.field('First name').type('Ebere');
      cy.field('Last name').type('Collector');
      cy.field('Initial password').type(password, { log: false });
      cy.contains('button', 'Create user').click();
    });
    cy.get('[role="dialog"]').should('not.exist');
    cy.get('input[placeholder="Search name or email"]').type(email);
    cy.contains('td', email).should('be.visible');

    // 3. An org-scoped role: DATA_COLLECTOR limited to the new site.
    cy.contains("tr", email).find(`[aria-label="Roles for ${email}"]`).click();
    cy.contains('[role="dialog"] button', 'Add role').click();
    cy.pick('Role', 'DATA COLLECTOR', { within: '[role="dialog"]' });
    cy.pick('Organisation (required)', site, { within: '[role="dialog"]' });
    cy.contains('[role="dialog"] button', 'Save roles').click();
    cy.get('[role="dialog"]').should('not.exist');
    cy.contains('tr', email).contains('DATA COLLECTOR');
    cy.logout();

    // 4. The new user signs in and sees only the collector's menus; the Data Hub offers only their site.
    cy.login(email, password);
    cy.menu().should('deep.equal', ['Data Hub', 'Carbon Accounting']);
    cy.visit('/admin');
    cy.contains("You don't have access to this page").should('be.visible');
    cy.visit('/data-hub');
    cy.contains('button', /add data point/i).click();
    cy.get('[role="dialog"]').contains('label', /organisation/i).parent().find('[role="combobox"]').click();
    cy.get('[role="listbox"] [role="option"]').then(($options) => {
      const names = Cypress._.map($options, (o) => o.innerText.trim()).filter((n) => n);
      expect(names).to.include(site);
      expect(names).not.to.include('Lagos HQ');
      expect(names).not.to.include('Port Harcourt Plant');
    });
  });
});
