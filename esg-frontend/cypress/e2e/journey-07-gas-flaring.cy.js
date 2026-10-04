// Journey 7 (master plan §7.3): a gas flaring record → emissions calculated from volume, composition and efficiency.
import roles from '../fixtures/roles.json';

const COMPOSITION = { 'Methane (CH4)': '85', 'Ethane (C2H6)': '7', 'Propane (C3H8)': '3', 'Butane (C4H10)': '1', 'Pentane+ (C5+)': '0.5', 'Carbon dioxide (CO2)': '2', 'Nitrogen (N2)': '1.5' };

describe('Journey 7: gas flaring', () => {
  it('records 1,000 Mcf with its gas analysis → 69.828 tCO2e (AR6), feeds Scope 1 and labels the satellite check Sandbox', () => {
    cy.uniqueDay().then((day) => {
      cy.login(roles.ESG_DIRECTOR.email);
      cy.carbonTab('Gas flaring', day.slice(0, 4));
      cy.contains('button', 'Record flaring').click();
      cy.pick('Facility', 'OML 42 Field', { within: '[role="dialog"]' });
      cy.pick('Reason', 'Routine', { within: '[role="dialog"]' });
      cy.get('[role="dialog"]').within(() => {
        cy.field('Volume flared').type('1000');
        cy.field('Period start').type(day);
        cy.field('Period end').type(day);
        Object.entries(COMPOSITION).forEach(([label, value]) => cy.field(label).type(value));
        cy.contains('button', 'Calculate and save').click();
      });
      cy.get('[role="dialog"]').should('not.exist');
      // Golden value: CarbonCalculatorsTest.flaringByCompositionIncludingInertCo2 (API Compendium stoichiometry, 98% efficiency).
      cy.get('table[aria-label="Flaring records"]').contains('tr', day).within(() => {
        cy.contains('69.828');
        cy.contains('Not checked');
        cy.get('[aria-label^="Satellite check"]').click();
        cy.contains('Sandbox: not connected');
      });

      // The flaring record created its Scope 1 emission record.
      cy.contains('[role="tab"]', 'Emission records').click();
      cy.get('table[aria-label="Emission records"]').contains('tr', day).within(() => {
        cy.contains('Gas flaring');
        cy.contains('69.8281');
        cy.contains('Flaring');
      });
    });
  });
});
