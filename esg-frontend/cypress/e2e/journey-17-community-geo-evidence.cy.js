// Journey 17 (master plan §7.3): a community project with geo-tagged evidence. The project is registered with its
// location, spend is recorded against the budget, and site photos are placed where they were taken — one near the
// site, one far away (flagged for the reviewer).
import roles from '../fixtures/roles.json';

// The 8-byte PNG signature: enough for the upload's type check (content matches the extension).
const PNG = Uint8Array.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

describe('Journey 17: community project with geo-tagged evidence', () => {
  it('registers a host-community project, records spend and places photos on the map', () => {
    const name = `Rumuokoro water scheme ${Date.now().toString(36)}`;
    const today = new Date().toLocaleDateString('en-CA', { timeZone: 'Africa/Lagos' });
    cy.login(roles.ESG_DIRECTOR.email);
    cy.visit('/social?tab=community');
    cy.contains('button', 'Add project').click();
    cy.pick('Organisation', 'Port Harcourt Plant', { within: '.MuiDialog-paper' });
    cy.pick('Category', 'Water and sanitation', { within: '.MuiDialog-paper' });
    cy.pick('Status', 'Active', { within: '.MuiDialog-paper' });
    cy.get('.MuiDialog-paper').within(() => {
      cy.field('Project name').type(name);
      cy.field('Community (optional)').type('Rumuokoro');
      cy.field('State (optional)').type('Rivers');
      cy.field('Latitude (optional)').type('4.8156');
      cy.contains('button', 'Add project').click();
    });
    // Coordinates come in pairs (the server's rule, applied in the form).
    cy.get('.MuiDialog-paper').should('contain', 'give both latitude and longitude, or neither');
    cy.get('.MuiDialog-paper').within(() => {
      cy.field('Longitude (optional)').type('7.0498');
      cy.field('Budget (optional)').type('10000000');
      cy.field('Planned beneficiaries (optional)').type('3000');
      cy.contains('label', 'PIA 2021 host community').click();
    });
    cy.get('.MuiDialog-paper').contains('button', 'Add project').click();
    cy.get('.MuiDialog-paper').should('not.exist');

    cy.get('[role="region"][aria-label="Project details"]').within(() => {
      cy.contains('PIA 2021 host community');
      // Spend against the budget.
      cy.get('form[aria-label="Record expenditure"]').within(() => {
        cy.field('Amount (NGN)').type('2500000');
        cy.field('What for').type('Borehole drilling');
        cy.contains('button', 'Add').click();
      });
      cy.get('[aria-label="Budget used"]').should('contain', '25%');
      cy.get('table[aria-label="Expenditure"]').should('contain', 'Borehole drilling');

      // A photo taken at the site.
      cy.get('form[aria-label="Add geo-tagged evidence"]').within(() => {
        cy.get('input[aria-label="Evidence photo"]').selectFile({ contents: Cypress.Buffer.from(PNG), fileName: 'borehole-head.png', mimeType: 'image/png' }, { force: true });
        cy.field('Latitude').type('4.8201');
        cy.field('Longitude').type('7.0512');
        cy.field('Caption (optional)').type('Borehole head');
        cy.contains('button', 'Add evidence').click();
      });
      cy.get('ul[aria-label="Evidence"]').should('contain', 'borehole-head.png').and('contain', `Taken ${today}`)
        .and('contain', 'km from the project location').and('not.contain', 'Far from the site');

      // One taken ~500 km away (Lagos) is flagged.
      cy.get('form[aria-label="Add geo-tagged evidence"]').within(() => {
        cy.get('input[aria-label="Evidence photo"]').selectFile({ contents: Cypress.Buffer.from(PNG), fileName: 'elsewhere.png', mimeType: 'image/png' }, { force: true });
        cy.field('Latitude').type('6.5244');
        cy.field('Longitude').type('3.3792');
        cy.contains('button', 'Add evidence').click();
      });
      cy.get('ul[aria-label="Evidence"]').contains('li', 'elsewhere.png').should('contain', 'Far from the site');
    });

    // The project and its spend show in the list and the year's community investment.
    cy.get('[aria-label="Close"]').click();
    cy.get('table[aria-label="Community projects"]').contains('tr', name).should('contain', '25%');
    cy.get('[aria-label="Social overview"]').should('contain', 'Community investment');
  });
});
