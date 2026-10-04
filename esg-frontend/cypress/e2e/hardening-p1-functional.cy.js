// Phase 1 functional fixes (WP-52b, D26): the import template imports cleanly and the history updates by itself
// (F1–F3), the calculator offers only the chosen scope's activities and says when a category has no factor (F4, F5),
// targets show this year's actual (F6), time zones come from a list (F10), deleting a draft doesn't refetch it (F13)
// and the dashboard's review card shows both queues and opens them (F14).
import roles from '../fixtures/roles.json';

describe('Hardening Phase 1: functional fixes', () => {
  it('F1–F3: the downloaded template imports with no failures and the history updates without a reload', () => {
    cy.apiToken(roles.DATA_COLLECTOR.email).then((token) => cy.request({
      url: '/api/v1/data/import/template', headers: { Authorization: `Bearer ${token}` },
    }).its('body').then((csv) => {
      expect(csv).to.contain('OML 42 Field').and.not.contain('Lagos HQ');
      cy.login(roles.DATA_COLLECTOR.email);
      cy.visit('/data-hub');
      cy.contains('[role="tab"]', 'Import').click();
      cy.get('[data-testid="import-file"]').selectFile({ contents: Cypress.Buffer.from(csv), fileName: 'template.csv' }, { force: true });
      // Every column is matched automatically, notes included.
      cy.get('form').within(() => {
        cy.contains('label', 'Notes').parent().find('[role="combobox"]').should('contain', 'notes');
        cy.contains('button', /^Import 3 rows$/).click();
      });
      cy.get('[aria-label="Import result"]').should('contain', '3 of 3 rows imported as drafts');
      cy.get('table[aria-label="Recent imports"]').contains('tr', 'template.csv')
        .should('contain', 'completed').find('td').eq(2).should('have.text', '3');
    }));
  });

  it('F4/F5: the calculator offers only the chosen scope\'s activities, and says when a category has none', () => {
    cy.login(roles.ESG_DIRECTOR.email);
    cy.carbonTab('Emission records');
    cy.contains('button', 'Calculate emissions').click();
    cy.pick('Scope', 'Scope 2 — purchased energy', { within: '[role="dialog"]' });
    cy.get('[role="dialog"]').contains('button', 'Next').click();
    cy.get('[role="dialog"]').contains('label', 'Activity').parent().find('[role="combobox"]').click();
    cy.get('[role="listbox"] [role="option"]').should('have.length', 1).and('contain', 'Grid electricity');
    cy.get('[role="option"]').first().click();
    cy.get('[role="dialog"]').contains('button', 'Back').click();

    // Business travel (category 6) has spend-based EEIO factors now.
    cy.pick('Scope', 'Scope 3 — value chain', { within: '[role="dialog"]' });
    cy.pick('Scope 3 category', '6. Business travel', { within: '[role="dialog"]' });
    cy.get('[role="dialog"]').contains('button', 'Spend-based').click();
    cy.get('[role="dialog"]').contains('button', 'Next').click();
    cy.get('[role="dialog"]').contains('label', 'Activity').parent().find('[role="combobox"]').click();
    cy.get('[role="listbox"]').should('contain', 'Air travel (spend)').and('contain', 'Hotel stays (spend)');
    cy.get('[role="option"]').first().click();
    cy.get('[role="dialog"]').contains('button', 'Back').click();

    // Category 15 (investments) has none: say so and offer the factor library.
    cy.pick('Scope 3 category', '15. Investments', { within: '[role="dialog"]' });
    cy.get('[role="dialog"]').contains('button', 'Next').click();
    cy.get('[aria-label="No factors"]').should('contain', 'No spend-based factors for Scope 3 category 15 — add a custom factor');
    cy.contains('button', 'Add a custom factor').click();
    cy.contains('[role="tab"][aria-selected="true"]', 'Emission factors');
  });

  it('F6: a target set this year shows this year\'s actual against its pathway', () => {
    const year = new Date().getFullYear();
    cy.apiToken(roles.ESG_DIRECTOR.email).then((token) => {
      cy.api(token, 'POST', '/carbon/emissions', {
        orgId: '0b000000-0000-4000-8000-000000000001', scope: 'scope_1', sourceActivity: 'diesel_generator', activityData: 500,
        reportingPeriodStart: `${year}-02-01`, reportingPeriodEnd: `${year}-02-28`,
      });
      cy.api(token, 'POST', '/carbon/targets', {
        targetName: `E2E base-year target ${Date.now()}`, targetType: 'absolute', scope: 'scope_1_2', baseYear: year, baseValue: 5000,
        targetYear: year + 4, targetValue: 2500, pathway: 'linear',
      }).then((target) => {
        expect(target.progressYear).to.eq(year);
        expect(target.status).to.not.eq('no_data');
        cy.login(roles.ESG_DIRECTOR.email);
        cy.carbonTab('Reduction targets');
        cy.contains(target.targetName).parents('.MuiCard-root').within(() => {
          cy.contains(`${year}:`).should('contain', 'actual vs');
          cy.contains('No data yet').should('not.exist');
        });
      });
    });
  });

  it('F10: the reporting time zone is picked from the IANA list', () => {
    cy.login(roles.TENANT_ADMIN.email);
    cy.visit('/admin');
    cy.contains('[role="tab"]', 'Settings').click();
    // Doesn't assume the starting zone (a retry may start from Accra); each save is checked after a reload.
    const pick = (typed, zone) => {
      cy.field('Reporting time zone').should('not.have.value', '').clear().type(typed);
      cy.contains('[role="option"]', zone).click();
      cy.contains('button', 'Save settings').click();
      cy.contains('Settings saved');
      cy.reload();
      cy.contains('[role="tab"]', 'Settings').click();
      cy.field('Reporting time zone').should('have.value', zone);
    };
    pick('Africa/Ac', 'Africa/Accra');
    pick('Africa/Lag', 'Africa/Lagos');
  });

  it('F13/F14: the review card opens the queues; deleting a draft closes it without refetching', () => {
    cy.uniqueDay().then((day) => {
      cy.apiToken(roles.DATA_COLLECTOR.email).then((token) => cy.api(token, 'POST', '/data/points', {
        orgId: '0b000000-0000-4000-8000-000000000004', metricKey: 'water_withdrawal_m3', numericValue: 12,
        reportingPeriodStart: day, reportingPeriodEnd: day,
      }).then((draft) => {
        cy.login(roles.DATA_COLLECTOR.email);
        cy.visit('/data-hub');
        cy.intercept('GET', `/api/v1/data/points/${draft.id}`).as('point');
        cy.get('table[aria-label="Data points"]').find(`[aria-label="Open Water withdrawal ${day}"]`).click();
        cy.wait('@point');
        // From here on, any request for the draft would be the stale refetch (404) that F13 removed.
        let afterDelete = 0;
        cy.intercept('GET', `/api/v1/data/points/${draft.id}`, () => { afterDelete += 1; });
        cy.get('[role="region"][aria-label="Data point details"]').contains('button', 'Delete draft').click();
        cy.get('[role="dialog"][aria-labelledby="confirm-title"]').contains('button', 'Delete').click(); // the app's dialog (Phase 2)
        cy.get('[role="region"][aria-label="Data point details"]').should('not.exist');
        cy.wait(500).then(() => expect(afterDelete, 'requests for the deleted draft').to.eq(0));
      }));
    });
    cy.login(roles.ESG_DIRECTOR.email);
    cy.visit('/dashboard');
    cy.get('a[aria-label^="Data points awaiting review"]').should('contain', 'to validate').and('contain', 'to approve').click();
    cy.location('pathname').should('eq', '/data-hub');
    cy.contains('[role="tab"][aria-selected="true"]', 'Review');
  });
});
