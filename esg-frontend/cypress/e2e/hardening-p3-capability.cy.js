// Hardening Phase 3 (WP-52d): missing capability — multi-user lifecycle, bulk review, draft preview, intensity
// onboarding, framework content, audit log, test-data hygiene and the source system / responsible person split.
import roles from '../fixtures/roles.json';

const OML42 = '0b000000-0000-4000-8000-000000000004';
const SCOPES = {
  diesel_litres: 'scope_1', petrol_litres: 'scope_1', lpg_kg: 'scope_1', natural_gas_m3: 'scope_1', electricity_kwh: 'scope_2',
};

/** A day in `year` unique to this attempt (no other spec uses 2021 or 2031; neither has a locked period). */
const dayIn = (year, offset = 0) => cy.uniqueDay().then((d) => {
  const base = new Date(`${year}${d.slice(4)}T00:00:00Z`);
  return new Date(base.getTime() + offset * 86400000).toISOString().slice(0, 10);
});
const day2031 = (offset = 0) => dayIn(2031, offset);

/** collector creates and submits; analyst validates; director approves. Yields the approved point. */
const approvedPoint = (metricKey, value, day) => cy.apiToken(roles.DATA_COLLECTOR.email).then((collector) =>
  cy.api(collector, 'POST', '/data/points', { orgId: OML42, metricKey, numericValue: value, reportingPeriodStart: day, reportingPeriodEnd: day })
    .then((p) => cy.api(collector, 'PUT', `/data/points/${p.id}/submit`, { version: p.version })))
  .then((p) => cy.apiToken(roles.ESG_ANALYST.email).then((analyst) => cy.api(analyst, 'PUT', `/data/points/${p.id}/validate`, { version: p.version })))
  .then((p) => cy.apiToken(roles.ESG_DIRECTOR.email).then((director) => cy.api(director, 'PUT', `/data/points/${p.id}/approve`, { version: p.version })));

const recordsFor = (pointId, day) => cy.apiToken(roles.ESG_DIRECTOR.email).then((director) =>
  cy.api(director, 'GET', `/carbon/emissions?source=data_hub&from=${day}&to=${day}&size=100`))
  .then((page) => page.content.filter((r) => r.dataPointId === pointId));

