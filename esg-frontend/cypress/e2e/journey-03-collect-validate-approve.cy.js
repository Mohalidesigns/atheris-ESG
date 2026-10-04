// Journey 3 (master plan §7.3): a data collector submits data points → an analyst validates → a director approves.
import roles from '../fixtures/roles.json';

describe('Journey 3: collect → validate → approve', () => {
  it('the point moves through the workflow and each step is recorded', () => {
    cy.uniqueDay().then((day) => {
      // 1. The collector (scoped to OML 42 Field) enters diesel use and submits it.
      cy.login(roles.DATA_COLLECTOR.email);
      cy.visit('/data-hub');
      cy.contains('button', 'Add data point').click();
      cy.get('[role="dialog"]').within(() => {
        cy.field('Metric').type('Diesel');
      });
      cy.contains('[role="option"]', 'Diesel consumed').click();
      cy.pick('Organisation', 'OML 42 Field', { within: '[role="dialog"]' });
      cy.get('[role="dialog"]').within(() => {
        cy.field('Value').type('842.5');
        cy.field('Period start').type(day);
        cy.field('Period end').type(day);
        cy.contains('button', 'Save and submit').click();
      });
      cy.get('[role="region"][aria-label="Data point details"]').within(() => {
        cy.contains('Submitted');
        cy.get('[aria-label="Quality score"]').should('contain', 'Data quality');
        cy.contains('Submitted by Emeka Nwosu');
      });
      cy.get('[aria-label="Close"]').click();
      cy.logout();

      // 2. The analyst validates it from the review queue; they can't approve.
      cy.login(roles.ESG_ANALYST.email);
      cy.visit('/data-hub');
      cy.contains('[role="tab"]', 'Review').click();
      cy.get('table[aria-label="Awaiting validation"]').find(`[aria-label="Open Diesel consumed ${day}"]`).click();
      cy.get('[role="region"][aria-label="Data point details"]').within(() => {
        cy.contains('button', 'Approve').should('not.exist');
        cy.contains('button', 'Validate').click();
        cy.contains('Validated');
        cy.contains('Validated by Chidi Okafor');
      });
      cy.get('[aria-label="Close"]').click();
      cy.logout();

      // 3. The director approves; the point is locked (only "Reopen" remains).
      cy.login(roles.ESG_DIRECTOR.email);
      cy.visit('/data-hub');
      cy.contains('[role="tab"]', 'Review').click();
      cy.get('table[aria-label="Awaiting approval"]').find(`[aria-label="Open Diesel consumed ${day}"]`).click();
      cy.get('[role="region"][aria-label="Data point details"]').within(() => {
        cy.contains('button', 'Approve').click();
        cy.contains('Approved');
        cy.contains('Approved by Adaeze Usman');
        cy.contains('button', 'Edit').should('not.exist');
        cy.contains('button', 'Reopen').should('be.visible');
      });
    });
  });
});
