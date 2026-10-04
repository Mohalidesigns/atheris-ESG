// Journey 23 (master plan §7.3): an IFC report is generated from tenant data, approved, shared and downloaded by the
// investor. Template content: R__ref_08 (IFC Performance Standards titles, pending SME); API detail: InvestorApiTest.
import roles from '../fixtures/roles.json';

const YEAR = new Date().getFullYear();

const step = (token, report, action) => {
  const s = report.approval.steps[report.approval.currentStep - 1];
  return cy.api(token, 'PUT', `/reports/${report.id}/approval-steps/${s.id}`, { action, version: report.version });
};

describe('Journey 23: DFI (IFC) report from tenant data', () => {
  it('is generated, approved, shared and downloaded by the investor', () => {
    const title = `IFC annual E&S performance report ${YEAR} · ${Date.now().toString(36)}`;
    // The director generates the IFC report from the Investor Portal.
    cy.login(roles.ESG_DIRECTOR.email);
    cy.visit('/investor?tab=reports');
    cy.contains('button', 'DFI report').click();
    cy.pick('Template', 'IFC annual E&S performance report', { within: '.MuiDialog-paper' });
    cy.pick('Year', String(YEAR), { within: '.MuiDialog-paper' });
    cy.get('.MuiDialog-paper').within(() => cy.field('Title (optional)').type(title));
    cy.get('.MuiDialog-paper').contains('button', 'Create report').click();
    cy.get('[aria-label="DFI report created"]').should('contain', title).find('a').invoke('attr', 'href')
      .then((href) => {
        const id = href.split('report=')[1];
        // It is a normal report filled from the tenant's data, organised by the Performance Standards.
        cy.visit(href);
        cy.contains('PS1: Assessment and management of E&S risks and impacts');
        cy.contains('PS2: Labour and working conditions');

        // Approval as for any report: the manager submits; director, compliance and board approve.
        cy.apiToken(roles.ESG_MANAGER.email).then((manager) => cy.api(manager, 'GET', `/reports/${id}`)
          .then((r) => cy.api(manager, 'POST', `/reports/${id}/approve`, { version: r.version })))
          .then((r) => cy.apiToken(roles.ESG_DIRECTOR.email).then((t) => step(t, r, 'approve')))
          .then((r) => cy.apiToken(roles.COMPLIANCE_OFFICER.email).then((t) => step(t, r, 'approve')))
          .then((r) => cy.apiToken(roles.BOARD_MEMBER.email).then((t) => step(t, r, 'approve')))
          .its('status').should('eq', 'approved');

        // Shared with investors from the portal.
        cy.login(roles.ESG_DIRECTOR.email);
        cy.visit('/investor?tab=reports');
        cy.contains('button', 'Share a report').click();
        cy.pick('Approved report', title, { within: '.MuiDialog-paper' });
        cy.get('.MuiDialog-paper').within(() => {
          cy.field('Note for investors (optional)').type('For the annual monitoring visit');
          cy.contains('button', 'Share').click();
        });
        cy.get('.MuiDialog-paper').should('not.exist');

        // The investor downloads the PDF.
        cy.login(roles.INVESTOR_VIEWER.email);
        cy.visit('/investor?tab=reports');
        cy.get('table[aria-label="Shared reports"]').contains('tr', title).should('contain', 'For the annual monitoring visit')
          .and('contain', 'v1.0').contains('button', 'PDF').click();
        cy.apiToken(roles.INVESTOR_VIEWER.email).then((investor) => cy.api(investor, 'GET', '/investor/shared-reports').then((list) => {
          const s = list.find((x) => x.reportId === id);
          cy.request({ url: `/api/v1/investor/shared-reports/${s.id}/download`, headers: { Authorization: `Bearer ${investor}` }, encoding: 'binary' })
            .then((res) => {
              expect(res.headers['content-type']).to.contain('application/pdf');
              expect(res.body.slice(0, 4)).to.eq('%PDF');
            });
        }));
      });
  });
});