describe('Hardening Phase 3: missing capability', () => {
  it('(1) approved diesel, petrol, LPG, natural gas and electricity become exactly one Scope 1/2 record each; a correction supersedes it', () => {
    day2031(0).then((day) => {
      Object.entries(SCOPES).forEach(([metric, scope], i) => {
        const d = new Date(Date.parse(day) + i * 86400000).toISOString().slice(0, 10);
        approvedPoint(metric, 100 + i, d).then((p) => recordsFor(p.id, d).then((records) => {
          expect(records, metric).to.have.length(1);
          expect(records[0].scope, metric).to.eq(scope);
          expect(Number(records[0].co2eTonnes), metric).to.be.greaterThan(0);
        }));
      });

      // Reopen → edit → approve again: still one live record, with the new value, linked to the one it replaces.
      const d = new Date(Date.parse(day) + 10 * 86400000).toISOString().slice(0, 10);
      approvedPoint('diesel_litres', 100, d).then((p) => recordsFor(p.id, d).then(([first]) => {
        cy.apiToken(roles.ESG_DIRECTOR.email).then((director) => cy.api(director, 'PUT', `/data/points/${p.id}/reopen`, { version: p.version, reason: 'Meter misread' }))
          .then((reopened) => cy.apiToken(roles.DATA_COLLECTOR.email).then((collector) => cy.api(collector, 'PUT', `/data/points/${p.id}`, {
            orgId: OML42, metricKey: 'diesel_litres', numericValue: 250, reportingPeriodStart: d, reportingPeriodEnd: d, version: reopened.version,
          }).then((edited) => cy.api(collector, 'PUT', `/data/points/${p.id}/submit`, { version: edited.version }))))
          .then((s) => cy.apiToken(roles.ESG_ANALYST.email).then((analyst) => cy.api(analyst, 'PUT', `/data/points/${p.id}/validate`, { version: s.version })))
          .then((v) => cy.apiToken(roles.ESG_DIRECTOR.email).then((director) => cy.api(director, 'PUT', `/data/points/${p.id}/approve`, { version: v.version })))
          .then(() => recordsFor(p.id, d))
          .then((live) => {
            expect(live).to.have.length(1);
            expect(Number(live[0].activityData)).to.eq(250);
            cy.apiToken(roles.AUDITOR.email).then((auditor) => cy.api(auditor, 'GET', `/audit/lineage?type=emission_record&id=${first.id}`))
              .its('downstream').then((edges) => expect(edges.find((e) => e.relation === 'superseded_by').targetId).to.eq(live[0].id));
          });
      }));
    });
  });

  it('(1, 3) a draft report previews watermarked; Director → Compliance → Board approve; PDF and Excel export the approved version', () => {
    day2031(20).then((day) => {
      approvedPoint('electricity_kwh', 5000, day);
      cy.apiToken(roles.ESG_MANAGER.email).then((manager) => cy.api(manager, 'GET', '/reports/templates')
        .then((templates) => cy.api(manager, 'POST', '/reports', {
          templateId: templates.find((t) => t.key === 'gri_core').id, title: `Phase 3 report ${day}`, reportingPeriodStart: day, reportingPeriodEnd: day,
        })).as('report'));

      // The draft previews as a DRAFT-watermarked PDF; the final export isn't offered yet.
      cy.login(roles.ESG_MANAGER.email);
      cy.get('@report').then((r) => cy.visit(`/reporting?report=${r.id}`));
      cy.window().then((win) => cy.stub(win, 'open').as('open').returns({}));
      cy.intercept('GET', '/api/v1/reports/*/preview').as('preview');
      cy.contains('button', 'Preview draft PDF').click();
      cy.wait('@preview').then(({ response }) => {
        expect(response.statusCode).to.eq(200);
        expect(response.headers['content-type']).to.contain('application/pdf');
        expect(response.headers['content-disposition']).to.contain('DRAFT');
      });
      cy.get('@open').should('have.been.calledWithMatch', /^blob:/);
      cy.contains('button', 'Export PDF').should('not.exist');
      cy.get('@report').then((r) => cy.apiToken(roles.ESG_MANAGER.email).then((manager) =>
        cy.apiResponse(manager, 'POST', `/reports/${r.id}/generate`, { format: 'pdf' }).its('status').should('eq', 409)));

      // Submit, then the three approvals, each by its own role.
      cy.get('@report').then((r) => cy.apiToken(roles.ESG_MANAGER.email).then((manager) =>
        cy.api(manager, 'POST', `/reports/${r.id}/approve`, { version: r.version })));
      [roles.ESG_DIRECTOR.email, roles.COMPLIANCE_OFFICER.email, roles.BOARD_MEMBER.email].forEach((email) => {
        cy.get('@report').then((r) => cy.apiToken(email).then((token) => cy.api(token, 'GET', `/reports/${r.id}`).then((current) => {
          const step = current.approval.steps.find((s) => s.canAct);
          expect(step, `${email} can act`).to.exist;
          return cy.api(token, 'PUT', `/reports/${r.id}/approval-steps/${step.id}`, { version: current.version, action: 'approve', comments: 'Checked' });
        })));
      });
      cy.get('@report').then((r) => cy.apiToken(roles.ESG_MANAGER.email).then((manager) => {
        cy.api(manager, 'GET', `/reports/${r.id}`).its('status').should('eq', 'approved');
        ['pdf', 'xlsx'].forEach((format) => cy.api(manager, 'POST', `/reports/${r.id}/generate`, { format }).then((job) => {
          expect(job.status).to.eq('done');
          expect(job.versionLabel).to.eq('v1.0');
          cy.request({ url: `/api/v1/files/${job.fileId}/content`, headers: { Authorization: `Bearer ${manager}` }, encoding: 'binary' })
            .then((res) => expect(res.body.slice(0, format === 'pdf' ? 5 : 2)).to.eq(format === 'pdf' ? '%PDF-' : 'PK'));
        }));
      }));
    });
  });

  it('(1) a regulatory change is assessed by one person and closed by another', () => {
    cy.apiToken(roles.COMPLIANCE_OFFICER.email).then((officer) => cy.api(officer, 'GET', '/compliance/owners').then((owners) =>
      cy.api(officer, 'POST', '/compliance/changes', {
        regulator: 'SEC Nigeria', title: `IFRS S1/S2 circular ${Date.now()}`, publishedDate: '2026-09-23', framework: 'SEC_NG',
        ownerUserId: owners.find((o) => o.email === roles.COMPLIANCE_OFFICER.email).id,
      }))
      .then((c) => cy.api(officer, 'PUT', `/compliance/changes/${c.id}/start`, { version: c.version }))
      .then((c) => cy.api(officer, 'PUT', `/compliance/changes/${c.id}/assess`, {
        version: c.version, impactLevel: 'high', impactAssessment: 'Implementation plan due 15 Oct 2026', requiredActions: 'Submit the plan',
      }))
      .then((c) => {
        // The assessor can't close their own assessment (segregation of duties).
        cy.apiResponse(officer, 'PUT', `/compliance/changes/${c.id}/close`, { version: c.version }).then((res) => {
          expect(res.status).to.eq(409);
          expect(res.body.errorCode).to.eq('ESG-409-SOD');
        });
        cy.apiToken(roles.ESG_DIRECTOR.email).then((director) => cy.api(director, 'PUT', `/compliance/changes/${c.id}/close`, { version: c.version }))
          .its('status').should('eq', 'closed');
      }));
  });

  it('(2) the analyst validates several points at once and sees the outcome per point', () => {
    day2031(40).then((day) => {
      const days = [0, 1, 2].map((i) => new Date(Date.parse(day) + i * 86400000).toISOString().slice(0, 10));
      cy.apiToken(roles.DATA_COLLECTOR.email).then((collector) => days.forEach((d, i) => cy.api(collector, 'POST', '/data/points', {
        orgId: OML42, metricKey: 'water_withdrawal_m3', numericValue: 10 + i, reportingPeriodStart: d, reportingPeriodEnd: d,
      }).then((p) => cy.api(collector, 'PUT', `/data/points/${p.id}/submit`, { version: p.version }))));

      cy.login(roles.ESG_ANALYST.email);
      cy.visit('/data-hub?tab=review');
      days.forEach((d) => cy.get('table[aria-label="Awaiting validation"]').find(`[aria-label="Select Water withdrawal ${d}"]`).check());
      cy.get('[role="region"][aria-label="Bulk actions"]').within(() => {
        cy.contains('3 selected');
        cy.contains('button', 'Validate (3)').click();
      });
      cy.get('[role="dialog"]').contains('button', 'Validate 3').click();
      cy.get('[role="dialog"]').should('contain', 'Validate: 3 of 3 validated').and('contain', 'All 3 data points were validated');
      cy.get('[role="dialog"]').contains('button', 'Close').click();
      // They have left the validation queue (here the only ones in it, so it is now empty).
      days.forEach((d) => cy.get(`[aria-label="Open Water withdrawal ${d}"]`).should('not.exist'));
    });
  });

  it('(4) the intensity card links straight to the form with the metric and year chosen', () => {
    // 2021 (selectable on the Carbon page) gets emissions but no revenue, headcount or production.
    dayIn(2021, 0).then((day) => {
      approvedPoint('diesel_litres', 50, day);
      cy.login(roles.ESG_DIRECTOR.email);
      cy.carbonTab('Overview', 2021);
      cy.get('[aria-label="Missing intensity data"]').contains('a', 'Add revenue').click();
      cy.location('pathname').should('eq', '/data-hub');
      cy.get('[role="dialog"]').within(() => {
        cy.field('Metric').should('have.value', 'Revenue (money)');
        cy.field('Period start').should('have.value', '2021-01-01');
        cy.field('Period end').should('have.value', '2021-12-31');
      });
    });
  });

  it('(5) every framework has content; NGX and SEC Nigeria are loaded and flagged pending SME verification', () => {
    cy.apiToken(roles.COMPLIANCE_OFFICER.email).then((officer) => cy.api(officer, 'GET', '/compliance/frameworks')).then((frameworks) => {
      ['NGX', 'SEC_NG', 'PENCOM', 'CDP', 'GHG_PROTOCOL', 'SASB'].forEach((code) => {
        const f = frameworks.find((x) => x.code === code);
        expect(f.requirementCount, code).to.be.greaterThan(0);
        expect(f.contentStatus, code).to.eq('pending_sme');
      });
    });
    cy.login(roles.COMPLIANCE_OFFICER.email);
    cy.visit('/compliance');
    cy.contains('[role="tab"]', 'Frameworks').click();
    cy.contains('NGX Sustainability Disclosure Guidelines').click();
    cy.contains('Pending SME verification');
    cy.contains("haven't been verified by an ESG subject-matter expert");

    // The platform import: a dry run lists what would change and imports nothing.
    cy.login(roles.SUPER_ADMIN.email);
    cy.visit('/admin?tab=framework-content');
    cy.pick('Framework', 'NGX');
    cy.get('table[aria-label="Content versions"]').should('contain', 'Bundled content pack').and('contain', 'Pending SME verification');
    cy.get('input[aria-label="Content file"]').selectFile({
      contents: Cypress.Buffer.from('requirement_code,title,category,data_type,parent_code,source_ref,metric_keys,description\nS3,Principles,GEN,narrative,,s.3,,\n'),
      fileName: 'ngx-check.csv', mimeType: 'text/csv',
    }, { force: true });
    cy.field('Version label').type('e2e-check');
    cy.field('Effective from').type('2026-10-01');
    cy.field('Source instrument').type('E2E dry run');
    cy.contains('button', 'Check file').click();
    cy.get('[aria-label="Import check"]').should('contain', 'Nothing has been imported yet').and('contain', 'Retired');
    cy.get('table[aria-label="Content versions"] tbody tr').should('have.length', 1);
  });

  it('(6) the audit log filters by user and exports CSV with before → after', () => {
    day2031(80).then((day) => cy.apiToken(roles.DATA_COLLECTOR.email).then((collector) => cy.api(collector, 'POST', '/data/points', {
      orgId: OML42, metricKey: 'water_withdrawal_m3', numericValue: 7, reportingPeriodStart: day, reportingPeriodEnd: day,
    }).then((p) => cy.api(collector, 'PUT', `/data/points/${p.id}/submit`, { version: p.version }))));
    cy.login(roles.TENANT_ADMIN.email);
    cy.visit('/admin?tab=audit');
    cy.pick('User', 'Emeka Nwosu');
    cy.pick('Table', 'Data points');
    cy.get('table').contains('td', 'validation_status: draft → submitted');
    cy.contains('button', 'Export CSV').click();
    const file = `${Cypress.config('downloadsFolder')}/audit-log-${new Date().toISOString().slice(0, 10)}.csv`;
    cy.readFile(file).should('contain', 'When (UTC),User,User email,Action,Table,Record ID,Changes')
      .and('contain', 'emeka.nwosu@esgpro.ng').and('contain', 'validation_status: draft → submitted');
  });

  it('(7) records created by the suite are marked as test data and purged', () => {
    const name = `E2E purge check ${Date.now()}`;
    cy.apiToken(roles.TENANT_ADMIN.email).then((admin) => cy.api(admin, 'POST', '/organisations', {
      name, parentOrgId: OML42, orgType: 'facility', countryCode: 'NG',
    }).then((org) => cy.request({ method: 'DELETE', url: '/api/v1/testing/test-data', headers: { Authorization: `Bearer ${admin}`, 'X-ESG-Test-Run': 'x' } })
      .its('body.data').then((purged) => {
        expect(purged.find((p) => p.entity === 'organisations').purged).to.be.at.least(1);
        cy.apiResponse(admin, 'GET', `/organisations/${org.id}`).its('status').should('eq', 404);
      })));
  });

  it('(8) a source system is not a person: the responsible person is recorded separately', () => {
    const system = `Generator meter ${Date.now().toString(36)}`;
    cy.login(roles.ESG_DIRECTOR.email);
    cy.visit('/data-hub');
    cy.contains('[role="tab"]', 'Sources').click();
    cy.contains('button', 'New data source').click();
    cy.get('[role="dialog"]').within(() => {
      cy.field('System name').type('Chidi Okafor');
      cy.contains('button', 'Add data source').click();
      cy.contains("This is a person's name");
      cy.field('System name').clear().type(system);
    });
    cy.pick('Type', 'Meter', { within: '[role="dialog"]' });
    cy.pick('Owner (responsible person)', 'Chidi Okafor', { within: '[role="dialog"]' });
    cy.get('[role="dialog"]').contains('button', 'Add data source').click();
    cy.get('table[aria-label="Data sources"]').contains('tr', system).should('contain', 'Meter').and('contain', 'Chidi Okafor');

    day2031(100).then((day) => cy.apiToken(roles.ESG_DIRECTOR.email).then((director) => cy.api(director, 'GET', '/data/sources')
      .then((sources) => cy.api(director, 'POST', '/data/points', {
        orgId: OML42, metricKey: 'diesel_litres', numericValue: 12, reportingPeriodStart: day, reportingPeriodEnd: day,
        sourceId: sources.find((s) => s.name === system).id,
      }))).then((p) => {
      expect(p.ownerName).to.eq('Chidi Okafor');
      expect(p.sourceName).to.eq(system);
    }));
  });
});
