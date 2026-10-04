import { describe, it, expect } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import SupplyChainPage from './SupplyChainPage';
import SupplierQuestionnairePortal from './SupplierQuestionnairePortal';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';
import { emptySupplier, emptyTemplate, emptyQuestion, supplierSchema, templateSchema } from '../schemas';

const overview = { suppliers: 3, active: 2, assessed: 1, byRisk: { high: 1, medium: 0, low: 1, unassessed: 1 }, averageScore: 61.2, openActions: 2,
  overdueActions: 1, awaitingReview: 1, spendBase: 2500000, localSpendPct: 80, baseCurrency: 'NGN', scope3Cat1Tonnes: 1.48, sectorAverages: {}, year: 2026 };
const ALL = ['supply_chain:read', 'supply_chain:create', 'supply_chain:update', 'supply_chain:approve', 'carbon_data:create', 'reference_data:read'];
const questions = [
  { id: 'q1', pillar: 'E', question: 'Do you measure energy use?', answerType: 'yes_no', positiveAnswer: 'yes', weight: 3, evidenceRequired: false },
  { id: 'q2', pillar: 'S', question: 'Do you have a health and safety policy?', answerType: 'yes_no', positiveAnswer: 'yes', weight: 2, evidenceRequired: true },
];
const questionnaire = {
  id: 'qn1', supplierId: 's1', supplierName: 'Okoro Fabrication', templateName: 'SME screen', title: 'SME screen', status: 'submitted', dueDate: '2026-10-20',
  score: 60, scoreE: 100, scoreS: 0, scoreG: null, riskLevel: 'medium', questions,
  answers: [{ questionId: 'q1', choice: 'yes' }, { questionId: 'q2', choice: 'yes' }], gaps: [{ questionId: 'q2', reason: 'evidence required but not attached' }],
  actions: ['review', 'return'], version: 3,
};

