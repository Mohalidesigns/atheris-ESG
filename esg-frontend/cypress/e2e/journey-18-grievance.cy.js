// Journey 18 (master plan §7.3): a grievance is raised → escalated → resolved. Escalation happens both ways: by hand
// with a reason, and by the overdue check (run daily; an approver can run it now — the time-travelled daily run is
// proven in SocialApiTest.journey18…). Whoever resolves a grievance can't also close it.
import roles from '../fixtures/roles.json';

describe('Journey 18: grievance raised, escalated and resolved', () => {
  it('records a grievance, escalates it, resolves it and has another person close it', () => {
    const tag = Date.now().toString(36);
    const title = `Dust from haulage trucks ${tag}`;
    const old = `Local hiring quota ${tag}`;
    const lagos = (offsetDays) => new Date(Date.now() + offsetDays * 86400000).toLocaleDateString('en-CA', { timeZone: 'Africa/Lagos' });

    // An old grievance, already past its 45-day SLA, for the overdue check.
    cy.apiToken(roles.ESG_MANAGER.email).then((t) => cy.api(t, 'POST', '/social/grievances', {
      orgId: '0b000000-0000-4000-8000-000000000002', anonymous: true, channel: 'phone', category: 'employment', severity: 'low',
      title: old, description: 'Fewer local hires than agreed', receivedOn: lagos(-60),
    })).its('overdue').should('eq', true);

    cy.login(roles.ESG_DIRECTOR.email);
    cy.visit('/social?tab=grievances');
    cy.contains('button', 'Record grievance').click();
    cy.pick('Organisation', 'Port Harcourt Plant', { within: '.MuiDialog-paper' });
    cy.get('.MuiDialog-paper').within(() => {
      cy.field('Community (optional)').type('Rumuokoro');
      cy.field('Complainant name').type('Chief Amadi');
      cy.field('Complainant contact (optional)').type('+2348030000000');
      cy.field('Title').type(title);
      cy.field('Description').type('Trucks raise dust near the primary school every morning.');
    });
    cy.pick('Channel', 'Community meeting', { within: '.MuiDialog-paper' });
    cy.pick('Category', 'Environment', { within: '.MuiDialog-paper' });
    cy.pick('Severity', 'Low', { within: '.MuiDialog-paper' });
    cy.pick('Assigned to (optional)', 'Amina', { within: '.MuiDialog-paper' });
    cy.get('.MuiDialog-paper').contains('button', 'Record grievance').click();
    cy.get('.MuiDialog-paper').should('not.exist');

    cy.get('[role="region"][aria-label="Grievance details"]').within(() => {
      cy.contains(/GRV-\d{4}-\d{4}/);
      cy.contains(`due ${lagos(45)}`);
      cy.contains('Chief Amadi');
      cy.contains('button', 'Acknowledge').click();
      cy.get('[aria-label="Grievance status"]').should('contain', 'Acknowledged');
      cy.contains('button', 'Escalate').click();
    });
    cy.get('.MuiDialog-paper').within(() => {
      cy.field('Reason').type('The school head wrote to the commissioner');
      cy.contains('button', 'Escalate').click();
    });
    cy.get('[aria-label="Escalation level"]').should('contain', 'level 1');
    cy.contains(`due ${lagos(7)}`);

    // Resolve, then the closing step is blocked for the resolver (segregation of duties).
    cy.contains('[role="region"] button', 'Resolve').click();
    cy.get('.MuiDialog-paper').within(() => {
      cy.field('Resolution').type('Water bowser on the access road twice a day; 30 km/h limit agreed with the haulier');
      cy.contains('button', 'Resolve').click();
    });
    cy.get('[aria-label="Grievance status"]').should('contain', 'Resolved');
    cy.get('[aria-label^="Close grievance: You resolved this grievance"]').should('exist');
    cy.get('table[aria-label="Grievance history"]').should('contain', 'escalate').and('contain', 'resolve');
    cy.get('[aria-label="Close"]').click();

    // The overdue check escalates the old grievance.
    cy.contains('button', 'Check overdue now').click();
    cy.contains(/Overdue grievances escalated: [1-9]/);
    cy.get('table[aria-label="Grievances"]').contains('tr', old).should('contain', 'L1');

    // Another approver closes the resolved grievance with the complainant's view.
    cy.get('table[aria-label="Grievances"]').contains('tr', title).invoke('text').then((row) => {
      const reference = row.match(/GRV-\d{4}-\d{4}/)[0];
      cy.apiToken(roles.TENANT_ADMIN.email).then((t) => cy.api(t, 'GET', '/social/grievances').then((list) => {
        const g = list.find((x) => x.reference === reference);
        cy.api(t, 'GET', `/social/grievances/${g.id}`).then((full) =>
          cy.api(t, 'POST', `/social/grievances/${g.id}/actions`, { action: 'close', complainantSatisfied: 'yes', version: full.version }));
      }));
      cy.reload();
      cy.get('table[aria-label="Grievances"]').contains('tr', reference).should('contain', 'Closed');
    });
  });
});
