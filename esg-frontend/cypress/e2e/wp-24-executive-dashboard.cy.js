// WP-24 executive dashboard: live figures that follow the workflow, deep links, and cards that follow permissions.
import roles from '../fixtures/roles.json';

const OML42 = '0b000000-0000-4000-8000-000000000004';
const YEAR = new Date().getFullYear();

describe('WP-24: executive dashboard', () => {
  it('an approval shows up in the activity feed and the year\'s emissions; the link opens the point', () => {
    const day = `${YEAR}-01-${String(10 + (Math.floor(Date.now() / 1000) % 18)).padStart(2, '0')}`;
    cy.apiToken(roles.DATA_COLLECTOR.email).then((collector) => cy.api(collector, 'POST', '/data/points', {
      orgId: OML42, metricKey: 'electricity_kwh', numericValue: 20000, reportingPeriodStart: day, reportingPeriodEnd: day,
    }).then((p) => cy.api(collector, 'PUT', `/data/points/${p.id}/submit`, { version: p.version })))
      .then((p) => cy.apiToken(roles.ESG_ANALYST.email).then((t) => cy.api(t, 'PUT', `/data/points/${p.id}/validate`, { version: p.version })))
      .then((p) => cy.apiToken(roles.ESG_DIRECTOR.email).then((t) => cy.api(t, 'PUT', `/data/points/${p.id}/approve`, { version: p.version })))
      .as('point');

    cy.login(roles.ESG_DIRECTOR.email);
    cy.visit('/');
    cy.contains(`Emissions ${YEAR} so far`);
    cy.get('[aria-label="Emissions trend chart"]').should('exist');
    cy.get('table[aria-label="Emissions by period"]').should('contain', String(YEAR));
    cy.get('[aria-label="Compliance by framework"]').should('contain', 'GRI');
    cy.get('[aria-label^="SDG 7 "]').invoke('attr', 'aria-label').should('match', /[1-9]\d* of \d+ metrics with data/);
    cy.contains('button', 'Quarters').click();
    cy.get('table[aria-label="Emissions by period"]').should('contain', `${YEAR}-Q1`);
    cy.get('@point').then((p) => {
      cy.get('[aria-label="Recent activity"]').contains('li', 'Adaeze Usman').contains('a', 'Electricity consumed · OML 42 Field').click();
      cy.location('search').should('eq', `?point=${p.id}`);
      cy.get('[role="region"][aria-label="Data point details"]').should('contain', 'Approved');
    });
  });

  it('a board member sees the dashboard without carbon, compliance or Data Hub figures', () => {
    cy.login(roles.BOARD_MEMBER.email);
    cy.visit('/');
    cy.contains('Recent activity');
    cy.contains(`Emissions ${YEAR} so far`).should('not.exist');
    cy.contains('Data points awaiting review').should('not.exist');
    cy.contains('SDG alignment').should('not.exist');
  });
});
