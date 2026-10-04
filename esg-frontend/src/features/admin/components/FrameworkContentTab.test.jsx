import { describe, it, expect } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import FrameworkContentTab from './FrameworkContentTab';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';
import { contentImportSchema } from '../frameworkContentSchema';

const frameworks = [{ code: 'NGX', name: 'NGX Sustainability Disclosure Guidelines', requirementCount: 50, contentStatus: 'pending_sme' }];
const versions = [{
  id: 'v1', versionLabel: '2019', effectiveFrom: '2019-01-01', effectiveTo: null, smeVerified: false, verifiedBy: null,
  source: 'NGX Guidelines 2019', format: 'csv', rows: 50, added: 50, changed: 0, retired: 0, importedBy: 'Bundled content pack',
  importedAt: '2026-10-02T10:00:00Z',
}];
const csv = () => new File(['requirement_code,title,category,data_type,parent_code,source_ref,metric_keys,description\nA,T,E,narrative,,,,\n'],
  'ngx-2026.csv', { type: 'text/csv' });

describe('FrameworkContentTab (Phase 3)', () => {
  it('checks the file first, shows what would change, then imports the version', async () => {
    const sent = [];
    fakeApi({
      'GET /compliance/frameworks': () => ok(frameworks),
      'GET /compliance/frameworks/NGX/content-versions': () => ok(versions),
      'POST /platform/frameworks/NGX/content': async (_b, req) => {
        const form = Object.fromEntries((await req.formData()).entries());
        sent.push(form);
        const applied = form.dryRun === 'false';
        return ok({ framework: 'NGX', versionLabel: '2026', applied, rows: 1, added: ['A'], changed: [], retired: ['3.1', '3.2'], unchanged: 0, errors: [] });
      },
    });
    renderWithStore(<FrameworkContentTab />, { preloadedState: authenticated(['framework_content:import', 'compliance:read']) });
    await userEvent.click(await screen.findByLabelText('Framework'));
    await userEvent.click(await screen.findByRole('option', { name: /NGX/ }));
    expect(await screen.findByRole('table', { name: 'Content versions' })).toHaveTextContent('Bundled content pack');
    await userEvent.upload(screen.getByLabelText('Content file'), csv());
    await userEvent.type(screen.getByLabelText('Version label'), '2026');
    await userEvent.type(screen.getByLabelText('Effective from'), '2026-10-01');
    await userEvent.type(screen.getByLabelText('Source instrument'), 'NGX revised guidelines 2026');
    expect(screen.getByRole('button', { name: 'Import version' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Check file' }));
    const check = await screen.findByLabelText('Import check');
    expect(check).toHaveTextContent('Nothing has been imported yet');
    expect(check).toHaveTextContent('Retired 2 (3.1, 3.2)');
    expect(sent[0]).toMatchObject({ versionLabel: '2026', effectiveFrom: '2026-10-01', source: 'NGX revised guidelines 2026', smeVerified: 'false', dryRun: 'true' });
    expect(sent[0].file).toBeTruthy(); // the multipart file part (its name doesn't survive jsdom's Request)
    await userEvent.click(screen.getByRole('button', { name: 'Import version' }));
    await waitFor(() => expect(sent[1]?.dryRun).toBe('false'));
    expect(await screen.findByLabelText('Import check')).toHaveTextContent('Version 2026 imported');
  });

  it('lists the file problems per line and imports nothing', async () => {
    fakeApi({
      'GET /compliance/frameworks': () => ok(frameworks),
      'GET /compliance/frameworks/NGX/content-versions': () => ok([]),
      'POST /platform/frameworks/NGX/content': () => ok({ framework: 'NGX', versionLabel: '2026', applied: false, rows: 2, added: [], changed: [], retired: [], unchanged: 0,
        errors: [{ line: 3, field: 'category', message: 'must be E, S, G or GEN' }] }),
    });
    renderWithStore(<FrameworkContentTab />, { preloadedState: authenticated(['framework_content:import', 'compliance:read']) });
    await userEvent.click(await screen.findByLabelText('Framework'));
    await userEvent.click(await screen.findByRole('option', { name: /NGX/ }));
    await userEvent.upload(screen.getByLabelText('Content file'), csv());
    await userEvent.type(screen.getByLabelText('Version label'), '2026');
    await userEvent.type(screen.getByLabelText('Effective from'), '2026-10-01');
    await userEvent.type(screen.getByLabelText('Source instrument'), 'x');
    await userEvent.click(screen.getByRole('button', { name: 'Check file' }));
    const problems = await screen.findByRole('table', { name: 'File problems' });
    expect(within(problems).getByText('must be E, S, G or GEN')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Import version' })).toBeDisabled();
  });

  it('mirrors the server checks', () => {
    const base = { code: 'NGX', file: csv(), versionLabel: '1', effectiveFrom: '2026-01-01', effectiveTo: '', source: 's', smeVerified: false, verifiedBy: '' };
    const fields = (v) => (contentImportSchema.safeParse({ ...base, ...v }).error?.issues ?? []).map((i) => i.path[0]);
    expect(fields({})).toEqual([]);
    expect(fields({ versionLabel: '' })).toEqual(['versionLabel']);
    expect(fields({ versionLabel: 'x'.repeat(51) })).toEqual(['versionLabel']);
    expect(fields({ effectiveTo: '2025-12-31' })).toEqual(['effectiveTo']);
    expect(fields({ source: '' })).toEqual(['source']);
    expect(fields({ smeVerified: true })).toEqual(['verifiedBy']);
    expect(fields({ file: new File(['x'], 'x.txt') })).toEqual(['file']);
    expect(fields({ file: null })).toEqual(['file']);
  });
});
