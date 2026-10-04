// Journey 11 (master plan §7.3): GRI data auto-maps to ISSB S1/S2 — enter once, map to many.
import roles from '../fixtures/roles.json';

const HQ = '0b000000-0000-4000-8000-000000000001';
const YEAR = new Date().getFullYear();

describe('Journey 11: GRI data maps to ISSB', () => {
  it('Scope 1 data answers GRI 305-1 and IFRS S2 together; a TCFD attestation meets its ISSB S2 equivalent', () => {
    // Scope 1 emissions recorded in Carbon this year (journey 6 does it through the calculator).
    cy.apiToken(roles.ESG_DIRECTOR.email).then((director) => cy.api(director, 'POST', '/carbon/emissions', {
      orgId: HQ, scope: 'scope_1', sourceActivity: 'diesel_generator', activityData: 250, activityUnit: 'litre',
      reportingPeriodStart: `${YEAR}-01-01`, reportingPeriodEnd: `${YEAR}-01-31`,
    }));

    cy.login(roles.COMPLIANCE_OFFICER.email);
    cy.visit('/compliance');
    cy.contains('[role="tab"]', 'Cross-framework mapping').click();
    cy.contains('button', `Map ${YEAR}`).click();
    cy.get('[aria-label="Mapping summary"]').should('contain', 'ISSB_S2 requirements map from GRI');
    cy.get('table[aria-label="Mapped requirements"]').contains('tr', 'S2-29(a)(i)(1)').within(() => {
      cy.contains('Met');
      cy.contains('305-1');
      cy.contains('Equivalent');
    });

    // Attest TCFD GOV-a once, with evidence…
    cy.contains('[role="tab"]', 'Gap analysis').click();
    cy.pick('Framework', 'TCFD Recommendations');
    cy.get('[aria-label="Record status for GOV-a"]').click();
    cy.get('[role="dialog"]').within(() => {
      cy.get('[data-testid="evidence-input"]').selectFile({ contents: Cypress.Buffer.from('%PDF-1.4 board minutes'), fileName: 'board-minutes.pdf', mimeType: 'application/pdf' }, { force: true });
      cy.contains('board-minutes.pdf');
    });
    cy.pick('Status', 'Complete', { within: '[role="dialog"]' });
    cy.get('[role="dialog"]').within(() => {
      cy.field('Notes').clear().type('Board oversight described in the 2026 governance report');
      cy.contains('button', 'Save status').click();
    });
    cy.get('[role="dialog"]').should('not.exist');
    cy.get('table[aria-label="Requirement checklist"]').contains('tr', 'GOV-a').should('contain', 'Met').and('contain', 'attested');

    // …and IFRS S2 6(a) is met through the mapping.
    cy.pick('Framework', 'IFRS S2 Climate-related Disclosures');
    cy.get('table[aria-label="Requirement checklist"]').contains('tr', 'S2-6(a)').within(() => {
      cy.contains('Met');
      cy.contains('via mapped requirement · TCFD GOV-a');
    });
  });
});
