// Phase 0 security and integrity (WP-52a, D25): MFA for MFA-required roles (S1), audited settings and refused
// actions with a usable Audit log (S2/S3), the role picker and user list (S4/S5), virus scanning with quarantine
// (S6) and provisional emission factors (S7).
import roles from '../fixtures/roles.json';

// The EICAR anti-virus test file, assembled at runtime so this source isn't flagged by real scanners.
const EICAR = 'X5O!P%@AP[4\\PZX54(P^)7CC)7}$' + 'EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*';

describe('Hardening Phase 0: security and integrity', () => {
  it('S1: an MFA-required role signs in only with a verification code', () => {
    cy.clearCookies();
    cy.visit('/login');
    cy.field('Email Address').type(roles.ESG_DIRECTOR.email);
    cy.env(['password']).then(({ password }) => cy.field('Password').type(password, { log: false }));
    cy.contains('button', 'Sign In').click();
    // No session yet: the code step, not the app.
    cy.contains('Enter the 6-digit code from your authenticator app.');
    cy.get('nav[aria-label="Main"]').should('not.exist');
    cy.field('Verification code').type('000000');
    cy.contains('button', 'Verify').click();
    cy.contains('Invalid verification code');
    cy.mfaCode(roles.ESG_DIRECTOR.email).then((code) => cy.field('Verification code').clear().type(code, { log: false }));
    cy.contains('button', 'Verify').click();
    cy.get('nav[aria-label="Main"]', { timeout: 20000 }).should('be.visible');
    // A password-only API login gets a challenge, never tokens.
    cy.env(['password']).then(({ password }) => cy.request('POST', '/api/v1/auth/login', { email: roles.ESG_DIRECTOR.email, password })
      .its('body.data').should((data) => {
        expect(data.mfaRequired).to.eq(true);
        expect(data.accessToken).to.eq(undefined);
      }));
  });

  it('S4/S5: the tenant admin sees no platform users and no SUPER ADMIN role to grant', () => {
    cy.login(roles.TENANT_ADMIN.email);
    cy.visit('/admin');
    cy.contains('Adaeze Usman').should('exist');
    cy.contains(roles.SUPER_ADMIN.email).should('not.exist');
    cy.contains('Segun Platform').should('not.exist');
    cy.get(`[aria-label="Roles for ${roles.ESG_ANALYST.email}"]`).click();
    cy.get('[role="dialog"]').contains('label', 'Role').parent().find('[role="combobox"]').click();
    cy.get('[role="listbox"]').should('contain', 'TENANT ADMIN').and('not.contain', 'SUPER ADMIN');
    cy.get('body').type('{esc}'); // close the list (the first option can be a disabled, already-held role)
  });

  it('S3/S2: a refused action and a settings change are in the Audit log, findable by name', () => {
    const tag = `e2e-${Date.now()}`;
    // The auditor (read-only) tries to create a user through the API: refused, and recorded.
    cy.apiToken(roles.AUDITOR.email).then((auditor) => cy.request({
      method: 'POST', url: '/api/v1/admin/users', failOnStatusCode: false, headers: { Authorization: `Bearer ${auditor}` },
      body: { email: `${tag}@esgpro.ng`, firstName: 'No', lastName: 'Access', password: 'Str0ng!Passw0rd' },
    }).its('status').should('eq', 403));

    cy.login(roles.TENANT_ADMIN.email);
    cy.visit('/admin');
    cy.contains('[role="tab"]', 'Audit log').click();
    cy.pick('Action', 'Refused');
    cy.contains('tr', 'Aisha Auditor').should('contain', 'POST /v1/admin/users').and('contain', 'ESG-403');

    // Settings change (fiscal year start) → audited with before and after, under its friendly table name.
    cy.contains('[role="tab"]', 'Settings').click();
    cy.pick('Fiscal year starts', 'April');
    cy.contains('button', 'Save settings').click();
    cy.contains('Settings saved');
    cy.pick('Fiscal year starts', 'January');
    cy.contains('button', 'Save settings').click();
    cy.contains('Settings saved');
    cy.contains('[role="tab"]', 'Audit log').click();
    cy.pick('Table', 'Tenant settings');
    cy.pick('Action', 'Changed');
    cy.contains('tr', 'Tolu Admin').first().find('[aria-label^="Details for update"]').click();
    cy.get('table[aria-label="Changed fields"]').should('contain', 'fiscal_year_start_month');
  });

  it('S6: an infected upload is quarantined, refused and audited', () => {
    cy.login(roles.DATA_COLLECTOR.email);
    cy.visit('/data-hub');
    cy.contains('button', 'Add data point').click();
    cy.get('[role="dialog"]').within(() => {
      cy.get('[data-testid="evidence-input"]').selectFile({ contents: Cypress.Buffer.from(EICAR), fileName: 'eicar.txt' }, { force: true });
      cy.contains('failed the virus scan (Eicar-Test-Signature) and was quarantined');
      cy.contains('button', 'Cancel').click();
    });
    cy.logout();
    cy.login(roles.TENANT_ADMIN.email);
    cy.visit('/admin');
    cy.contains('[role="tab"]', 'Audit log').click();
    cy.pick('Action', 'Quarantined');
    cy.contains('tr', 'Emeka Nwosu').find('[aria-label^="Details for quarantined"]').click();
    cy.get('table[aria-label="Changed fields"]').should('contain', 'Eicar-Test-Signature').and('contain', 'eicar.txt');
  });

  it('S7: a calculation with a factor pending expert review is marked provisional', () => {
    cy.uniqueDay().then((day) => {
      cy.login(roles.ESG_DIRECTOR.email);
      cy.carbonTab('Emission records', 2026);
      cy.calculateEmission({ activity: 'Diesel (AGO) in generators', org: 'Lagos HQ', day, amount: 10 });
      cy.get('[aria-label="Calculation result"]').should('contain', 'Provisional factor');
    });
  });
});
