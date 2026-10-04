// Journey 13 (master plan §7.3): report created from a template → auto-populated → versioned → approved → PDF exported.
import roles from '../fixtures/roles.json';

const OML42 = '0b000000-0000-4000-8000-000000000004';

const openReport = (id) => cy.visit(`/reporting?report=${id}`);
const approve = (email) => {
  cy.login(email);
  cy.get('@reportId').then(openReport);
  cy.contains('[role="tab"]', 'Approval').click();
  cy.get('[aria-label="Approval"]').contains('button', 'Approve').click();
  cy.logout();
};

describe('Journey 13: report from template to approved PDF', () => {
  it('fills from approved data, keeps versions, needs three approvals and exports the approved version', () => {
    cy.uniqueDay().then((day) => {
      // Approved electricity for the report's period (collector → analyst → director).
      cy.apiToken(roles.DATA_COLLECTOR.email).then((collector) => cy.api(collector, 'POST', '/data/points', {
        orgId: OML42, metricKey: 'electricity_kwh', numericValue: 8420, reportingPeriodStart: day, reportingPeriodEnd: day,
      }).then((p) => cy.api(collector, 'PUT', `/data/points/${p.id}/submit`, { version: p.version }))).then((p) => {
        cy.apiToken(roles.ESG_ANALYST.email).then((analyst) => cy.api(analyst, 'PUT', `/data/points/${p.id}/validate`, { version: p.version }))
          .then((v) => cy.apiToken(roles.ESG_DIRECTOR.email).then((director) => cy.api(director, 'PUT', `/data/points/${p.id}/approve`, { version: v.version })));
      });

      // The manager creates the report from the GRI template for that period.
      const title = `Sustainability report ${day}`;
      cy.login(roles.ESG_MANAGER.email);
      cy.visit('/reporting');
      cy.contains('button', 'New report').click();
      cy.pick('Template', 'GRI sustainability report', { within: '[role="dialog"]' });
      cy.get('[role="dialog"]').within(() => {
        cy.get('[aria-label="Template preview"]').should('contain', 'Filled from approved data');
        cy.field('Report title').type(title);
        cy.field('Reporting period start').type(day);
        cy.field('Reporting period end').type(day);
        cy.contains('button', 'Create report').click();
      });
      cy.get('[aria-label="Report status"]').should('contain', 'Draft');
      cy.get('[aria-label="Report version"]').should('contain', 'v0.1');
      cy.location('search').then((s) => cy.wrap(new URLSearchParams(s).get('report')).as('reportId'));

      // Auto-populated from the approved data.
      cy.contains('[role="tab"]', 'Data').click();
      cy.get('table[aria-label="Data snapshot"]').contains('tr', 'metric:electricity_kwh').should('contain', '8,420 kWh');

      // Edit a narrative → v0.2; submit.
      cy.contains('[role="tab"]', 'Content').click();
      cy.get('[aria-label="Section 1"]').within(() => cy.field('Text').clear().type('This report covers our Nigerian operations.'));
      cy.contains('button', 'Save sections').click();
      cy.get('[aria-label="Report version"]').should('contain', 'v0.2');
      cy.contains('button', 'Submit for approval').click();
      cy.get('[aria-label="Report status"]').should('contain', 'In review');
      cy.logout();

      // Three approvals: ESG Director, Compliance, Board.
      approve(roles.ESG_DIRECTOR.email);
      approve(roles.COMPLIANCE_OFFICER.email);
      approve(roles.BOARD_MEMBER.email);

      // Approved as v1.0 and exported to PDF from that version.
      cy.login(roles.ESG_MANAGER.email);
      cy.get('@reportId').then(openReport);
      cy.get('[aria-label="Report status"]').should('contain', 'Approved');
      cy.get('[aria-label="Report version"]').should('contain', 'v1.0');
      cy.contains('[role="tab"]', 'Versions').click();
      cy.get('table[aria-label="Versions"]').should('contain', 'Approved').and('contain', 'Saved').and('contain', 'Created from template');
      cy.contains('button', 'Export PDF').click();
      cy.contains('PDF generated from the approved version.');
      cy.get('table[aria-label="Exports"]').contains('tr', 'PDF').should('contain', 'v1.0').and('contain', 'done');

      // The file is a real PDF.
      cy.get('@reportId').then((id) => cy.apiToken(roles.ESG_DIRECTOR.email).then((token) => cy.api(token, 'GET', `/reports/${id}`)).then((r) => {
        const job = r.exports.find((e) => e.format === 'pdf');
        cy.apiToken(roles.ESG_DIRECTOR.email).then((token) => cy.request({
          url: `/api/v1/files/${job.fileId}/content`, headers: { Authorization: `Bearer ${token}` }, encoding: 'binary',
        })).then((res) => {
          expect(res.headers['content-type']).to.contain('application/pdf');
          expect(res.body.slice(0, 5)).to.eq('%PDF-');
        });
      }));
    });
  });
});
