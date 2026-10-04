// Journey 9 (master plan §7.3): a reduction target is set → progress is shown against actual emissions.
import roles from '../fixtures/roles.json';

describe('Journey 9: reduction target', () => {
  it('measures a Scope 3 target against the recorded emissions of the following year', () => {
    // A base year of its own (2040–2089) so retries and other journeys can't add to its actuals.
    const base = 2040 + (Math.floor(Date.now() / 1000) % 50);
    cy.apiToken(roles.ESG_DIRECTOR.email).then((token) => {
      // Actual for base + 1: 51,282.051282 km × 0.195 kgCO2e/km = 10 tCO2e (CarbonApiTest.journey9TargetProgressAgainstActuals).
      cy.api(token, 'POST', '/carbon/emissions', {
        orgId: '0b000000-0000-4000-8000-000000000001', scope: 'scope_3', scope3Category: 6, calculationMethod: 'activity_based',
        sourceActivity: 'air_travel_longhaul', activityData: 51282.051282, activityUnit: 'km',
        reportingPeriodStart: `${base + 1}-03-01`, reportingPeriodEnd: `${base + 1}-03-31`,
      }).its('co2eTonnes').should('eq', 10);
    });

    cy.login(roles.ESG_DIRECTOR.email);
    cy.carbonTab('Reduction targets');
    cy.contains('button', 'Set target').click();
    const name = `Halve business travel ${base}`;
    cy.pick('Scope', 'Scope 3', { within: '[role="dialog"]' });
    cy.get('[role="dialog"]').within(() => {
      cy.field('Name').type(name);
      cy.field('Base year').type(String(base));
      cy.field('Base value').type('40');
      cy.field('Target year').type(String(base + 3));
      cy.field('Target value').type('20');
      cy.contains('label', 'Check against SBTi criteria').click();
      cy.contains('button', 'Set target').click();
    });
    cy.get('[role="dialog"]').should('not.exist');
    // Linear pathway: 40 → 20 over 3 years; expected at base + 1 = 33.33; actual 10 is below the target → achieved (150%).
    cy.get(`[aria-label="Target ${name}"]`).within(() => {
      cy.contains('Achieved');
      cy.contains(`${base + 1}: 10 actual vs 33.33 on the pathway · 150% of the reduction achieved`);
      cy.contains('16.67% a year');
      cy.get('[role="alert"]').should('exist'); // SBTi result (findings or a pass), never silently skipped
      cy.get('.recharts-line').should('have.length', 2);
    });
  });
});
