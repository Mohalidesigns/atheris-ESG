// WP-50 (D38): grounded AI against the real AI service (esg-ai). Forecasts and the Isolation Forest scan always run;
// report extraction runs only where Ollama serves the Granite model (scripts/e2e.sh sets aiLlm), never against a fake.
import roles from '../fixtures/roles.json';

const YEAR = new Date().getFullYear();
const HQ = '0b000000-0000-4000-8000-000000000001';
const OML42 = '0b000000-0000-4000-8000-000000000004';
const month = (m) => {
  const start = new Date(Date.UTC(YEAR, m - 1, 1));
  const end = new Date(Date.UTC(YEAR, m, 0));
  return [start.toISOString().slice(0, 10), end.toISOString().slice(0, 10)];
};

describe('WP-50: grounded AI', () => {
  it('forecasts emissions from recorded history and flags an isolated value, both labelled AI-generated', () => {
    // History: eight months of generator diesel at HQ, and nine months of gas at OML 42 with one implausible reading.
    cy.apiToken(roles.ESG_DIRECTOR.email).then((token) => {
      for (let m = 1; m <= 8; m++) {
        const [start, end] = month(m);
        cy.api(token, 'POST', '/carbon/emissions', { orgId: HQ, scope: 'scope_1', sourceActivity: 'diesel_generator', activityData: 400 + m * 10,
          reportingPeriodStart: start, reportingPeriodEnd: end });
      }
    });
    cy.apiToken(roles.DATA_COLLECTOR.email).then((token) => {
      for (let m = 1; m <= 9; m++) {
        const [start, end] = month(m);
        cy.api(token, 'POST', '/data/points', { orgId: OML42, metricKey: 'natural_gas_m3', numericValue: m === 7 ? 98765 : 500 + m,
          reportingPeriodStart: start, reportingPeriodEnd: end })
          .then((draft) => cy.api(token, 'PUT', `/data/points/${draft.id}/submit`, { version: draft.version }));
      }
    });

    cy.login(roles.ESG_MANAGER.email);
    cy.visit('/analytics?tab=forecasts');
    cy.get('[aria-label="AI service"]').should('not.exist');
    cy.get('form[aria-label="New forecast"]').contains('button', 'Forecast').click();
    cy.get('[aria-label="Forecast chart"]', { timeout: 30000 }).should('contain', 'AI-generated').and('contain', 'Total emissions (Scopes 1–3)');
    cy.get('[aria-label="Forecast basis"]').should('contain', 'months').and('contain', 'emission records');
    cy.get('table[aria-label="Forecast runs"]').should('contain', 'Amina Bello');

    cy.visit('/analytics?tab=anomalies');
    cy.contains('button', 'ML scan (AI)').click();
    cy.get('[role="dialog"]').contains('button', 'Run scan').click();
    cy.get('[aria-label="Scan result"]', { timeout: 30000 }).should('contain', 'Isolation Forest');
    cy.get('table[aria-label="Anomaly flags"]').contains('tr', 'Natural gas').should('contain', 'AI-generated').and('contain', '98,765');
  });

  it('reads a report with the Granite model and enters a value only when a person accepts it', () => {
    cy.env(['aiLlm']).then(({ aiLlm }) => {
      if (!aiLlm) {
        cy.log('Skipped: no Ollama Granite model on this machine (the extraction contract is covered by esg-ai tests and AiApiTest)');
        return;
      }
      cy.login(roles.ESG_MANAGER.email);
      cy.visit('/analytics?tab=extraction');
      cy.get('input[aria-label="Report PDF"]').selectFile('cypress/fixtures/sustainability-report.pdf', { force: true });
      cy.get('[aria-label="Extraction review"]', { timeout: 180000 }).should('contain', 'Completed');
      cy.get('table[aria-label="Extracted values"]').should('contain', '“Grid electricity consumption was 48,200 kWh across our offices.”');
      cy.get('table[aria-label="Extracted values"]').contains('tr', '48,200').contains('button', 'Accept').click();
      cy.get('[role="dialog"]').within(() => {
        cy.field('Period start').clear().type(`${YEAR}-01-01`);
        cy.field('Period end').clear().type(`${YEAR}-06-30`);
      });
      cy.pick('Organisation', 'Lagos HQ', { within: '[role="dialog"]' });
      cy.get('[role="dialog"]').contains('button', 'Accept as draft').click();
      cy.get('[role="dialog"]').should('not.exist');
      cy.get('table[aria-label="Extracted values"]').contains('tr', '48,200').should('contain', 'Accepted');
    });
  });
});
