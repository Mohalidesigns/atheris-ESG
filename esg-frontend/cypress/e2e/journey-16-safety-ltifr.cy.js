// Journey 16 (master plan §7.3): a safety incident is recorded → LTIFR is recalculated. Hours worked come from approved
// workforce figures (submitted by one person, approved by another); the rate is checked against the formula
// LTIFR = lost-time injuries × 1,000,000 ÷ hours (golden values in SocialCalculatorsTest).
import roles from '../fixtures/roles.json';

const OML42 = '0b000000-0000-4000-8000-000000000004';

describe('Journey 16: safety incident recalculates LTIFR', () => {
  it('approved hours + a lost-time injury give the new rate, and the investigation runs to closure', () => {
    const tag = Date.now().toString(36);
    const year = new Date().getFullYear();
    // A period that has ended (figures can only be submitted after it ends); the day varies so reruns don't collide.
    const end = `${year}-06-${String(10 + (Date.now() % 18)).padStart(2, '0')}`;
    const lines = [
      { level: 'non_management', gender: 'female', ageBand: '30_50', headcount: 30, disabledHeadcount: 1, totalAnnualPay: null },
      { level: 'non_management', gender: 'male', ageBand: '30_50', headcount: 50, disabledHeadcount: 0, totalAnnualPay: null },
    ];
    cy.apiToken(roles.ESG_MANAGER.email).then((manager) => {
      cy.api(manager, 'POST', '/social/workforce', { orgId: OML42, reportingPeriodStart: `${year}-01-01`, reportingPeriodEnd: end,
        hoursWorked: 250000, lines }).then((s) => cy.api(manager, 'POST', `/social/workforce/${s.id}/actions`, { action: 'submit', version: s.version }))
        .then((s) => cy.apiToken(roles.ESG_DIRECTOR.email).then((director) => {
          // The manager can't approve (approval needs social:approve); the director does.
          cy.apiResponse(manager, 'POST', `/social/workforce/${s.id}/actions`, { action: 'approve', version: s.version }).its('status').should('eq', 403);
          cy.api(director, 'POST', `/social/workforce/${s.id}/actions`, { action: 'approve', version: s.version }).its('status').should('eq', 'approved');
        }));
    });
    cy.apiToken(roles.ESG_DIRECTOR.email).then((t) => cy.api(t, 'GET', `/social/safety/statistics?year=${year}`)).as('before', { type: 'static' });

    cy.login(roles.ESG_DIRECTOR.email);
    cy.visit('/social?tab=safety');
    cy.get('[aria-label="Safety rates"]').should('contain', 'LTIFR');
    cy.contains('button', 'Record incident').click();
    cy.pick('Organisation', 'OML 42 Field', { within: '.MuiDialog-paper' });
    cy.get('.MuiDialog-paper').within(() => {
      cy.field('Title').type(`Hand caught in valve wheel ${tag}`);
      cy.field('Date and time').type(`${year}-07-01T10:30`);
    });
    cy.pick('Type', 'Injury', { within: '.MuiDialog-paper' });
    cy.pick('Outcome', 'Lost time', { within: '.MuiDialog-paper' });
    // The form applies the server's rules: an injury names who was hurt; lost time has lost days.
    cy.get('.MuiDialog-paper').contains('button', 'Record incident').click();
    cy.get('.MuiDialog-paper').should('contain', 'say who was hurt').and('contain', 'a lost-time injury has at least one lost day');
    cy.pick('Person affected', 'Employee', { within: '.MuiDialog-paper' });
    cy.get('.MuiDialog-paper').within(() => {
      cy.field('Days lost or restricted').type('2');
      cy.contains('button', 'Record incident').click();
    });
    cy.get('.MuiDialog-paper').should('not.exist');

    // LTIFR recalculated from the register and the approved hours.
    cy.get('@before').then((b) => {
      const expected = (((b.lostTimeInjuries + 1) * 1000000) / Number(b.hoursWorked)).toFixed(2);
      cy.get('[aria-label="Safety rates"]').should('contain', expected);
    });

    // Investigation: start, record findings; whoever records findings can't close, so an approver closes it.
    cy.get('[role="region"][aria-label="Incident details"]').within(() => {
      cy.get('[aria-label="Incident status"]').should('contain', 'Reported');
      cy.contains('button', 'Start investigation').click();
    });
    cy.pick('Investigator', 'Amina', { within: '.MuiDialog-paper' });
    cy.get('.MuiDialog-paper').last().contains('button', 'Start investigation').click();
    cy.get('[aria-label="Incident status"]').should('contain', 'Investigating');
    cy.contains('button', 'Record findings').click();
    cy.get('.MuiDialog-paper').last().within(() => {
      cy.field('Root cause').type('Valve wheel without a guard');
      cy.field('Corrective actions').type('Guards fitted to all wheels; toolbox talk held');
      cy.contains('button', 'Record findings').click();
    });
    cy.get('[aria-label="Incident status"]').should('contain', 'Actions open');
    cy.get('[aria-label^="Close incident: You recorded the findings"]').should('exist');
    cy.get('[role="region"][aria-label="Incident details"]').should('contain', 'Valve wheel without a guard');
  });
});