describe('Supply chain page', () => {
  it('sends a questionnaire and shows the link once', async () => {
    let sent;
    fakeApi({
      'GET /supply-chain/overview': () => ok(overview),
      'GET /supply-chain/suppliers': () => ok([{ id: 's1', name: 'Okoro Fabrication', tier: 'tier_1', onboarding: 'simplified', status: 'pending', countryCode: 'NG', local: true, openActions: 0 }]),
      'GET /supply-chain/questionnaires': () => ok([]),
      'GET /supply-chain/templates': () => ok([{ id: 't1', name: 'SME screen', kind: 'simplified', status: 'published', questions, weightE: 40, weightS: 30, weightG: 30, sent: 0 },
        { id: 't2', name: 'Draft screen', kind: 'full', status: 'draft', questions: [], weightE: 40, weightS: 30, weightG: 30, sent: 0 }]),
      'POST /supply-chain/questionnaires': (body) => { sent = body; return [201, { success: true, data: { questionnaireId: 'qn1', link: 'http://app/supplier-questionnaire/abc', emailStatus: 'no_email' } }]; },
      'GET /supply-chain/questionnaires/qn1': () => ok({ ...questionnaire, status: 'sent', score: null, gaps: [], answers: [], actions: ['resend', 'cancel'] }),
    });
    renderWithStore(<SupplyChainPage />, { preloadedState: authenticated(ALL) });
    expect(await screen.findByLabelText('Supply chain overview')).toHaveTextContent('80%');
    await userEvent.click(screen.getByRole('tab', { name: 'Questionnaires' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Send questionnaire' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByLabelText('Template'));
    expect(screen.queryByRole('option', { name: /Draft screen/ })).not.toBeInTheDocument();
    await userEvent.click(await screen.findByRole('option', { name: 'SME screen (simplified)' }));
    await userEvent.click(within(dialog).getByLabelText('Supplier'));
    await userEvent.click(await screen.findByRole('option', { name: 'Okoro Fabrication' }));
    await userEvent.type(within(dialog).getByLabelText('Due date'), '2099-01-31');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Send' }));
    await waitFor(() => expect(sent).toEqual({ supplierId: 's1', templateId: 't1', dueDate: '2099-01-31', title: null }));
    expect(await screen.findByLabelText('Link to send the supplier')).toHaveValue('http://app/supplier-questionnaire/abc');
    expect(screen.getByText(/no email address: send the link yourself/)).toBeInTheDocument();
  });

  it('reviews a scorecard and raises a corrective action from a gap', async () => {
    let raised;
    fakeApi({
      'GET /supply-chain/overview': () => ok(overview),
      'GET /supply-chain/suppliers': () => ok([{ id: 's1', name: 'Okoro Fabrication', tier: 'tier_1', onboarding: 'simplified', status: 'active', openActions: 0 }]),
      'GET /supply-chain/questionnaires': () => ok([{ ...questionnaire, questions: [], answers: [], gaps: [] }]),
      'GET /supply-chain/questionnaires/qn1': () => ok(questionnaire),
      'GET /supply-chain/people': () => ok([{ id: 'u1', name: 'Amina Bello' }]),
      'POST /supply-chain/actions': (body) => { raised = body; return [201, { success: true, data: { ...body, id: 'a1' } }]; },
    });
    renderWithStore(<SupplyChainPage />, { preloadedState: authenticated(ALL) });
    await userEvent.click(await screen.findByRole('tab', { name: 'Questionnaires' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Open SME screen for Okoro Fabrication' }));
    const drawer = await screen.findByRole('region', { name: 'Questionnaire' });
    expect(within(drawer).getByLabelText('Scorecard')).toHaveTextContent('60');
    expect(within(drawer).getByText('evidence required but not attached')).toBeInTheDocument();
    expect(within(drawer).getByRole('button', { name: 'Accept scorecard' })).toBeEnabled();
    await userEvent.click(within(drawer).getByRole('button', { name: 'Raise action' }));
    const dialog = await screen.findByRole('dialog', { name: 'Raise corrective action' });
    expect(within(dialog).getByLabelText('Title')).toHaveValue('Do you have a health and safety policy?');
    await userEvent.type(within(dialog).getByLabelText('Due date'), '2099-03-31');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Raise action' }));
    await waitFor(() => expect(raised).toMatchObject({ supplierId: 's1', questionnaireId: 'qn1', questionId: 'q2', severity: 'medium', dueDate: '2099-03-31' }));
  });
});

describe('Supplier questionnaire link', () => {
  it('lets the supplier answer with evidence and submit without an account', async () => {
    let saved;
    let submitted = false;
    const view = (status = 'sent') => ok({ buyer: 'Atheris Demo', supplierName: 'Okoro Fabrication', title: 'SME screen', status, dueDate: '2026-10-20',
      questions, answers: [] });
    fakeApi({
      'GET /public/supplier-questionnaires/tok123': () => (submitted ? view('submitted') : view()),
      'POST /public/supplier-questionnaires/tok123/evidence': () => [201, { success: true, data: { id: 'f1', originalName: 'hs.pdf' } }],
      'PUT /public/supplier-questionnaires/tok123/answers': (body) => { saved = body; return view('in_progress'); },
      'POST /public/supplier-questionnaires/tok123/submit': () => { submitted = true; return view('submitted'); },
    });
    renderWithStore(<Routes><Route path="/supplier-questionnaire/:token" element={<SupplierQuestionnairePortal />} /></Routes>,
      { route: '/supplier-questionnaire/tok123' });
    expect(await screen.findByText('Private link for Okoro Fabrication. No account needed.')).toBeInTheDocument();
    const q1 = screen.getByRole('radiogroup', { name: 'Answer 1' });
    await userEvent.click(within(q1).getByLabelText('Yes'));
    const q2 = screen.getByRole('radiogroup', { name: 'Answer 2' });
    await userEvent.click(within(q2).getByLabelText('Yes'));
    const file = new File(['%PDF-1.7 policy'], 'hs.pdf', { type: 'application/pdf' });
    await userEvent.upload(screen.getByLabelText('Evidence 2'), file);
    expect(await screen.findByText('hs.pdf')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Submit answers' }));
    await waitFor(() => expect(saved.answers).toEqual([
      { questionId: 'q1', choice: 'yes', number: null, text: null, comment: null, fileId: null },
      { questionId: 'q2', choice: 'yes', number: null, text: null, comment: null, fileId: 'f1' },
    ]));
    expect(await screen.findByLabelText('Submitted')).toBeInTheDocument();
  });

  it('says plainly when a link no longer works', async () => {
    fakeApi({ 'GET /public/supplier-questionnaires/old': () => [404, { status: 404, errorCode: 'ESG-404' }] });
    renderWithStore(<Routes><Route path="/supplier-questionnaire/:token" element={<SupplierQuestionnairePortal />} /></Routes>,
      { route: '/supplier-questionnaire/old' });
    expect(await screen.findByText(/isn't valid any more/)).toBeInTheDocument();
  });
});

describe('Supply chain form rules mirror the server', () => {
  it('full onboarding needs an email; template weights add to 100 and only yes/no and scale questions are scored', () => {
    expect(supplierSchema.safeParse({ ...emptySupplier, name: 'x', onboarding: 'full' }).error.issues.map((i) => i.path[0])).toEqual(['contactEmail']);
    const t = templateSchema.safeParse({ ...emptyTemplate, name: 'x', weightG: '20',
      questions: [{ ...emptyQuestion, question: 'q', positiveAnswer: '' }, { ...emptyQuestion, question: 'n', answerType: 'number', positiveAnswer: '', weight: '2' }] });
    expect(t.error.issues.map((i) => i.path.join('.'))).toEqual(expect.arrayContaining(['weightE', 'questions.0.positiveAnswer', 'questions.1.weight']));
  });
});
