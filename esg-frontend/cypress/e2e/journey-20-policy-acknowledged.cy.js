// Journey 20 (master plan §7.3): policy approved by someone else → published → acknowledgements tracked.
import roles from '../fixtures/roles.json';

describe('Journey 20: policy published and acknowledged', () => {
  it('needs a second approver, then every colleague is asked to acknowledge it', () => {
    const name = `Gifts and hospitality ${Date.now().toString(36)}`;
    cy.login(roles.ESG_DIRECTOR.email);
    cy.visit('/governance?tab=policies');
    cy.contains('button', 'New policy').click();
    cy.get('[role="dialog"]').within(() => {
      cy.field('Policy name').type(name);
      cy.field('Policy text').type('Gifts above NGN 50,000 must be declared.');
    });
    cy.pick('Category', 'Anti-bribery and corruption', { within: '[role="dialog"]' });
    cy.pick('Policy owner', 'Kemi Compliance', { within: '[role="dialog"]' });
    cy.get('[role="dialog"]').contains('button', 'Create draft').click();
    cy.get('[aria-label="Policy details"]').within(() => {
      cy.contains('button', 'Submit for approval').click();
      cy.get('[aria-label="Policy status"]').should('contain', 'In review');
      // The submitter can't approve their own policy.
      cy.get('[aria-label^="Approve: You submitted this policy"]').should('exist');
    });
    cy.get('body').type('{esc}'); // close the policy drawer
    cy.logout();

    cy.login(roles.BOARD_MEMBER.email);
    cy.visit('/governance?tab=policies');
    cy.contains('tr', name).contains('button', 'Open').click();
    cy.get('[aria-label="Policy details"]').contains('button', 'Approve').click();
    cy.get('[aria-label="Policy status"]').should('contain', 'Approved');
    cy.get('body').type('{esc}'); // close the policy drawer
    cy.logout();

    cy.login(roles.ESG_DIRECTOR.email);
    cy.visit('/governance?tab=policies');
    cy.contains('tr', name).contains('button', 'Open').click();
    cy.get('[aria-label="Policy details"]').contains('button', 'Publish').click();
    cy.get('[aria-label="Policy status"]').should('contain', 'Published');
    cy.get('[aria-label="Acknowledgements"]').should('contain', 'Acknowledged by 0 of');
    cy.get('body').type('{esc}'); // close the policy drawer
    cy.logout();

    // A data collector (no Governance menu) acknowledges it from the user menu.
    cy.login(roles.DATA_COLLECTOR.email);
    cy.get('[aria-label="Account menu"]').click();
    cy.contains('[role="menuitem"]', 'My policies').click();
    cy.contains(`[aria-label="Policy ${name}"]`, name).contains('button', 'Read and acknowledge').click();
    cy.contains('button', 'I have read and acknowledge it').click();
    cy.get(`[aria-label="Policy ${name}"]`).should('contain', 'Acknowledged');
    cy.get('body').type('{esc}'); // close the policy drawer
    cy.logout();

    cy.login(roles.ESG_DIRECTOR.email);
    cy.visit('/governance?tab=policies');
    cy.contains('tr', name).contains('button', 'Open').click();
    cy.get('[aria-label="Acknowledgements"]').should('contain', 'Acknowledged by 1 of');
  });
});
