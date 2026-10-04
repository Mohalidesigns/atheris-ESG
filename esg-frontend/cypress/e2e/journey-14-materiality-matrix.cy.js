// Journey 14 (master plan §7.3): a materiality assessment → the matrix renders from real scores. Positions follow the
// formulas in RiskMath (golden values in RiskMathTest): Climate change (4, 3, 5; financial 4 × 4) with investors 5 and
// communities 3 at 40/60 and a 0.25 blend sits at impact 3.95, financial 3.20 — double material.
import roles from '../fixtures/roles.json';

describe('Journey 14: materiality matrix from real scores', () => {
  it('weights stakeholders, scores topics, re-weights, and has the assessment approved by someone else', () => {
    const name = `DMA ${Date.now().toString(36)}`;
    const year = new Date().getFullYear();
    cy.login(roles.ESG_DIRECTOR.email);
    cy.visit('/risk?tab=materiality');
    cy.contains('button', 'New assessment').click();
    cy.get('.MuiDialog-paper').within(() => {
      cy.field('Assessment name').type(name);
      cy.field('Reporting year').clear().type(String(year));
      cy.field('Stakeholder blend').clear().type('0.25');
      cy.contains('button', 'Create assessment').click();
      // The blend needs stakeholder groups (the server's rule, in the form).
      cy.contains('add stakeholder groups before blending their ratings in');
      cy.contains('button', 'Add stakeholder group').click();
      cy.contains('button', 'Add stakeholder group').click();
      cy.get('input[aria-label="Weight, line 1"]').type('40');
      cy.get('input[aria-label="Weight, line 2"]').type('60');
    });
    cy.get('[aria-label="Stakeholder group, line 1"]').click();
    cy.contains('[role="option"]', 'Investors').click();
    cy.get('[aria-label="Stakeholder group, line 2"]').click();
    cy.contains('[role="option"]', 'Communities').click();
    cy.get('.MuiDialog-paper').contains('button', 'Create assessment').click();
    cy.get('.MuiDialog-paper').should('not.exist');

    const addTopic = (topic, impactType, scores, financial, ratings) => {
      cy.get('[role="region"][aria-label="Materiality assessment"]').contains('button', 'Add topic').click();
      cy.get('.MuiDialog-paper').within(() => cy.field('Topic').type(topic));
      cy.pick('Pillar', 'Environmental', { within: '.MuiDialog-paper' });
      cy.pick('Impact', impactType, { within: '.MuiDialog-paper' });
      Object.entries(scores).forEach(([label, option]) => cy.pick(label, option, { within: '.MuiDialog-paper' }));
      Object.entries(financial).forEach(([label, option]) => cy.pick(label, option, { within: '.MuiDialog-paper' }));
      Object.entries(ratings).forEach(([label, option]) => cy.pick(label, option, { within: '.MuiDialog-paper' }));
      cy.get('.MuiDialog-paper').contains('button', 'Add topic').click();
      cy.get('.MuiDialog-paper').should('not.exist');
    };
    addTopic('Climate change', 'Actual', { Scale: '4 · High', Scope: '3 · Moderate', Irremediability: '5 · Very high' },
      { 'Financial magnitude': '4 · High', 'Financial likelihood': '4 · Likely' }, { 'Investors rating': '5 · Very high', 'Communities rating': '3 · Moderate' });
    addTopic('Biodiversity', 'Potential', { Scale: '2 · Low', Scope: '2 · Low', Irremediability: '3 · Moderate', 'Impact likelihood': '2 · Unlikely' },
      { 'Financial magnitude': '1 · Very low', 'Financial likelihood': '1 · Rare' }, { 'Communities rating': '2 · Low' });

    cy.get('table[aria-label="Materiality topics"]').within(() => {
      cy.contains('tr', 'Climate change').find('td').then((td) => {
        expect(td.eq(5).text(), 'impact position').to.eq('3.95');
        expect(td.eq(6).text(), 'financial').to.eq('3.20');
        expect(td.eq(4).text(), 'stakeholders').to.eq('3.80');
        expect(td.eq(7).text()).to.eq('Double material');
      });
      cy.contains('tr', 'Biodiversity').should('contain', '1.20').and('contain', '0.20').and('contain', 'Not material');
    });
    cy.get('[aria-label="Materiality matrix"] .recharts-scatter-symbol').should('have.length', 2);
    cy.get('[aria-label="Material topics"]').should('contain', '1 of 2 topics material');

    // Re-weighting repositions: with no stakeholder blend Climate change sits at its own impact score.
    cy.contains('button', 'Edit assessment').click();
    cy.get('.MuiDialog-paper').within(() => {
      cy.field('Stakeholder blend').clear().type('0');
      cy.contains('button', 'Save changes').click();
    });
    cy.get('.MuiDialog-paper').should('not.exist');
    // Wait for the refreshed assessment (the severity column also reads 4.00, so check the blend and the impact position cell).
    cy.get('[role="region"][aria-label="Materiality assessment"]').should('contain', 'stakeholder blend 0.00');
    cy.get('table[aria-label="Materiality topics"]').contains('tr', 'Climate change').find('td').eq(5).should('have.text', '4.00');

    // Submit; the submitter can't approve, another approver does.
    cy.contains('[role="region"] button', 'Submit for approval').click();
    cy.get('[aria-label="Assessment status"]').should('contain', 'In review');
    cy.get('[aria-label^="Approve: You submitted this assessment"]').should('exist');
    cy.apiToken(roles.TENANT_ADMIN.email).then((t) => cy.api(t, 'GET', '/risk/assessments').then((list) => {
      const a = list.find((x) => x.name === name);
      cy.api(t, 'POST', `/risk/assessments/${a.id}/actions`, { action: 'approve', version: a.version }).its('status').should('eq', 'approved');
    }));
    cy.reload();
    cy.get('table[aria-label="Materiality assessments"]').contains('tr', name).should('contain', 'Approved');
  });
});
