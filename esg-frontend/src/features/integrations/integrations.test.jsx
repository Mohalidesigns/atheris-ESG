import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { authenticated, renderWithStore } from '../../test/renderWithStore';
import { fakeApi, ok } from '../../test/fakeApi';
import IntegrationsTab from './components/IntegrationsTab';
import PlatformRequestsTab from './components/PlatformRequestsTab';
import { apiKeySchema, requestSchema, statusSchema, toApiKeyBody, toRequestBody, webhookSchema } from './schemas';

const ADMIN = ['tenant_settings:read', 'integrations:read', 'integrations:manage', 'data_points:create', 'data_points:read'];
const connectors = [
  { key: 'api_push', name: 'REST API and IoT push (API key)', category: 'iot', method: 'push', description: 'Your systems send readings.',
    dataProvided: 'Any metric', availability: 'available', openRequest: null },
  { key: 'sage', name: 'Sage (50, 300, Intacct)', category: 'accounting', method: 'api', description: 'Reads the purchase ledger.',
    dataProvided: 'Spend', availability: 'on_request', openRequest: null },
  { key: 'workday', name: 'Workday HCM', category: 'hris', method: 'api', description: 'Reads workforce data.', dataProvided: 'Workforce',
    availability: 'on_request', openRequest: { id: 'r0', reference: 'INT-2026-0001', status: 'in_review' } },
];
const request = { id: 'r1', reference: 'INT-2026-0002', connectorKey: 'sage', connectorName: 'Sage (50, 300, Intacct)', status: 'requested',
  statusNote: null, createdAt: '2026-10-03T09:00:00Z', createdByName: 'Ada Eze', version: 0 };

