// Journey 5 (master plan §7.3): Scope 2 grid electricity → tCO2e with the Nigerian grid factor; a REC lowers market-based Scope 2.
import roles from '../fixtures/roles.json';

describe('Journey 5: Scope 2 grid electricity', () => {
  it('uses the NG grid factor (0.43 kgCO2e/kWh) and shows where it came from; a REC is matched against it', () => {
    cy.uniqueDay().then((day) => {
      cy.login(roles.ESG_DIRECTOR.email);
      cy.carbonTab('Emission records', day.slice(0, 4));
      cy.calculateEmission({ scope: 'Scope 2 — purchased energy', activity: 'Grid electricity', org: 'Lagos HQ', day, amount: 10000 });
      // 10,000 kWh × 0.43 kgCO2e/kWh ÷ 1000 = 4.3 tCO2e, from the country (NG) factor with its source and version.
      cy.get('[aria-label="Calculation result"]').within(() => {
        cy.contains('h3', /^4\.3\s*tCO2e$/);
        cy.contains('0.43 kgCO2e/kWh · NG · version 1 (country factor)');
        cy.contains('FMPWH Nigeria 2024');
      });
      cy.get('[role="dialog"]').contains('button', 'Save record').click();
      cy.get('[role="dialog"]').should('not.exist');
      cy.get('table[aria-label="Emission records"]').contains('tr', day).within(() => {
        cy.contains('Grid electricity');
        cy.contains('4.3');
        cy.contains('Calculator');
      });

      // A 5 MWh solar REC for the same organisation and day covers 5,000 of the 10,000 kWh: 5,000 × 0.43 ÷ 1000 = 2.15 t avoided.
      cy.contains('[role="tab"]', 'RECs & PPAs').click();
      cy.contains('button', 'Record instrument').click();
      cy.pick('Organisation', 'Lagos HQ', { within: '[role="dialog"]' });
      cy.get('[role="dialog"]').within(() => {
        cy.field('Electricity covered').type('5');
        cy.field('Period start').type(day);
        cy.field('Period end').type(day);
        cy.field('Certificate reference').type(`REC-${day}`);
        cy.field('Supplier / registry').type('I-REC Nigeria');
        cy.contains('button', 'Record instrument').click();
      });
      cy.get('table[aria-label="Energy instruments"]').contains('tr', `REC-${day}`).within(() => {
        cy.get('td').eq(4).should('have.text', '5');
        cy.get('td').eq(5).should('have.text', '2.15');
      });
    });
  });
});
