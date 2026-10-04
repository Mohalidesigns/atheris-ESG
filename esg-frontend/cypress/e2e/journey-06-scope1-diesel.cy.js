// Journey 6 (master plan §7.3): diesel generator (Scope 1) → tCO2e; edit recalculates; the director verifies.
import roles from '../fixtures/roles.json';

describe('Journey 6: Scope 1 diesel generator', () => {
  it('calculates 1,000 L at 2.68 kgCO2e/L, recalculates on edit and is verified', () => {
    cy.uniqueDay().then((day) => {
      cy.login(roles.ESG_DIRECTOR.email);
      cy.carbonTab('Emission records', day.slice(0, 4));
      cy.calculateEmission({ activity: 'Diesel (AGO) in generators', org: 'Lagos HQ', day, amount: 1000 });
      // The NG factor is CO2e-only (published as CO2e), so it is applied as published: 1000 × 2.68 ÷ 1000 = 2.68 t.
      cy.get('[aria-label="Calculation result"]').within(() => {
        cy.contains('h3', /^2\.68\s*tCO2e$/);
        cy.contains('2.68 kgCO2e/litre · NG');
        cy.contains('as published');
      });
      cy.get('[role="dialog"]').contains('button', 'Save record').click();
      cy.get('table[aria-label="Emission records"]').contains('tr', day).should('contain', '2.68').and('contain', '1,000 litre');

      // Edit: 1,500 L → 4.02 t.
      cy.get(`[aria-label="Edit diesel_generator ${day}"]`).click();
      cy.get('[role="dialog"]').within(() => {
        cy.contains('button', 'Next').click();
        cy.contains('button', 'Next').click();
        cy.field('Activity data').clear().type('1500');
        cy.contains('button', 'Calculate').click();
        cy.get('[aria-label="Calculation result"]').contains('h3', /^4\.02\s*tCO2e$/);
        cy.contains('button', 'Save changes').click();
      });
      cy.get('[role="dialog"]').should('not.exist');
      cy.get('table[aria-label="Emission records"]').contains('tr', day).should('contain', '4.02').and('contain', '1,500 litre');

      cy.get(`[aria-label="Verify diesel_generator ${day}"]`).click();
      cy.get('table[aria-label="Emission records"]').contains('tr', day).should('contain', 'Verified');
    });
  });
});
