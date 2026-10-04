// Journey 25 (master plan §7.3): a course is built and published → assigned → the learner completes the modules and
// the quiz → a certificate is issued → anyone verifies its code without signing in.
// Quiz scoring, expiry and codes: TrainingMathTest; QR code, period lock, revocation and access: TrainingApiTest.
import roles from '../fixtures/roles.json';

describe('Journey 25: training completed and certificate verified', () => {
  it('the training admin publishes and assigns a course; the analyst passes it; the certificate verifies publicly', () => {
    const tag = Date.now().toString(36).toUpperCase();
    const title = `Introduction to ESG ${tag}`;

    // The training admin builds the course: one text module and one question, pass mark 50%.
    cy.login(roles.TRAINING_ADMIN.email);
    cy.visit('/training');
    cy.contains('[role="tab"]', 'Courses').click();
    cy.contains('button', 'New course').click();
    cy.get('.MuiDialog-paper').within(() => {
      cy.field('Course code').type(`ESG-${tag}`);
      cy.field('Title').type(title);
      cy.field('Duration (hours)').type('1.5');
      cy.field('Pass mark (%)').clear().type('50');
      cy.field('Certificate valid for (months, optional)').type('24');
      cy.contains('button', 'Add module').click();
      cy.get('[aria-label="Title, line 1"]').type('What ESG means');
      cy.get('[aria-label="Text, line 1"]').type('Environmental, social and governance factors, measured and reported.');
      cy.get('[aria-label="Minutes, line 1"]').type('20');
      cy.contains('button', 'Add question').click();
      cy.get('[aria-label="Question, line 1"]').type('What does the G in ESG stand for?');
      cy.get('[aria-label="Options, line 1"]').type('Green{enter}Governance{enter}Growth');
    });
    cy.get('[aria-label="Correct answer, line 1"]').click();
    cy.get('[role="listbox"]').contains('[role="option"]', 'Option 2').click();
    cy.get('.MuiDialog-paper').contains('button', 'Create course').click();
    cy.get('.MuiDialog-paper').should('not.exist');
    cy.get('table[aria-label="Courses"]').contains('tr', title).should('contain', 'Draft');
    cy.get(`[aria-label="Publish ${title}"]`).click();
    cy.get('table[aria-label="Courses"]').contains('tr', title).should('contain', 'Published');

    // Assigned to the analyst.
    cy.get(`[aria-label="Assign ${title}"]`).click();
    cy.pick('People', 'Chidi Okafor', { within: '.MuiDialog-paper' });
    cy.get('body').type('{esc}');
    cy.get('.MuiDialog-paper').contains('button', 'Assign').click();
    cy.get('[aria-label="Assignment result"]').should('contain', '1 enrolled');
    cy.get('.MuiDialog-paper').contains('button', 'Close').click();

    // The analyst works through the module and passes the quiz.
    cy.login(roles.ESG_ANALYST.email);
    cy.visit('/training');
    cy.get(`[aria-label="Open ${title}"]`).click();
    cy.get('.MuiDialog-paper').within(() => {
      cy.contains('Finish every module to take the quiz.');
      cy.get('[aria-label="Mark What ESG means done"]').click();
      cy.get('[aria-label="Quiz"]').contains('label', 'Governance').click();
      cy.contains('button', 'Submit answers').click();
      cy.get('[aria-label="Quiz result"]').should('contain', 'Passed with 100%');
      cy.get('[aria-label="Quiz result"]').contains('button', /^Certificate [A-HJ-NP-Z2-9]{10}$/).invoke('text')
        .then((text) => cy.wrap(text.replace('Certificate ', '').trim()).as('code', { type: 'static' }));
      cy.contains('button', 'Close').click();
    });
    cy.get(`[aria-label="${title}"]`).should('contain', 'Passed').and('contain', 'Valid');

    // Anyone checks the code (or scans the QR code) without signing in.
    cy.clearCookies();
    cy.get('@code').then((code) => {
      cy.visit(`/verify-certificate/${code}`);
      cy.get('[aria-label="Certificate"]').should('contain', 'This certificate is valid.').and('contain', 'Chidi Okafor').and('contain', title)
        .and('contain', code);
    });
    cy.get('header.MuiAppBar-root').should('not.exist');
  });
});
