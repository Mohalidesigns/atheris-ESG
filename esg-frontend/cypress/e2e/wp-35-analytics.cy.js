// WP-35 Analytics (D34): an unusual approved value is flagged, explained by the manager and accepted by the director;
// a KPI is built over the site's approved data; a cited benchmark dataset places the site in a quartile band; the
// maturity index shows its evidence. Statistics golden values: AnalyticsMathTest; API detail: AnalyticsApiTest.
import roles from '../fixtures/roles.json';

const OML42 = '0b000000-0000-4000-8000-000000000004';
const YEAR = new Date().getFullYear();
// Monthly water use, January to August: steady around 100 m3, so none of these is unusual on approval.
const HISTORY = [100, 102, 98, 101, 99, 100, 103, 97];
const month = (m) => {
  const start = `${YEAR}-${String(m).padStart(2, '0')}-01`;
  const end = new Date(Date.UTC(YEAR, m, 0)).toISOString().slice(0, 10);
  return { start, end };
};

/** Collector enters and submits, analyst validates, director approves (journey 3 covers the same steps in the UI). */
const approved = (site, m, value) => cy.apiToken(roles.DATA_COLLECTOR.email).then((collector) => cy.api(collector, 'POST', '/data/points', {
  orgId: site, metricKey: 'water_consumption_m3', numericValue: value, reportingPeriodStart: month(m).start, reportingPeriodEnd: month(m).end,
}).then((p) => cy.api(collector, 'PUT', `/data/points/${p.id}/submit`, { version: p.version })))
  .then((p) => cy.apiToken(roles.ESG_ANALYST.email).then((analyst) => cy.api(analyst, 'PUT', `/data/points/${p.id}/validate`, { version: p.version })))
  .then((p) => cy.apiToken(roles.ESG_DIRECTOR.email).then((director) => cy.api(director, 'PUT', `/data/points/${p.id}/approve`, { version: p.version })));

describe('WP-35: anomaly flag explained and accepted; KPI, benchmark and maturity from real data', () => {
  it('flags an unusual value, takes it through explanation and acceptance, and builds analytics on the site', () => {
    const tag = Date.now().toString(36);
    const siteName = `E2E Site water ${tag}`;
    cy.apiToken(roles.TENANT_ADMIN.email).then((admin) => cy.api(admin, 'POST', '/organisations', {
      name: siteName, parentOrgId: OML42, orgType: 'facility', countryCode: 'NG',
    })).then((site) => {
      HISTORY.forEach((v, i) => approved(site.id, i + 1, v));
      // September: 160 m3, far outside 97–103. Approving it raises the flag.
      approved(site.id, 9, 160);

      // The manager (Lagos HQ and below) sees the flag and explains it.
      cy.login(roles.ESG_MANAGER.email);
      cy.visit('/analytics');
      cy.get('table[aria-label="Anomaly flags"]').contains('tr', siteName).should('contain', 'Open').and('contain', 'standard deviations')
        .contains('button', 'Open').click();
      cy.get('[role="region"][aria-label="Anomaly"]').within(() => {
        cy.get('[aria-label="Why it was flagged"]').should('contain', '160');
        cy.contains('compared with 8 earlier periods');
        cy.contains('button', 'Accept explanation').should('not.exist');
        cy.contains('button', 'Explain').click();
      });
      cy.get('.MuiDialog-paper').within(() => {
        cy.field('Explanation').type('Cooling tower leak found on 12 September and repaired the same week');
        cy.contains('button', 'Send for review').click();
      });
      cy.get('.MuiDialog-paper').should('not.exist');
      cy.get('table[aria-label="Anomaly flags"]').contains('tr', siteName).should('contain', 'Explained');

      // A KPI over the site's water: January–September adds up to 960 m3.
      cy.contains('[role="tab"]', 'KPIs').click();
      cy.contains('button', 'New KPI').click();
      cy.get('.MuiDialog-paper').within(() => {
        cy.field('Name').type(`Site water ${tag}`);
        cy.field('Code (for reports)').type(`site_water_${tag}`);
        cy.field('Unit').type('m3');
        cy.field('Target value (optional)').type('900');
        cy.field('Target year (optional)').type(String(YEAR + 1));
      });
      cy.pick('Organisation', siteName, { within: '.MuiDialog-paper' });
      cy.pick('Metric', 'Water consumption (m3)', { within: '.MuiDialog-paper' });
      cy.get('.MuiDialog-paper').contains('button', 'Create KPI').click();
      cy.get('.MuiDialog-paper').should('not.exist');
      cy.get(`[aria-label="KPI Site water ${tag}"]`).should('contain', '960').and('contain', `in ${YEAR}`).and('contain', `Target 900 by ${YEAR + 1}`);

      // The director accepts the explanation (someone else wrote it).
      cy.login(roles.ESG_DIRECTOR.email);
      cy.visit('/analytics?tab=anomalies');
      cy.get('table[aria-label="Anomaly flags"]').contains('tr', siteName).contains('button', 'Open').click();
      cy.get('[role="region"][aria-label="Anomaly"]').within(() => {
        cy.get('[aria-label="Explanation"]').should('contain', 'Cooling tower leak');
        cy.contains('button', 'Accept explanation').click();
      });
      cy.get('.MuiDialog-paper').within(() => {
        cy.field('Review note (optional)').type('Maintenance log seen');
        cy.contains('button', 'Accept explanation').click();
      });
      cy.get('table[aria-label="Anomaly flags"]').contains('tr', siteName).should('contain', 'Accepted');

      // A cited benchmark dataset: the site's 960 m3 sits between P25 and the median (lower is better).
      cy.contains('[role="tab"]', 'Benchmarks').click();
      cy.contains('button', 'Import dataset').click();
      cy.get('.MuiDialog-paper').within(() => {
        cy.field('Dataset name').type(`Water survey ${tag}`);
        cy.field('Publisher').type('Manufacturers Association of Nigeria');
        cy.field('Publication year').type(String(YEAR));
        cy.get('[data-testid="evidence-input"]').selectFile({ contents: Cypress.Buffer.from('%PDF-1.7 survey'), fileName: 'water-survey.pdf',
          mimeType: 'application/pdf' }, { force: true });
        cy.contains('water-survey.pdf');
        cy.get('[aria-label="Metric, line 1"]').click();
      });
      cy.get('[role="listbox"]').contains('[role="option"]', 'Water consumption (m3)').click();
      cy.get('.MuiDialog-paper').within(() => {
        cy.get('input[aria-label="Data year, line 1"]').type(String(YEAR));
        cy.get('input[aria-label="P25, line 1"]').type('500');
        cy.get('input[aria-label="Median, line 1"]').type('1000');
        cy.get('input[aria-label="P75, line 1"]').type('1500');
        cy.contains('button', 'Save dataset').click();
      });
      cy.get('.MuiDialog-paper').should('not.exist');
      cy.get(`[aria-label="Benchmark Water consumption — Water survey ${tag}"]`).should('contain', 'Manufacturers Association of Nigeria')
        .contains('tr', siteName).should('contain', 'Second quarter');

      // The maturity index lists what counted, including the KPI with a target.
      cy.contains('[role="tab"]', 'Maturity').click();
      cy.get('[aria-label="Maturity score"]').should('contain', 'of 5');
      cy.get('[aria-label="Dimension Risk & strategy"]').should('contain', 'A KPI with a target tracked');

      // The dashboard shows the analytics card.
      cy.visit('/');
      cy.get('[aria-label="Analytics summary"]').should('contain', 'ESG maturity').and('contain', 'KPIs on track');
    });
  });
});
