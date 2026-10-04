// Journey 28 (master plan §7.3, WP-45, D45): a consultant switches the interface to French on the sign-in page, signs in,
// finds the menu in French and the choice kept on the account (another device starts in French too), then switches to
// Arabic: the page turns right-to-left and still passes WCAG 2.1 AA. Translations are read from the shipped bundles.
import roles from '../fixtures/roles.json';

const user = roles.CONSULTANT;

const resetLanguage = () => cy.apiToken(user.email).then((token) => cy.api(token, 'PUT', '/auth/me/language', { preferredLanguage: 'en' }));

describe('Journey 28: interface language', () => {
  beforeEach(resetLanguage); // also before a retry
  after(resetLanguage);

  it('French from the sign-in page, kept on the account; Arabic right-to-left and accessible', () => {
    cy.readFile('src/i18n/locales/fr.json').then(({ messages: fr }) => cy.readFile('src/i18n/locales/ar.json').then(({ messages: ar }) => {
      cy.intercept('PUT', '**/api/v1/auth/me/language').as('saveLanguage');
      // Signed out: the sign-in page offers the languages and says which are unreviewed drafts.
      cy.clearCookies();
      cy.visit('/login');
      cy.get('[aria-label="Language: English"]').click();
      cy.get('[role="menu"]').within(() => {
        cy.contains('li', 'Français').should('contain', 'Draft translation');
        cy.contains('li', 'Français').click();
      });
      cy.get('html').should('have.attr', 'lang', 'fr').and('have.attr', 'dir', 'ltr');
      cy.contains(fr['This translation is a draft and has not been reviewed by a professional translator yet.']).should('be.visible');

      // Sign in with the French form.
      cy.env(['password']).then(({ password }) => {
        cy.field(fr['Email Address']).type(user.email);
        cy.field(fr.Password).type(password, { log: false });
      });
      cy.contains('button', fr['Sign In']).click();
      cy.get(`nav[aria-label="${fr.Main}"]`, { timeout: 20000 }).should('exist');
      cy.get(`nav[aria-label="${fr.Main}"] .MuiListItemText-primary`)
        .then(($items) => Cypress._.map($items, 'innerText'))
        .should('deep.equal', user.menu.map((item) => fr[item]));

      // The choice is on the account: a device that has never chosen a language starts in French after sign-in.
      cy.wait('@saveLanguage').its('request.body').should('deep.equal', { preferredLanguage: 'fr' });
      cy.apiToken(user.email).then((token) => cy.api(token, 'GET', '/auth/me')).its('preferredLanguage').should('eq', 'fr');
      cy.clearLocalStorage();
      cy.reload();
      cy.get(`nav[aria-label="${fr.Main}"]`, { timeout: 20000 }).should('exist');
      cy.get('html').should('have.attr', 'lang', 'fr');

      // Arabic from the account menu: right-to-left, translated, and accessible.
      cy.get(`[aria-label="${fr['Account menu']}"]`).click();
      cy.get(`[aria-label="${fr.Language}: Français"]`).click();
      cy.contains('[role="menu"] li', 'العربية').click();
      cy.get('html').should('have.attr', 'lang', 'ar').and('have.attr', 'dir', 'rtl');
      cy.wait('@saveLanguage').its('request.body').should('deep.equal', { preferredLanguage: 'ar' });
      cy.get(`nav[aria-label="${ar.Main}"]`).should('exist');
      cy.contains(`nav[aria-label="${ar.Main}"] .MuiListItemText-primary`, ar.Dashboard).should('be.visible');
      cy.get('body').should('have.css', 'direction', 'rtl');
      cy.a11y('dashboard in Arabic (rtl)');

      // Back to English.
      cy.get(`[aria-label="${ar['Account menu']}"]`).click();
      cy.get(`[aria-label="${ar.Language}: العربية"]`).click();
      cy.contains('[role="menu"] li', 'English').click();
      cy.get('html').should('have.attr', 'dir', 'ltr');
      cy.get('nav[aria-label="Main"]').should('exist');
      cy.wait('@saveLanguage').its('request.body').should('deep.equal', { preferredLanguage: 'en' });
      cy.apiToken(user.email).then((token) => cy.api(token, 'GET', '/auth/me')).its('preferredLanguage').should('eq', 'en');
    }));
  });
});
