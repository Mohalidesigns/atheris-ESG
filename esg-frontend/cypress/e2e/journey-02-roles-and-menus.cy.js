// Journey 2 (master plan §7.3): each of the 12 roles logs in and sees only its permitted menus.
// Also Gate G1: "a user of each of the 12 roles can log in on a clean clone".
import roles from '../fixtures/roles.json';

const ALL_PAGES = {
  'Data Hub': '/data-hub', 'Carbon Accounting': '/carbon', Compliance: '/compliance', Reporting: '/reporting',
  Governance: '/governance', 'Investor Portal': '/investor', Training: '/training', Administration: '/admin',
};

describe('Journey 2: every role signs in and sees only its menus', () => {
  Object.entries(roles).filter(([role]) => !role.startsWith('_')).forEach(([role, { email, menu }]) => {
    it(`${role} (${email})`, () => {
      cy.login(email);
      cy.menu().should('deep.equal', menu);
      cy.get('nav[aria-label="Main"]').parent().contains(role.replace(/_/g, ' '));

      // A page outside the role's menu is refused, not just hidden.
      const hidden = Object.entries(ALL_PAGES).find(([label]) => !menu.includes(label));
      if (hidden) {
        cy.visit(hidden[1]);
        cy.contains("You don't have access to this page").should('be.visible');
      }
      cy.logout();
    });
  });

  it('a wrong password is refused with the generic message', () => {
    cy.visit('/login');
    cy.field('Email Address').type(roles.AUDITOR.email);
    cy.field('Password').type('not-the-password', { log: false });
    cy.contains('button', 'Sign In').click();
    cy.contains('Invalid email or password').should('be.visible');
    cy.get('nav[aria-label="Main"]').should('not.exist');
  });
});
