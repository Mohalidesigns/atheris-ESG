// Journey 4 (master plan §7.3): bulk Excel import with column mapping; errors reported per row.
import roles from '../fixtures/roles.json';

describe('Journey 4: bulk Excel import', () => {
  it('maps the columns, imports the good rows as drafts and lists each bad row', () => {
    cy.uniqueDay().then((day) => {
      cy.login(roles.DATA_COLLECTOR.email);
      cy.visit('/data-hub');
      cy.contains('[role="tab"]', 'Import').click();
      cy.get('[data-testid="import-file"]').selectFile('cypress/fixtures/oml42-q3.xlsx', { force: true });

      // Suggested mapping from the headers: KPI → metric, Site → organisation, Amount → value, Note → sub-category.
      cy.contains('oml42-q3.xlsx: 5 rows');
      cy.contains('label', 'Metric *').parent().find('[role="combobox"]').should('have.text', 'KPI');
      cy.contains('label', 'Organisation').parent().find('[role="combobox"]').should('have.text', 'Site');
      cy.contains('label', 'Value *').parent().find('[role="combobox"]').should('have.text', 'Amount');
      cy.get('table[aria-label="File preview"]').should('contain', 'OML 42 Field');

      // The file has no period columns: one period for every row.
      cy.contains('button', 'Import 5 rows').click();
      cy.contains('Map a column or enter a start date for every row');
      cy.field('Period start for every row').type(day);
      cy.field('Period end for every row').type(day);
      cy.contains('button', 'Import 5 rows').click();

      cy.get('[aria-label="Import result"]', { timeout: 30000 }).within(() => {
        cy.contains('2 of 5 rows imported as drafts; 3 need fixing', { timeout: 30000 });
        cy.get('table[aria-label="Row errors"]').within(() => {
          cy.contains('tr', "Unknown metric 'unknown_metric'").should('contain', '4').and('contain', 'KPI');
          cy.contains('tr', 'access to this organisation').should('contain', '5');
          cy.contains('tr', 'whole number').should('contain', '6').and('contain', 'Amount');
        });
        cy.contains('button', 'Show the imported drafts').click();
      });

      // Back on the data list, filtered to this import: two drafts with values from the sheet.
      cy.contains('From one import');
      cy.get('table[aria-label="Data points"]').within(() => {
        cy.get('tbody tr').should('have.length', 2);
        cy.contains('tr', 'Diesel consumed').should('contain', '300').and('contain', 'Draft');
        cy.contains('tr', 'Electricity consumed').should('contain', '1,200');
      });
    });
  });
});
