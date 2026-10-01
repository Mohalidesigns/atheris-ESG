// Journey 29 (master plan §7.3): the audit log shows who changed what for an approved data point.
import roles from '../fixtures/roles.json';

describe('Journey 29: who changed what on an approved data point', () => {
  it('the data point history and the audit log name each person and change', () => {
    cy.uniqueDay().then((day) => {
      // Set up through the API: collector creates, edits and submits; analyst validates; director approves.
      cy.apiToken(roles.DATA_COLLECTOR.email).then((collector) => {
        cy.api(collector, 'POST', '/data/points', {
          orgId: '0b000000-0000-4000-8000-000000000004', metricKey: 'lpg_kg', numericValue: 40,
          reportingPeriodStart: day, reportingPeriodEnd: day,
        }).then((draft) => {
          cy.api(collector, 'PUT', `/data/points/${draft.id}`, {
            orgId: draft.orgId, metricKey: 'lpg_kg', numericValue: 45, reportingPeriodStart: day, reportingPeriodEnd: day, version: draft.version,
          })
            .then((edited) => cy.api(collector, 'POST', `/data/points/${draft.id}/submit`, { version: edited.version }))
            .then((submitted) => cy.apiToken(roles.ESG_ANALYST.email)
              .then((analyst) => cy.api(analyst, 'POST', `/data/points/${draft.id}/validate`, { version: submitted.version })))
            .then((validated) => cy.apiToken(roles.ESG_DIRECTOR.email)
              .then((director) => cy.api(director, 'POST', `/data/points/${draft.id}/approve`, { version: validated.version })))
            .then(() => {
              // In the UI, as the director.
              cy.login(roles.ESG_DIRECTOR.email);
              cy.visit('/data-hub');
              cy.get('table[aria-label="Data points"]').find(`[aria-label="Open LPG consumed ${day}"]`).click();
              cy.get('ul[aria-label="History"]').within(() => {
                cy.contains('Created by Emeka Nwosu');
                cy.contains('Edited by Emeka Nwosu').parent().should('contain', 'numeric_value');
                cy.contains('Submitted by Emeka Nwosu');
                cy.contains('Validated by Chidi Okafor');
                cy.contains('Approved by Adaeze Usman');
              });
              cy.get('[aria-label="Close"]').click();

              // The immutable audit log, filtered to this record, shows the same people and the old/new values.
              cy.visit('/admin');
              cy.contains('[role="tab"]', 'Audit log').click();
              cy.field('Record ID').type(draft.id);
              cy.contains('td', 'data_hub.data_points').should('exist');
              cy.contains('tr', 'Adaeze Usman').should('contain', 'update');
              cy.contains('tr', 'Chidi Okafor').should('contain', 'update');
              cy.contains('tr', 'Emeka Nwosu').should('exist');
              cy.contains('tr', 'insert').find('[aria-label^="Details for insert"]').click();
              cy.get('table[aria-label="Changed fields"]').should('contain', 'numeric_value');
            });
        });
      });
    });
  });
});
