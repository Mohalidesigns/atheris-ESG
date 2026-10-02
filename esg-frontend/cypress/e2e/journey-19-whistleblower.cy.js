// Journey 19 (master plan §7.3): anonymous whistleblower submission (no login) → case reference → investigator updates
// → the submitter checks the status.
import roles from '../fixtures/roles.json';

describe('Journey 19: anonymous whistleblower report', () => {
  it('is received without sign-in, investigated by the ethics officer and followed by the reporter', () => {
    const story = `Procurement asked a supplier for cash to win tender ${Date.now()}.`;
    cy.clearCookies();
    cy.visit('/whistleblower/esg-demo');
    cy.get('[aria-label="Privacy"]').should('contain', "we don't record your name, email, IP address or device");
    cy.pick('What is it about?', 'Bribery or corruption');
    cy.field('What happened?').type(story);
    cy.contains('button', 'Send report').click();
    cy.get('[aria-label="Case reference"]').invoke('text').as('ref', { type: 'static' });
    cy.get('[aria-label="Secret"]').invoke('text').as('secret', { type: 'static' });
    cy.get('header.MuiAppBar-root').should('not.exist'); // never signed in

    // The ethics officer (the only role that can read cases) triages it and leaves an update for the reporter.
    cy.login(roles.ETHICS_OFFICER.email);
    cy.visit('/governance?tab=whistleblowing');
    cy.get("@ref").then((ref) => cy.get(`[aria-label="Open ${ref}"]`).click());
    cy.get('[role="region"][aria-label="Case details"]').within(() => {
      cy.contains(story);
      cy.contains('Opening a case is recorded in the audit log.');
    });
    cy.pick('Status', 'Being assessed', { within: '[aria-label="Case details"]' });
    cy.get('[aria-label="Case details"]').within(() => {
      cy.field('Update for the reporter').type('We have started looking into your report.');
      cy.contains('button', 'Save case').click();
      cy.get('[aria-label="Case status"]').should('contain', 'Being assessed');
    });
    cy.get('body').type('{esc}'); // close the case drawer
    cy.logout();

    // A director can't see cases at all.
    cy.login(roles.ESG_DIRECTOR.email);
    cy.visit('/governance?tab=whistleblowing');
    cy.get('[aria-label="Reporting link"]').should('contain', '/whistleblower/esg-demo');
    cy.get('table[aria-label="Whistleblower cases"]').should('not.exist');
    cy.logout();

    // The reporter checks progress anonymously.
    cy.clearCookies();
    cy.visit('/whistleblower/esg-demo');
    cy.contains('[role="tab"]', 'Check a report').click();
    cy.get('@ref').then((ref) => cy.field('Case reference').type(ref));
    cy.get('@secret').then((secret) => cy.field('Secret').type(secret, { log: false }));
    cy.contains('button', 'Check status').click();
    cy.get('[aria-label="Case status"]').should('contain', 'Being assessed').and('contain', 'We have started looking into your report.');
  });
});
