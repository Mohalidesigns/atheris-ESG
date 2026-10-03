// WP-52 (D40): NDPA 2023 controls end to end — the data protection officer publishes a privacy notice, a user must read
// it before working and can download their own data; an erasure request anonymises a leaver's account.
// Runs last in the suite (it publishes a notice every later sign-in would have to acknowledge).
import roles from '../fixtures/roles.json';

describe('WP-52: privacy notice, own data and erasure', () => {
  it('the notice is published and acknowledged; a person downloads their data; a leaver is erased', () => {
    const tag = Date.now().toString(36);
    const title = `How Atheris ESG uses your data ${tag}`;

    // The compliance officer (data protection officer) publishes the notice.
    cy.login(roles.COMPLIANCE_OFFICER.email);
    cy.visit('/admin?tab=privacy');
    cy.get('[aria-label="Privacy notice"]').contains('button', /Publish/).click();
    cy.get('[role="dialog"]').within(() => {
      cy.field('Title').type(title);
      cy.field('Notice').type('We process your account and activity data to run the ESG platform for your employer.');
      cy.field('Data protection officer contact').type('dpo@esgpro.ng');
      cy.contains('button', 'Publish').click();
    });
    // The publisher reads it too before carrying on.
    cy.get('[role="dialog"]').should('contain', title).contains('button', 'I have read this notice').click();
    cy.get('[aria-label="Acknowledgements"]').should('contain', 'have read it');

    // An analyst is shown the notice once, then can download their own data.
    cy.login(roles.ESG_ANALYST.email);
    cy.get('[role="dialog"]').should('contain', title).and('contain', 'dpo@esgpro.ng');
    cy.contains('button', 'I have read this notice').click();
    cy.get('[role="dialog"]').should('not.exist');
    cy.reload();
    cy.get('[role="dialog"]').should('not.exist');
    cy.intercept('GET', '/api/v1/privacy/my-data').as('myData');
    cy.get('[aria-label="Account menu"]').click();
    cy.contains('[role="menuitem"]', 'Download my data').click();
    cy.wait('@myData').then(({ response }) => {
      expect(response.statusCode).to.eq(200);
      expect(response.headers['content-disposition']).to.contain('my-data.json');
      expect(response.body.profile.email).to.eq(roles.ESG_ANALYST.email);
    });

    // A leaver asks to be erased: the request is recorded and their account anonymised.
    const email = `leaver.${tag}@example.org`;
    cy.apiToken(roles.TENANT_ADMIN.email).then((admin) => cy.api(admin, 'POST', '/admin/users', {
      email, firstName: 'Leaver', lastName: tag, password: 'Str0ng!Passw0rd-e2e', preferredLanguage: 'en', timezone: 'Africa/Lagos',
    }));
    cy.login(roles.COMPLIANCE_OFFICER.email);
    cy.visit('/admin?tab=privacy');
    cy.contains('button', 'Record request').click();
    cy.pick('Request', 'Erasure', { within: '[role="dialog"]' });
    cy.pick('User account (if they have one)', email, { within: '[role="dialog"]' });
    cy.get('[role="dialog"]').within(() => {
      cy.field("Person's name").type(`Leaver ${tag}`);
      cy.contains('button', 'Record request').click();
    });
    cy.get('table[aria-label="Data subject requests"]').contains('tr', `Leaver ${tag}`).within(() => {
      cy.contains('Received');
      cy.get('td').first().invoke('text').as('reference', { type: 'static' });
      cy.contains('button', 'Erase').click();
    });
    cy.get('@reference').then((reference) => {
      cy.get('[role="dialog"]').within(() => {
        cy.contains('button', 'Erase').should('be.disabled');
        cy.field(`Type ${reference} to confirm`).type(reference);
        cy.contains('button', 'Erase').click();
      });
    });
    cy.get('table[aria-label="Data subject requests"]').contains('tr', `Leaver ${tag}`).should('contain', 'Completed')
      .and('contain', 'Kept as required by law');
    // The erased person can no longer sign in.
    cy.request({ method: 'POST', url: '/api/v1/auth/login', body: { email, password: 'Str0ng!Passw0rd-e2e' }, failOnStatusCode: false })
      .its('status').should('eq', 401);
  });
});
