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

/**
 * Runs the Emission Calculator (Carbon → Emission records → Calculate emissions) up to the server-calculated result.
 * Leaves the dialog open on the result step so the journey can check it and save.
 */
Cypress.Commands.add('calculateEmission', ({ scope, scope3Category, spend = false, activity, org, day, amount, currency }) => {
  cy.contains('button', 'Calculate emissions').click();
  const dialog = '[role="dialog"]';
  if (scope) cy.pick('Scope', scope, { within: dialog });
  if (scope3Category) cy.pick('Scope 3 category', scope3Category, { within: dialog });
  if (spend) cy.get(dialog).contains('button', 'Spend-based').click();
  cy.get(dialog).contains('button', 'Next').click();
  cy.pick('Activity', activity, { within: dialog });
  cy.pick('Organisation', org, { within: dialog });
  cy.get(dialog).within(() => {
    cy.field('Period start').type(day);
    cy.field('Period end').type(day);
    cy.contains('button', 'Next').click();
    cy.field(spend ? 'Amount spent' : 'Activity data').type(String(amount));
  });
  if (currency) cy.pick('Currency', currency, { within: dialog });
  cy.get(dialog).contains('button', 'Calculate').click();
  cy.get('[aria-label="Calculation result"]').should('be.visible');
});

/** Opens a Carbon Accounting tab for a year. */
Cypress.Commands.add('carbonTab', (tab, year) => {
  cy.visit('/carbon');
  if (year) cy.pick('Year', String(year));
  cy.contains('[role="tab"]', tab).click();
});
