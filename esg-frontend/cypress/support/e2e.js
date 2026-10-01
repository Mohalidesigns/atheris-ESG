// Shared E2E commands (WP-18). Selectors go through visible labels and roles, as a user would.

/** The input a visible <label> points at (MUI TextField). */
Cypress.Commands.add('field', (label, options = {}) =>
  cy.contains('label', new RegExp(`^${Cypress._.escapeRegExp(label)}( \\*)?$`), options)
    .invoke('attr', 'for')
    .then((id) => cy.get(`[id="${id}"]`)));

/** Picks an option in an MUI select identified by its label. */
Cypress.Commands.add('pick', (label, option, { within } = {}) => {
  const scope = within ? cy.get(within) : cy.root();
  scope.contains('label', label).parent().find('[role="combobox"]').click();
  cy.get('[role="listbox"]').contains('[role="option"]', option).click();
});

/** Signs in through the login page and waits for the app shell. Default password: the seeded one (config env). */
Cypress.Commands.add('login', (email, password) => {
  const signIn = (secret) => {
    cy.clearCookies(); // an API login (cy.apiToken) leaves a refresh cookie that would restore another session
    cy.visit('/login');
    cy.field('Email Address').clear().type(email);
    cy.field('Password').clear().type(secret, { log: false });
    cy.contains('button', 'Sign In').click();
    cy.get('nav[aria-label="Main"]', { timeout: 20000 }).should('be.visible');
  };
  if (password) signIn(password);
  else cy.env(['password']).then(({ password: seeded }) => signIn(seeded));
});

Cypress.Commands.add('logout', () => {
  cy.get('[aria-label="Sign out"]').click();
  cy.contains('button', 'Sign In').should('be.visible');
});

/** Labels of the sidebar items, in order. */
Cypress.Commands.add('menu', () =>
  cy.get('nav[aria-label="Main"] .MuiListItemText-primary').then(($items) => Cypress._.map($items, 'innerText')));

/** A one-day period in 2026 unique to this attempt (fresh DB per run; retries must not collide with themselves). */
Cypress.Commands.add('uniqueDay', () => {
  const day = new Date(Date.UTC(2026, 0, 1) + (Math.floor(Date.now() / 1000) % 330) * 86400000);
  return cy.wrap(day.toISOString().slice(0, 10));
});

/** Signs in through the API and yields a bearer token (for setting up data a journey only needs to look at). */
Cypress.Commands.add('apiToken', (email) => cy.env(['password']).then(({ password }) =>
  cy.request('POST', '/api/v1/auth/login', { email, password }).its('body.data.accessToken')));

/** Calls the API with a bearer token. */
Cypress.Commands.add('api', (token, method, url, body) =>
  cy.request({ method, url: `/api/v1${url}`, body, headers: { Authorization: `Bearer ${token}` } }).its('body.data'));