describe('Integrations › Connectors', () => {
  it('shows what is available now, what is on request, and sends a request with the details the team needs', async () => {
    let sent;
    fakeApi({
      'GET /integrations': () => ok([]), 'GET /integrations/connectors': () => ok(connectors),
      'GET /integrations/requests': () => ok(sent ? [request] : []),
      'POST /integrations/requests': (body) => { sent = body; return [201, { success: true, data: request, message: 'Request sent to the Atheris team' }]; },
    });
    renderWithStore(<IntegrationsTab />, { preloadedState: authenticated(ADMIN) });
    await userEvent.click(await screen.findByRole('tab', { name: 'Connectors' }));
    const api = await screen.findByLabelText('REST API and IoT push (API key)');
    expect(api).toHaveTextContent('Available now');
    expect(screen.getByLabelText('Workday HCM')).toHaveTextContent('In review · INT-2026-0001');
    expect(within(screen.getByLabelText('Sage (50, 300, Intacct)')).getByText('On request')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Request Sage (50, 300, Intacct)' }));
    const dialog = await screen.findByRole('dialog', { name: 'Request Sage (50, 300, Intacct)' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Send request' }));
    expect(await within(dialog).findByText('Describe the system (product, version, hosted or on-premise)')).toBeInTheDocument();
    expect(within(dialog).getByText('choose at least one kind of data')).toBeInTheDocument();
    await userEvent.type(within(dialog).getByLabelText('Your system'), 'Sage 300 2024, on-premise');
    await userEvent.click(within(dialog).getByLabelText('Procurement spend (Scope 3)'));
    await userEvent.type(within(dialog).getByLabelText('Contact person'), 'Ifeoma Finance');
    await userEvent.type(within(dialog).getByLabelText('Contact email'), 'ifeoma@example.org');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Send request' }));
    await waitFor(() => expect(sent).toEqual({ connectorKey: 'sage', systemDetails: 'Sage 300 2024, on-premise', dataNeeded: ['procurement_spend'],
      frequency: 'monthly', contactName: 'Ifeoma Finance', contactEmail: 'ifeoma@example.org', contactPhone: null, notes: null }));
    const table = await screen.findByRole('table', { name: 'Integration requests' });
    expect(within(table).getByText('INT-2026-0002')).toBeInTheDocument();
    expect(within(table).getByRole('button', { name: 'Withdraw INT-2026-0002' })).toBeInTheDocument();
  });

  it('read-only viewers can see the catalogue but not request', async () => {
    fakeApi({ 'GET /integrations': () => ok([]), 'GET /integrations/connectors': () => ok(connectors), 'GET /integrations/requests': () => ok([]) });
    renderWithStore(<IntegrationsTab />, { preloadedState: authenticated(['integrations:read']) });
    await userEvent.click(await screen.findByRole('tab', { name: 'Connectors' }));
    await screen.findByLabelText('Sage (50, 300, Intacct)');
    expect(screen.queryByRole('button', { name: /^Request / })).not.toBeInTheDocument();
  });
});

describe('Integrations › API keys and webhooks', () => {
  it('creates a key with the permissions its creator holds and shows the key once', async () => {
    let body;
    fakeApi({
      'GET /integrations': () => ok([]), 'GET /integrations/connectors': () => ok(connectors), 'GET /integrations/requests': () => ok([]),
      'GET /integrations/api-keys': () => ok([]),
      'GET /integrations/api-keys/scopes': () => ok([
        { permission: 'data_points:create', description: 'Send data points (saved as drafts for review)', grantable: true },
        { permission: 'carbon_data:read', description: 'Read emissions', grantable: false },
      ]),
      'POST /integrations/api-keys': (b) => { body = b; return [201, { success: true, data: { key: 'atk_abc123_secretvalue', apiKey: { id: 'k1', name: b.name } } }]; },
    });
    renderWithStore(<IntegrationsTab />, { preloadedState: authenticated(ADMIN) });
    await userEvent.click(await screen.findByRole('tab', { name: 'API keys' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Create API key' }));
    const dialog = await screen.findByRole('dialog', { name: 'Create an API key' });
    expect(within(dialog).getByLabelText(/Read emissions/)).toBeDisabled();
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Flow station gateway');
    await userEvent.click(within(dialog).getByLabelText(/Send data points/));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create key' }));
    await waitFor(() => expect(body).toEqual({ name: 'Flow station gateway', scopes: ['data_points:create'], expiresAt: null }));
    const shown = await screen.findByRole('dialog', { name: 'API key "Flow station gateway"' });
    expect(within(shown).getByLabelText('API key')).toHaveTextContent('atk_abc123_secretvalue');
    expect(shown).toHaveTextContent("won't be shown again");
  });

  it('adds a webhook, shows its secret once, and reports a test delivery', async () => {
    const hook = { id: 'w1', name: 'ERP bridge', url: 'https://erp.example.org/hooks/esg', events: ['data_point.approved'], active: true,
      consecutiveFailures: 0, version: 0 };
    let created = false;
    fakeApi({
      'GET /integrations': () => ok([]), 'GET /integrations/connectors': () => ok(connectors), 'GET /integrations/requests': () => ok([]),
      'GET /integrations/webhooks': () => ok(created ? [hook] : []),
      'GET /integrations/webhooks/events': () => ok([{ type: 'data_point.approved', description: 'A data point was approved' }]),
      'POST /integrations/webhooks': () => { created = true; return [201, { success: true, data: { webhook: hook, secret: 'whsec_abc' } }]; },
      'POST /integrations/webhooks/w1/test': () => ok({ id: 'd1', status: 'pending', attempts: 1, lastStatusCode: 503, lastError: 'The endpoint answered HTTP 503' }),
    });
    renderWithStore(<IntegrationsTab />, { preloadedState: authenticated(ADMIN) });
    await userEvent.click(await screen.findByRole('tab', { name: 'Webhooks' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Add webhook' }));
    const dialog = await screen.findByRole('dialog', { name: 'Add a webhook' });
    await userEvent.type(within(dialog).getByLabelText('Name'), 'ERP bridge');
    await userEvent.type(within(dialog).getByLabelText('Endpoint URL'), 'not a url');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add webhook' }));
    expect(await within(dialog).findByText('is not a valid URL')).toBeInTheDocument();
    expect(within(dialog).getByText('choose at least one event')).toBeInTheDocument();
    await userEvent.clear(within(dialog).getByLabelText('Endpoint URL'));
    await userEvent.type(within(dialog).getByLabelText('Endpoint URL'), 'https://erp.example.org/hooks/esg');
    await userEvent.click(within(dialog).getByLabelText(/A data point was approved/));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add webhook' }));
    const secret = await screen.findByRole('dialog', { name: 'Signing secret for ERP bridge' });
    expect(within(secret).getByLabelText('Signing secret')).toHaveTextContent('whsec_abc');
    await userEvent.click(within(secret).getByRole('button', { name: 'Done' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Send test' }));
    expect(await screen.findByText('ERP bridge: The endpoint answered HTTP 503. It will be retried.')).toBeInTheDocument();
  });
});

describe('Platform › Integration requests', () => {
  it('moves a request on, and declining needs a reason', async () => {
    let change;
    fakeApi({
      'GET /platform/integration-requests': () => ok([{ ...request, tenantId: 't-2', tenantName: 'Delta Oil', systemDetails: 'Sage 300', dataNeeded: ['procurement_spend'],
        frequency: 'monthly', contactName: 'Ifeoma', contactEmail: 'ifeoma@example.org' }]),
      'POST /platform/integration-requests/r1/status': (b) => { change = b; return ok({ ...request, status: b.status }); },
    });
    renderWithStore(<PlatformRequestsTab />, { preloadedState: authenticated(['tenants:read', 'tenants:update']) });
    await userEvent.click(await screen.findByRole('button', { name: 'Update INT-2026-0002' }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent('Delta Oil: Sage 300. Data: Procurement spend (Scope 3), monthly.');
    await userEvent.click(within(dialog).getByLabelText('New status'));
    await userEvent.click(await screen.findByRole('option', { name: 'Declined' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Update' }));
    expect(await within(dialog).findByText('Say why the request is declined')).toBeInTheDocument();
    await userEvent.type(within(dialog).getByLabelText('Note for the client'), 'Sage 300 on-premise has no API we can reach');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Update' }));
    await waitFor(() => expect(change).toEqual({ status: 'declined', note: 'Sage 300 on-premise has no API we can reach', version: 0 }));
  });
});

describe('Integration form rules mirror the server', () => {
  const issues = (schema, v) => schema.safeParse(v).error?.issues.map((i) => i.path.join('.')) ?? [];

  it('requests, API keys, webhooks and status changes', () => {
    const r = { systemDetails: 'SAP B1', dataNeeded: ['energy_fuel'], frequency: 'monthly', contactName: 'A', contactEmail: 'a@b.co', contactPhone: '', notes: '' };
    expect(issues(requestSchema, r)).toEqual([]);
    expect(issues(requestSchema, { ...r, systemDetails: 'x'.repeat(501), contactPhone: 'call me', contactEmail: 'nope', dataNeeded: [] }))
      .toEqual(['systemDetails', 'dataNeeded', 'contactEmail', 'contactPhone']);
    expect(toRequestBody('sap_business_one', { ...r, contactPhone: ' +234 801 ' })).toMatchObject({ connectorKey: 'sap_business_one', contactPhone: '+234 801', notes: null });

    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-03T12:00:00Z'));
    const keys = apiKeySchema(new Date());
    expect(issues(keys, { name: 'G', scopes: ['data_points:read'], expiresOn: '' })).toEqual([]);
    expect(issues(keys, { name: '', scopes: [], expiresOn: '2026-10-03' })).toEqual(['name', 'scopes', 'expiresOn']);
    expect(issues(keys, { name: 'G', scopes: ['x'], expiresOn: '2028-12-31' })).toEqual(['expiresOn']);
    expect(toApiKeyBody({ name: ' G ', scopes: ['x'], expiresOn: '2027-01-31' })).toEqual({ name: 'G', scopes: ['x'], expiresAt: '2027-01-31T23:59:59.000Z' });
    vi.useRealTimers();

    expect(issues(webhookSchema, { name: 'W', url: 'ftp://x', events: [], active: true })).toEqual(['url', 'events']);
    expect(issues(webhookSchema, { name: 'W', url: 'https://x.example/h', events: ['report.approved'], active: false })).toEqual([]);
    expect(issues(statusSchema, { status: 'declined', note: '' })).toEqual(['note']);
    expect(issues(statusSchema, { status: 'active', note: '' })).toEqual([]);
  });
});
