// Journey 10 (master plan §7.3): gap analysis shows missing disclosures; entering data closes the gap.
// SEC Nigeria's requirements await the ESG SME (D23 (6)), so this runs on GRI 303-3 (water withdrawal), a quantitative
// requirement the engine treats exactly as it will SEC Nigeria's once their content is loaded.
import roles from '../fixtures/roles.json';

const OML42 = '0b000000-0000-4000-8000-000000000004';
const YEAR = new Date().getFullYear();

const row = (code) => cy.get('table[aria-label="Requirement checklist"]').contains('tr', code);
// Remount the tab to refetch. (No page reload: cy.apiToken's API logins replace the refresh cookie, so a reload would
// restore another user's session.)
const reopenGaps = () => {
  cy.contains('[role="tab"]', 'Overview').click();
  cy.contains('[role="tab"]', 'Gap analysis').click();
  cy.pick('Framework', 'GRI Standards');
};

describe('Journey 10: entering data closes a gap', () => {
  it('GRI 303-3 goes from Missing to Partial (submitted) to Met (approved)', () => {
    // Start clean for this year's water data (a retry may find points from the first attempt): approved → reopen and
    // delete the draft; draft → delete; in review → reject (rejected data doesn't count).
    cy.apiToken(roles.ESG_DIRECTOR.email).then((director) => {
      const del = (p) => cy.request({ method: 'DELETE', url: `/api/v1/data/points/${p.id}?version=${p.version}`, headers: { Authorization: `Bearer ${director}` } });
      cy.api(director, 'GET', `/data/points?metricKey=water_withdrawal_m3&from=${YEAR}-01-01&to=${YEAR}-12-31&size=100`).then((page) => {
        (page.content ?? []).forEach((p) => {
          if (p.validationStatus === 'approved') {
            cy.api(director, 'PUT', `/data/points/${p.id}/reopen`, { version: p.version, reason: 'E2E reset' }).then(del);
          } else if (p.validationStatus === 'draft') {
            del(p);
          } else if (p.validationStatus !== 'rejected') {
            cy.api(director, 'PUT', `/data/points/${p.id}/reject`, { version: p.version, reason: 'E2E reset' });
          }
        });
      });
    });

    cy.login(roles.COMPLIANCE_OFFICER.email);
    cy.visit('/compliance');
    cy.contains('[role="tab"]', 'Gap analysis').click();
    cy.pick('Framework', 'GRI Standards');
    row('303-3').within(() => {
      cy.contains('Missing');
      cy.contains('Enter Water withdrawal');
    });

    // The data collector enters and submits the year's withdrawal (Data Hub API; journey 3 covers the UI).
    cy.apiToken(roles.DATA_COLLECTOR.email).then((collector) => {
      cy.api(collector, 'POST', '/data/points', {
        orgId: OML42, metricKey: 'water_withdrawal_m3', numericValue: 15400,
        reportingPeriodStart: `${YEAR}-01-01`, reportingPeriodEnd: `${YEAR}-06-30`,
      }).then((p) => cy.api(collector, 'PUT', `/data/points/${p.id}/submit`, { version: p.version }))
        .as('point');
    });
    reopenGaps();
    row('303-3').within(() => {
      cy.contains('Partial');
      cy.contains('Get Water withdrawal approved');
    });

    // Analyst validates, director approves: the gap closes.
    cy.get('@point').then((p) => {
      cy.apiToken(roles.ESG_ANALYST.email).then((analyst) => cy.api(analyst, 'PUT', `/data/points/${p.id}/validate`, { version: p.version }))
        .then((v) => cy.apiToken(roles.ESG_DIRECTOR.email).then((director) => cy.api(director, 'PUT', `/data/points/${p.id}/approve`, { version: v.version })));
    });
    reopenGaps();
    row('303-3').within(() => {
      cy.contains('Met');
      cy.contains('from approved data');
      cy.contains('100%');
    });
  });
});
