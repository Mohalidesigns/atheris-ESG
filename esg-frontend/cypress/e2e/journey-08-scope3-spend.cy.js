// Journey 8 (master plan §7.3): Scope 3 category 1, spend-based, from supplier spend with the tenant's own spend factor.
import roles from '../fixtures/roles.json';

describe('Journey 8: Scope 3 spend-based', () => {
  it('adds a 0.5 kgCO2e/USD spend factor and calculates USD 2,000 of purchased goods → 1 tCO2e', () => {
    cy.uniqueDay().then((day) => {
      const category = `purchased_goods_${day.replaceAll('-', '')}_${Date.now() % 10000}`;
      const label = category.replace(/_/g, ' ').replace(/^./, (c) => c.toUpperCase()); // derives back to `category`
      cy.login(roles.ESG_DIRECTOR.email);
      cy.carbonTab('Emission factors');
      cy.contains('button', 'Add custom factor').click();
      cy.pick('Factor type', 'Spend-based', { within: '[role="dialog"]' });
      cy.pick('Spend currency', 'USD', { within: '[role="dialog"]' });
      // A new activity (F11): its name, then its GHG scope and Scope 3 category; the unit follows the currency.
      cy.get('[role="dialog"]').within(() => cy.field('Category').click());
      cy.contains('[role="option"]', 'New activity…').click();
      cy.get('[role="dialog"]').within(() => {
        cy.field('New activity name').type(label);
        cy.field('Category key').should('have.value', category);
      });
      cy.pick('GHG scope', 'Scope 3 — value chain', { within: '[role="dialog"]' });
      cy.pick('Scope 3 category', '1. Purchased goods and services', { within: '[role="dialog"]' });
      cy.get('[role="dialog"]').within(() => {
        cy.field('Unit').should('have.value', 'kgCO2e/USD');
        cy.field('Value').type('0.5');
        cy.field('Source').type('Supplier EEIO study 2026');
        cy.field('Valid from').type('2026-01-01');
        cy.contains('button', 'Save').click();
      });
      cy.get('[role="dialog"]').should('not.exist');
      cy.get('table[aria-label="Emission factors"]').contains('tr', category).should('contain', 'Spend · USD');

      cy.carbonTab('Emission records', day.slice(0, 4));
      cy.calculateEmission({
        scope: 'Scope 3 — value chain', scope3Category: '1. Purchased goods and services', spend: true, activity: label, org: 'Lagos HQ',
        day, amount: 2000, currency: 'USD',
      });
      // USD 2,000 × 0.5 kgCO2e/USD ÷ 1000 = 1 tCO2e, from the tenant's factor.
      cy.get('[aria-label="Calculation result"]').within(() => {
        cy.contains('h3', /^1\s*tCO2e$/);
        cy.contains('2,000 USD');
        cy.contains('(your own factor)');
      });
      cy.get('[role="dialog"]').contains('button', 'Save record').click();
      cy.get('table[aria-label="Emission records"]').contains('tr', day).within(() => {
        cy.contains('scope 3 · cat 1');
        cy.contains('USD 2,000');
      });
    });
  });
});
