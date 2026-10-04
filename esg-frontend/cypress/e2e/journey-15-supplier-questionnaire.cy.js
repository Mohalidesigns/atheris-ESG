// Journey 15 (master plan §7.3): a supplier is onboarded (simplified) → a questionnaire is sent → the supplier answers
// through the link with no account → the response is scored → a corrective action is raised and closed. Scoring
// golden values: SupplierScoringTest.
import roles from '../fixtures/roles.json';

const PDF = '%PDF-1.7 Health and safety policy';

describe('Journey 15: supplier questionnaire to corrective action', () => {
  it('onboards an SME supplier, scores its answers and closes the corrective action', () => {
    const tag = Date.now().toString(36);
    const supplier = `Okoro Fabrication ${tag}`;
    const template = `SME screen ${tag}`;
    // A published simplified template (the builder itself is covered by the round-trip and API tests).
    cy.apiToken(roles.ESG_DIRECTOR.email).then((t) => cy.api(t, 'POST', '/supply-chain/templates', {
      name: template, kind: 'simplified', weightE: 40, weightS: 30, weightG: 30, questions: [
        { pillar: 'E', question: 'Do you measure your energy use?', answerType: 'yes_no', positiveAnswer: 'yes', weight: 3, evidenceRequired: false },
        { pillar: 'S', question: 'Do you have a health and safety policy?', answerType: 'yes_no', positiveAnswer: 'yes', weight: 2, evidenceRequired: true },
        { pillar: 'G', question: 'How mature is your anti-bribery programme?', answerType: 'scale', weight: 1, evidenceRequired: false },
      ],
    }).then((tp) => cy.api(t, 'POST', `/supply-chain/templates/${tp.id}/actions`, { action: 'publish', version: tp.version })));

    // Simplified onboarding: no email needed.
    cy.login(roles.ESG_DIRECTOR.email);
    cy.visit('/supply-chain');
    cy.contains('button', 'Add supplier').click();
    cy.get('.MuiDialog-paper').within(() => {
      cy.field('Supplier name').type(supplier);
      cy.field('State / region (optional)').type('Rivers');
      cy.field('Contact phone (optional)').type('+234 803 000 0000');
    });
    cy.pick('Sector (optional)', 'C · Manufacturing', { within: '.MuiDialog-paper' });
    cy.get('.MuiDialog-paper').contains('button', 'Add supplier').click();
    cy.get('.MuiDialog-paper').should('not.exist');
    cy.get('[role="region"][aria-label="Supplier details"]').should('contain', 'Simplified (SME / informal)').and('contain', 'Local supplier');
    cy.get('[aria-label="Close"]').click();

    // Send the questionnaire; the link is shown once.
    cy.contains('[role="tab"]', 'Questionnaires').click();
    cy.contains('button', 'Send questionnaire').click();
    cy.pick('Supplier', supplier, { within: '.MuiDialog-paper' });
    cy.pick('Template', template, { within: '.MuiDialog-paper' });
    cy.get('.MuiDialog-paper').within(() => {
      cy.field('Due date').type(new Date(Date.now() + 14 * 86400000).toISOString().slice(0, 10));
      cy.contains('button', 'Send').click();
    });
    cy.get('input[aria-label="Link to send the supplier"]').invoke('val').then((link) => {
      const path = new URL(link).pathname;
      cy.contains('button', 'Done').click();

      // The supplier opens the link with no account.
      cy.clearCookies();
      cy.clearLocalStorage();
      cy.visit(path);
      cy.contains(`Private link for ${supplier}`);
      cy.get('[role="radiogroup"][aria-label="Answer 1"]').contains('label', 'Yes').click();
      cy.get('[role="radiogroup"][aria-label="Answer 2"]').contains('label', 'Yes').click();
      cy.contains('label', 'Answer 3').parent().find('[role="combobox"]').click();
      cy.contains('[role="option"]', '1 (not at all)').click();
      cy.get('input[aria-label="Evidence 2"]').selectFile({ contents: Cypress.Buffer.from(PDF), fileName: 'hs-policy.pdf', mimeType: 'application/pdf' }, { force: true });
      cy.contains('hs-policy.pdf');
      cy.contains('button', 'Submit answers').click();
      cy.get('[aria-label="Submitted"]').should('contain', 'sent for review');
    });

    // Scored: E 100, S 100, G 0 → (100 × 40 + 100 × 30 + 0 × 30) ÷ 100 = 70.0, low risk; the weak answer is a gap.
    cy.login(roles.ESG_DIRECTOR.email);
    cy.visit('/supply-chain?tab=questionnaires');
    cy.get('table[aria-label="Questionnaires"]').contains('tr', supplier).should('contain', 'Awaiting review').contains('button', 'Open').click();
    cy.get('[role="region"][aria-label="Questionnaire"]').within(() => {
      cy.get('[aria-label="Scorecard"]').should('contain', '70');
      cy.contains('tr', 'anti-bribery').should('contain', 'rated 1 of 5');
      cy.contains('tr', 'health and safety policy').should('contain', 'hs-policy.pdf');
    });
    // Raise a corrective action from the gap, owned by the manager, then accept the scorecard.
    cy.get('[role="region"][aria-label="Questionnaire"]').contains('tr', 'anti-bribery').contains('button', 'Raise action').click();
    cy.get('.MuiDialog-paper').within(() => cy.field('Due date').type(new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10)));
    cy.pick('Owner (optional)', 'Amina', { within: '.MuiDialog-paper' });
    cy.get('.MuiDialog-paper').contains('button', 'Raise action').click();
    cy.get('.MuiDialog-paper').should('not.exist');
    cy.get('[role="region"][aria-label="Questionnaire"]').within(() => {
      cy.contains('button', 'Accept scorecard').click();
      cy.get('[aria-label="Questionnaire status"]').should('contain', 'Reviewed');
    });

    // The manager records the fix; someone else verifies and closes.
    cy.apiToken(roles.ESG_MANAGER.email).then((t) => cy.api(t, 'GET', '/supply-chain/actions').then((list) => {
      const a = list.find((x) => x.supplierName === supplier);
      cy.api(t, 'POST', `/supply-chain/actions/${a.id}/steps`, { action: 'implement', note: 'Anti-bribery policy adopted and staff trained', version: a.version });
    }));
    // (an API sign-in leaves its own refresh cookie, so sign the director back in)
    cy.login(roles.ESG_DIRECTOR.email);
    cy.visit('/supply-chain?tab=actions');
    cy.get('table[aria-label="Corrective actions"]').contains('tr', supplier).should('contain', 'Implemented').contains('button', 'Open').click();
    cy.get('[role="region"][aria-label="Corrective action"]').contains('button', 'Verify').click();
    cy.get('.MuiDialog-paper').within(() => {
      cy.field('Verification note (optional)').type('Policy and training records seen');
      cy.contains('button', 'Verify').click();
    });
    cy.get('table[aria-label="Corrective actions"]').contains('tr', supplier).should('contain', 'Verified').contains('button', 'Open').click();
    cy.get('[role="region"][aria-label="Corrective action"]').contains('button', 'Close').click();
    cy.get('table[aria-label="Corrective actions"]').contains('tr', supplier).should('contain', 'Closed');

    // The supplier now carries its score and risk tier.
    cy.contains('[role="tab"]', 'Suppliers').click();
    cy.get('table[aria-label="Suppliers"]').contains('tr', supplier).should('contain', '70').and('contain', 'Low risk');
  });
});
