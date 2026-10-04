// Journey 21 (master plan §7.3): board composition → governance code checker results.
import roles from '../fixtures/roles.json';

describe('Journey 21: board composition and governance code checks', () => {
  it('the register drives independence, diversity and CAMA / NCCG / King IV results', () => {
    const tag = Date.now().toString(36);
    const add = (token, body) => cy.api(token, 'POST', '/governance/board-members', { skills: ['Finance'], committees: [], ...body });
    cy.apiToken(roles.ESG_DIRECTOR.email).then((t) => {
      add(t, { name: `Chair ${tag}`, boardRole: 'chair', independent: true, executive: false, gender: 'female', appointedOn: '2020-01-01' });
      add(t, { name: `MD ${tag}`, boardRole: 'md_ceo', independent: false, executive: true, gender: 'male', appointedOn: '2021-01-01' });
      add(t, { name: `INED long ${tag}`, boardRole: 'independent_non_executive_director', independent: true, executive: false, gender: 'female',
        appointedOn: '2010-01-01', committees: ['audit', 'risk'] });
      add(t, { name: `INED ${tag}`, boardRole: 'independent_non_executive_director', independent: true, executive: false, gender: 'male',
        appointedOn: '2023-01-01', committees: ['audit'] });
    });

    cy.login(roles.ESG_DIRECTOR.email);
    cy.visit('/governance');
    cy.get('[aria-label="Board composition"]').should('contain', 'Directors').and('contain', 'Independent');
    // Add the company secretary through the form.
    cy.contains('button', 'Add board member').click();
    cy.get('[role="dialog"]').within(() => cy.field('Name').type(`Secretary ${tag}`));
    cy.pick('Board role', 'Company secretary', { within: '[role="dialog"]' });
    cy.get('[role="dialog"]').within(() => {
      cy.field('Appointed on').type('2022-05-01');
      cy.contains('button', 'Add member').click();
    });
    cy.get('table[aria-label="Board members"]').should('contain', `Secretary ${tag}`);

    cy.contains('[role="tab"]', 'Code check').click();
    cy.get('table[aria-label="Code check results"]').within(() => {
      cy.contains('tr', 'company secretary').should('contain', 'Meets');
      cy.contains('tr', 'nine years').should('contain', 'Does not meet').and('contain', `INED long ${tag}`);
      cy.contains('tr', 'remuneration committee').should('contain', 'Does not meet');
    });
    cy.contains('pending ESG expert verification');

    // The dashboard summarises board health.
    cy.visit('/');
    cy.get('table[aria-label="Governance summary"]').should('contain', 'not met');
  });
});
