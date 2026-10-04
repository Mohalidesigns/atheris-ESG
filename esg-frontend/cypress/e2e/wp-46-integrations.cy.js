// WP-46 (D44): the integration line — a client requests a connector and the platform team activates it; an API key
// sends readings; a webhook receives signed events (test ping, then a real approval) on a local receiver.
import roles from '../fixtures/roles.json';

describe('WP-46: connectors on request, API keys and webhooks', () => {
  it('a connector is requested and activated; a gateway sends data with a key; approvals reach the webhook signed', () => {
    const tag = Date.now().toString(36);

    // 1. The tenant administrator asks for the Sage connector.
    cy.login(roles.TENANT_ADMIN.email);
    cy.visit('/admin?tab=integrations');
    cy.contains('[role="tab"]', 'Connectors').click();
    cy.get('[aria-label="REST API and IoT push (API key)"]').should('contain', 'Available now');
    cy.get('[aria-label="Sage (50, 300, Intacct)"]').should('contain', 'On request');
    cy.get('[aria-label="Request Sage (50, 300, Intacct)"]').click();
    cy.get('[role="dialog"]').within(() => {
      cy.field('Your system').type(`Sage 300 2024, on-premise ${tag}`);
      cy.contains('label', 'Procurement spend (Scope 3)').click();
      cy.field('Contact person').type('Ifeoma Finance');
      cy.field('Contact email').type('ifeoma@example.org');
      cy.contains('button', 'Send request').click();
    });
    cy.get('table[aria-label="Integration requests"]').contains('tr', 'Sage').should('contain', 'Requested')
      .find('td').first().invoke('text').as('reference', { type: 'static' });
    cy.a11y('Administration — integrations');

    // 2. The platform team activates it.
    cy.login(roles.SUPER_ADMIN.email);
    cy.visit('/admin?tab=integration-requests');
    cy.get('@reference').then((reference) => {
      cy.get(`[aria-label="Update ${reference}"]`).click();
      cy.pick('New status', 'Active (connector live)', { within: '[role="dialog"]' });
      cy.get('[role="dialog"]').within(() => {
        cy.field('Note for the client').type('Live from the November close');
        cy.contains('button', 'Update').click();
      });
      cy.get('table[aria-label="Integration requests from clients"]').contains('tr', reference).should('contain', 'Active');

      // 3. The client sees it live.
      cy.login(roles.TENANT_ADMIN.email);
      cy.visit('/admin?tab=integrations');
      cy.contains('[role="tab"]', 'Connectors').click();
      cy.get('[aria-label="Sage (50, 300, Intacct)"]').should('contain', `Active · ${reference}`);
    });

    // 4. An API key for a flow-station gateway: it sends a reading, which arrives as a draft.
    cy.contains('[role="tab"]', 'API keys').click();
    cy.contains('button', 'Create API key').click();
    cy.get('[role="dialog"]').within(() => {
      cy.field('Name').type(`Gateway ${tag}`);
      cy.contains('label', 'Send data points').click();
      cy.contains('label', 'Read the organisation structure').click();
      cy.contains('button', 'Create key').click();
    });
    cy.get('[aria-label="API key"]').invoke('text').then((key) => {
      cy.contains('button', 'Done').click();
      cy.get('table[aria-label="API keys"]').should('contain', `Gateway ${tag}`).and('not.contain', key);
      cy.request({ url: '/api/v1/organisations', headers: { 'X-API-Key': key } }).its('body.data').then((orgs) => {
        const field = orgs.find((o) => o.name === 'OML 42 Field');
        cy.uniqueDay().then((day) => {
          cy.request({ method: 'POST', url: '/api/v1/data/points', headers: { 'X-API-Key': key },
            body: { orgId: field.id, metricKey: 'lpg_kg', numericValue: 64, reportingPeriodStart: day, reportingPeriodEnd: day } })
            .its('body.data').should('include', { validationStatus: 'draft', metricKey: 'lpg_kg' });
        });
      });
      // Nothing beyond its permissions.
      cy.request({ url: '/api/v1/admin/users', headers: { 'X-API-Key': key }, failOnStatusCode: false }).its('status').should('eq', 403);
    });

    // 5. A webhook to a receiver: the test ping arrives, then a real approval, both signed with the secret shown once.
    cy.task('startWebhookReceiver').then((url) => {
      cy.contains('[role="tab"]', 'Webhooks').click();
      cy.contains('button', 'Add webhook').click();
      cy.get('[role="dialog"]').within(() => {
        cy.field('Name').type(`ERP bridge ${tag}`);
        cy.field('Endpoint URL').type(url);
        cy.contains('label', 'A data point was approved').click();
        cy.contains('button', 'Add webhook').click();
      });
      cy.get('[aria-label="Signing secret"]').invoke('text').then((secret) => {
        cy.contains('button', 'Done').click();
        cy.get('table[aria-label="Webhooks"]').contains('tr', `ERP bridge ${tag}`).contains('button', 'Send test').click();
        cy.contains(`ERP bridge ${tag}: test event delivered (HTTP 200)`);

        // A data point is approved (collector → analyst → director); the dispatcher sends the event within seconds.
        cy.uniqueDay().then((day) => {
          const d = new Date(Date.parse(day) + 3 * 86400000).toISOString().slice(0, 10);
          cy.apiToken(roles.DATA_COLLECTOR.email).then((collector) => cy.api(collector, 'GET', '/organisations').then((orgs) => {
            const field = orgs.find((o) => o.name === 'OML 42 Field');
            cy.api(collector, 'POST', '/data/points', { orgId: field.id, metricKey: 'lpg_kg', numericValue: 81, reportingPeriodStart: d, reportingPeriodEnd: d })
              .then((p) => cy.api(collector, 'PUT', `/data/points/${p.id}/submit`, { version: p.version }));
          })).then((p) => cy.apiToken(roles.ESG_ANALYST.email).then((analyst) => cy.api(analyst, 'PUT', `/data/points/${p.id}/validate`, { version: p.version })))
            .then((p) => cy.apiToken(roles.ESG_DIRECTOR.email).then((director) => cy.api(director, 'PUT', `/data/points/${p.id}/approve`, { version: p.version })))
            .then((approved) => {
              const find = (attempt = 0) => cy.task('webhooksReceived').then((all) => {
                const hit = all.find((w) => w.event === 'data_point.approved' && JSON.parse(w.body).data.id === approved.id);
                if (hit || attempt > 20) return hit;
                return cy.wait(1000).then(() => find(attempt + 1));
              });
              find().then((hit) => {
                expect(hit, 'the approval reached the webhook').to.exist;
                const [t, v1] = hit.signature.split(',');
                cy.task('hmac', { secret, message: `${t.slice(2)}.${hit.body}` }).then((expected) => expect(v1).to.eq(`v1=${expected}`));
                expect(JSON.parse(hit.body).data).to.include({ metricKey: 'lpg_kg', validationStatus: 'approved' });
              });
            });
        });
      });
    });
    cy.get('table[aria-label="Webhooks"]').contains('tr', `ERP bridge ${tag}`).contains('button', 'Deliveries').click();
    cy.get('table[aria-label="Deliveries"]').should('contain', 'ping').and('contain', 'Delivered');
  });
});
