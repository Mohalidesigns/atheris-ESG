// Journey 26 (master plan §7.3, WP-43, D43): a field collector loses the connection, keeps capturing, and everything
// syncs when the connection returns; a value another device sent in the meantime becomes a conflict they decide.
// The browser is taken offline for real (Chrome DevTools network emulation): requests fail and navigator.onLine is false.
import roles from '../fixtures/roles.json';

const cdp = (command, params) => Cypress.automation('remote:debugger:protocol', { command, params });
const network = (offline) => cy.then(() => cdp('Network.enable')
  .then(() => cdp('Network.emulateNetworkConditions', { offline, latency: 0, downloadThroughput: -1, uploadThroughput: -1 })));

const addPoint = (day, value) => {
  cy.contains('button', 'Add data point').click();
  cy.get('[role="dialog"]').within(() => {
    cy.contains("You're offline. This will be saved on this device");
    cy.field('Metric').type('Petrol');
  });
  cy.contains('[role="option"]', 'Petrol (PMS) consumed').click();
  cy.pick('Organisation', 'OML 42 Field', { within: '[role="dialog"]' });
  cy.get('[role="dialog"]').within(() => {
    cy.field('Value').type(String(value));
    cy.field('Period start').type(day);
    cy.field('Period end').type(day);
    cy.field('Notes (optional)').type('Captured offline at the flow station');
    cy.contains('button', 'Save draft').click();
  });
  cy.get('[role="dialog"]').should('not.exist');
};

describe('Journey 26: offline capture and sync', () => {
  afterEach(() => network(false));

  it('three points captured offline sync when the connection returns; a conflict is decided by the collector', () => {
    cy.uniqueDay().then((first) => {
      const days = [0, 1, 2].map((n) => new Date(Date.parse(first) + n * 86400000).toISOString().slice(0, 10));

      cy.login(roles.DATA_COLLECTOR.email);
      cy.visit('/data-hub');
      cy.contains('button', 'Add data point').should('be.enabled');

      // The connection drops. The app says so and keeps working: three readings are kept on the device.
      network(true);
      cy.get('[aria-label^="Offline records: Offline"]').should('be.visible');
      addPoint(days[0], 100);
      addPoint(days[1], 110);
      addPoint(days[2], 120);
      cy.get('[aria-label="Offline records: Offline · 3 waiting"]').should('be.visible');

      // Meanwhile another device (the analyst's tablet) sends a value for the second day, after the collector captured theirs.
      cy.apiToken(roles.ESG_ANALYST.email).then((analyst) => {
        cy.api(analyst, 'GET', '/organisations').then((orgs) => {
          const field = orgs.find((o) => o.name === 'OML 42 Field');
          cy.api(analyst, 'POST', '/data/sync', {
            deviceId: 'analyst-tablet',
            operations: [{
              opId: crypto.randomUUID(), clientId: crypto.randomUUID(), kind: 'data_point', capturedAt: new Date(Date.now() + 1000).toISOString(),
              dataPoint: { orgId: field.id, metricKey: 'petrol_litres', numericValue: 999, reportingPeriodStart: days[1], reportingPeriodEnd: days[1] },
            }],
          }).its('applied').should('eq', 1);
        });
      });

      // The connection returns: the app sends what waited without being asked.
      cy.intercept('POST', '/api/v1/data/sync').as('sync');
      network(false);
      cy.wait('@sync').then(({ request, response }) => {
        expect(request.body.operations).to.have.length(3);
        expect(response.body.data).to.include({ received: 3, applied: 2, conflicts: 1, rejected: 0 });
      });
      cy.contains('2 records synced · 1 needs your decision');

      // The collector decides: their reading replaces the tablet's.
      cy.get('[aria-label$="to check"][aria-label^="Offline records"]').click();
      cy.contains('[role="tab"][aria-selected="true"]', 'On this device');
      cy.get('[aria-label="Conflict: Petrol (PMS) consumed"]').within(() => {
        cy.contains('after you captured yours');
        cy.contains('110 L');
        cy.contains('999 L');
        cy.contains('button', 'Use mine').click();
      });
      cy.get('[aria-label="Conflict: Petrol (PMS) consumed"]').should('not.exist');
      cy.contains('Nothing waiting');
      cy.get('[aria-label^="Offline records"]').should('not.exist');
      cy.a11y('Data Hub — on this device');

      // All three are drafts in the Data Hub with the collector's values.
      cy.apiToken(roles.DATA_COLLECTOR.email).then((collector) => {
        cy.api(collector, 'GET', `/data/points?metricKey=petrol_litres&from=${days[0]}&to=${days[2]}&size=50`).then((page) => {
          const byDay = Object.fromEntries(page.content.filter((p) => days.includes(p.reportingPeriodStart))
            .map((p) => [p.reportingPeriodStart, p]));
          expect(Object.keys(byDay)).to.have.length(3);
          expect(days.map((d) => Number(byDay[d].numericValue))).to.deep.eq([100, 110, 120]);
          expect(byDay[days[0]].validationStatus).to.eq('draft');
          expect(byDay[days[0]].notes).to.eq('Captured offline at the flow station');
        });
      });
    });
  });
});
