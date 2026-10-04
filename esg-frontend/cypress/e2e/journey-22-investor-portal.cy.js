// Journey 22 (master plan §7.3): an investor sees only published scorecards and the data room they were given.
// Scorecard arithmetic: ScorecardMathTest; access rules: InvestorApiTest.
import roles from '../fixtures/roles.json';

const YEAR = new Date().getFullYear();

describe('Journey 22: investor sees only published scorecards and their data room', () => {
  it('a published scorecard and a room document reach the investor; a draft does not', () => {
    const tag = Date.now().toString(36);
    const published = `Investment committee scorecard ${tag}`;
    const draft = `Working draft scorecard ${tag}`;
    const room = `Series B diligence ${tag}`;

    // The director generates two scorecards; another manager (tenant admin) publishes one.
    cy.apiToken(roles.ESG_DIRECTOR.email).then((director) => {
      cy.api(director, 'POST', '/investor/scorecards', { year: YEAR, title: draft });
      cy.api(director, 'POST', '/investor/scorecards', { year: YEAR, title: published, commentary: 'Prepared for the Q4 committee' }).then((s) =>
        cy.apiToken(roles.TENANT_ADMIN.email).then((admin) => cy.api(admin, 'POST', `/investor/scorecards/${s.id}/actions`, { action: 'publish', version: s.version })));
    });

    // The director creates a data room for the investor and adds a document (UI).
    cy.login(roles.ESG_DIRECTOR.email);
    cy.visit('/investor?tab=rooms');
    cy.contains('button', 'New data room').click();
    cy.get('.MuiDialog-paper').within(() => {
      cy.field('Room name').type(room);
      cy.field('Expires on').type(`${YEAR + 1}-12-31`);
    });
    cy.pick('Investors with access', 'Ifeoma Investor', { within: '.MuiDialog-paper' });
    cy.get('body').type('{esc}');
    cy.get('.MuiDialog-paper').contains('button', 'Create room').click();
    cy.get('.MuiDialog-paper').should('not.exist');
    cy.get('table[aria-label="Data rooms"]').contains('tr', room).contains('button', 'Open').click();
    cy.get('[role="region"][aria-label="Data room"]').within(() => {
      cy.get('input[aria-label="Room document"]').selectFile('cypress/fixtures/esg-policy.pdf', { force: true });
      cy.get('[aria-label="Room documents"]').should('contain', 'esg-policy');
      cy.contains('Ifeoma Investor');
    });

    // The investor sees the published scorecard (not the draft) and their room.
    cy.login(roles.INVESTOR_VIEWER.email);
    cy.visit('/investor');
    cy.contains('published ESG information shared with you');
    cy.get(`[aria-label="Scorecard ${published}"]`).should('contain', 'Prepared for the Q4 committee').find('[aria-label="Grade"]').invoke('text')
      .should('match', /^(A\+|A|B\+|B|C\+|C|D|F)$/);
    cy.contains(draft).should('not.exist');
    cy.contains('button', 'Generate scorecard').should('not.exist');
    cy.contains('[role="tab"]', 'Data rooms').click();
    cy.get('table[aria-label="Data rooms"]').contains('tr', room).contains('button', 'Open').click();
    cy.intercept('GET', '**/investor/data-rooms/*/documents/*/download').as('download');
    cy.get('[role="region"][aria-label="Data room"]').within(() => {
      cy.contains('Access log').should('not.exist');
      cy.get('[aria-label="Download esg-policy"]').click();
    });
    cy.wait('@download').its('response.statusCode').should('eq', 200);

    // The director sees who opened and downloaded what.
    cy.apiToken(roles.ESG_DIRECTOR.email).then((director) => cy.api(director, 'GET', '/investor/data-rooms').then((rooms) => {
      const r = rooms.find((x) => x.name === room);
      cy.api(director, 'GET', `/investor/data-rooms/${r.id}/access-log`).then((log) => {
        expect(log.map((l) => l.action)).to.include.members(['view', 'download']);
        expect(log.find((l) => l.action === 'download').email).to.eq(roles.INVESTOR_VIEWER.email);
        expect(log.find((l) => l.action === 'download').watermarked).to.eq(true);
      });
    }));
  });
});
