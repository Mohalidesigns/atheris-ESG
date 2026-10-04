// Journey 24 (master plan §7.3): carbon credits recorded → retired → the same serials can't be counted twice.
// Serial guard across tenants and retirements: CarbonMarketApiTest; arithmetic: CarbonMarketMathTest.
import roles from '../fixtures/roles.json';

const YEAR = new Date().getFullYear();

describe('Journey 24: carbon credits recorded, retired, never counted twice', () => {
  it('a lot is recorded, part retired and approved; recording the same serials again is refused', () => {
    const tag = Date.now().toString(36).toUpperCase();
    const project = `Niger Delta mangroves ${tag}`;
    const batch = `VCS-${tag}-2024`;

    // The manager adds the project and records 500 credits.
    cy.login(roles.ESG_MANAGER.email);
    cy.visit('/carbon-market?tab=projects');
    cy.get('[aria-label="Integrations"]').should('contain', 'Sandbox');
    cy.contains('button', 'Add project').click();
    cy.get('.MuiDialog-paper').within(() => {
      cy.field('Project name').type(project);
      cy.field('Registry').type('Verra Registry');
      cy.field('Registry project ID').type(`VCS-${tag}`);
    });
    cy.pick('Project type', 'Mangrove restoration', { within: '.MuiDialog-paper' });
    cy.get('.MuiDialog-paper').contains('button', 'Add project').click();
    cy.get('.MuiDialog-paper').should('not.exist');
    cy.get('table[aria-label="Offset projects"]').contains('tr', project).should('contain', 'Nature-based');

    const record = (from, to) => {
      cy.contains('button', 'Record credits').click();
      cy.pick('Project', project, { within: '.MuiDialog-paper' });
      cy.get('.MuiDialog-paper').within(() => {
        cy.field('Vintage').type('2024');
        cy.field('Issuance batch (serial prefix)').type(batch);
        cy.field('First serial').type(String(from));
        cy.field('Last serial').type(String(to));
        cy.field('Acquired on').type('2025-06-01');
        cy.contains('button', 'Record credits').click();
      });
    };
    cy.contains('[role="tab"]', 'Credits').click();
    record(1, 500);
    cy.get('.MuiDialog-paper').should('not.exist');
    cy.get('table[aria-label="Credit lots"]').contains('tr', batch).should('contain', '500').and('contain', 'Not verified');

    // Retire 200 against this year's Scope 1: serials 1–200 are reserved.
    cy.get('table[aria-label="Credit lots"]').contains('tr', batch).contains('button', 'Retire').click();
    cy.get('.MuiDialog-paper').within(() => {
      cy.field('Credits (tCO2e)').type('200');
      cy.field('Retired on behalf of').type('ESG Pro Demo Corp');
      cy.contains('button', 'Request retirement').click();
    });
    cy.get('.MuiDialog-paper').should('not.exist');
    cy.get('table[aria-label="Credit lots"]').contains('tr', batch).should('contain', '200 reserved').and('contain', '300');

    // The same serials can't be recorded again (here or by any other organisation).
    record(150, 250);
    cy.get('.MuiDialog-paper').should('contain', 'already recorded in another holding');
    cy.get('.MuiDialog-paper').contains('button', 'Cancel').click();

    // The director approves with the registry's retirement reference.
    cy.login(roles.ESG_DIRECTOR.email);
    cy.visit('/carbon-market?tab=retirements');
    cy.get('table[aria-label="Retirements"]').contains('tr', batch).should('contain', '1–200').and('contain', 'Requested')
      .contains('button', 'Approve').click();
    cy.get('.MuiDialog-paper').within(() => {
      cy.field('Registry retirement reference').type(`VCU-RET-${tag}`);
      cy.contains('button', 'Approve retirement').click();
    });
    cy.get('.MuiDialog-paper').should('not.exist');
    cy.get('table[aria-label="Retirements"]').contains('tr', batch).should('contain', 'Retired').and('contain', `VCU-RET-${tag}`);

    // Shown beside the gross inventory, never netted out of it.
    cy.get('[aria-label="Offsetting"]').should('contain', `Offsetting, ${YEAR}`).and('contain', 'Reported emissions stay gross');
    cy.get('[aria-label="Carbon market overview"]').should('contain', `Retired against ${YEAR}`);
  });
});
