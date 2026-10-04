// Journey 12 (master plan §7.3): a filing deadline alert fires. The full 90/60/30/14/7/1 calendar is proven with a fixed
// clock (ComplianceApiTest.journey12…, time travel); here the owner sets a filing due in a week, the reminder run fires
// and the alert reaches their notifications.
import roles from '../fixtures/roles.json';

describe('Journey 12: filing deadline alert', () => {
  it('a filing due in 7 days triggers the owner\'s reminder', () => {
    const due = new Date(Date.now() + 7 * 86400000).toISOString().slice(0, 10);
    const description = `NGX sustainability report ${Date.now()}`;
    cy.login(roles.COMPLIANCE_OFFICER.email);
    cy.visit('/compliance');
    cy.contains('[role="tab"]', 'Filing deadlines').click();
    cy.contains('button', 'Add deadline').click();
    cy.get('[role="dialog"]').within(() => {
      cy.field('Description').type(description);
      cy.field('Due date').type(due);
    });
    cy.pick('Framework', 'CBN Nigerian Sustainable Banking Principles', { within: '[role="dialog"]' });
    cy.pick('Owner', 'Kemi', { within: '[role="dialog"]' });
    cy.get('[role="dialog"]').contains('button', 'Add deadline').click();
    cy.get('[role="dialog"]').should('not.exist');
    cy.get('table[aria-label="Filing tracker"]').contains('tr', description).should('contain', 'Upcoming');

    cy.contains('button', 'Send due reminders now').click();
    cy.contains(/Reminders sent: [1-9]/);
    // 7 days in the browser's zone; the server counts in Lagos time, so the interval is 7 (or 14 across midnight).
    cy.get('table[aria-label="Filing tracker"]').contains('tr', description).contains(/Reminded: (7|14)d/);

    cy.get('[aria-label^="Notifications"]').first().click();
    cy.contains(/CBN_NSBP filing due in [6-8] days/);
    cy.contains(`${description} is due on`);
  });
});
