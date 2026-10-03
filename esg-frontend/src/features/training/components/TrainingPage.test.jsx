import { describe, it, expect } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import TrainingPage from './TrainingPage';
import VerifyCertificatePage from './VerifyCertificatePage';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';
import { assignSchema, attendanceSchema, courseSchema, emptyCourse, optionList, pathSchema, toCourseBody } from '../schemas';

const LEARNER = ['training:read'];
const ADMIN = ['training:read', 'training:create', 'training:update', 'training:configure', 'files:create', 'files:read'];
const modules = [{ id: 'm1', title: 'Why ESG', kind: 'text', body: 'Environmental, social and governance.', minutes: 20 },
  { id: 'm2', title: 'Talk', kind: 'video', url: 'https://videos.example.org/esg', minutes: 15 }];
const course = { id: 'c1', code: 'ESG-101', title: 'ESG basics', description: 'What ESG means', category: 'esg_fundamentals', delivery: 'online', durationHours: 1.5,
  passingScore: 50, certificateValidityMonths: 12, prerequisiteIds: [], prerequisiteTitles: [], status: 'published', modules,
  questions: [{ id: 'q1', question: 'What does the G stand for?', options: ['Green', 'Governance'] }], enrolled: 1, passed: 0, actions: [], version: 1 };
const enrolment = (extra = {}) => ({ id: 'e1', courseId: 'c1', courseCode: 'ESG-101', courseTitle: 'ESG basics', delivery: 'online', durationHours: 1.5, userId: 'u1',
  userName: 'Esg Analyst', source: 'assigned', dueOn: '2099-01-31', overdue: false, status: 'in_progress', modulesDone: ['m1'], modulesTotal: 2, attempts: 0,
  bestScore: null, completedOn: null, hours: null, certificate: null, version: 3, ...extra });

describe('Training page', () => {
  it('a learner works through the modules, takes the quiz and gets a certificate', async () => {
    let state = enrolment();
    const calls = fakeApi({
      'GET /training/progress': () => ok({ enrollments: [state], gaps: [{ courseId: 'c9', courseTitle: 'Climate basics', pathName: 'Analyst path', state: 'not_enrolled' }],
        hoursThisYear: 4.5 }),
      'GET /training/courses/c1': () => ok(course),
      'POST /training/enrollments/e1/modules/m2/done': () => { state = enrolment({ modulesDone: ['m1', 'm2'], version: 4 }); return ok(state); },
      'POST /training/enrollments/e1/quiz': () => {
        state = enrolment({ modulesDone: ['m1', 'm2'], status: 'passed', bestScore: 100, attempts: 1, completedOn: '2026-10-03', hours: 1.5, version: 6,
          certificate: { id: 'cert1', code: 'ABCDEFGH23', status: 'valid', holderName: 'Esg Analyst', courseTitle: 'ESG basics', issuedOn: '2026-10-03' } });
        return ok(state);
      },
    });
    renderWithStore(<TrainingPage />, { preloadedState: authenticated(LEARNER) });
    expect(await screen.findByLabelText('Competency gaps')).toHaveTextContent('Climate basics — Not enrolled');
    // Learners don't get the admin tabs.
    expect(screen.queryByRole('tab', { name: 'Courses' })).not.toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: 'Team progress' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Open ESG basics' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('Finish every module to take the quiz.')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Mark Talk done' }));
    expect(calls.find((c) => c.key === 'POST /training/enrollments/e1/modules/m2/done').query).toEqual({ version: '3' });
    const quiz = await within(dialog).findByLabelText('Quiz');
    expect(within(dialog).getByRole('button', { name: 'Submit answers' })).toBeDisabled();
    await userEvent.click(within(quiz).getByLabelText('Governance'));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Submit answers' }));
    await waitFor(() => expect(calls.find((c) => c.key === 'POST /training/enrollments/e1/quiz').body).toEqual({ answers: [1], version: 4 }));
    expect(await within(dialog).findByLabelText('Quiz result')).toHaveTextContent('Passed with 100%');
    expect(within(dialog).getByRole('button', { name: 'Certificate ABCDEFGH23' })).toBeInTheDocument();
  });

  it('shows why a course can\'t be joined yet', async () => {
    fakeApi({
      'GET /training/progress': () => ok({ enrollments: [], gaps: [], hoursThisYear: 0 }),
      'GET /training/courses': () => ok([{ ...course, id: 'c2', title: 'Advanced ESG', prerequisiteTitles: ['ESG basics'] }]),
      'POST /training/courses/c2/enrol': () => [409, { detail: 'Pass ESG basics first.', errorCode: 'ESG-409-STATE' }],
    });
    renderWithStore(<TrainingPage />, { preloadedState: authenticated(LEARNER), route: '/training?tab=catalogue' });
    await userEvent.click(await screen.findByRole('tab', { name: 'Catalogue' }));
    expect(await screen.findByText('Pass first: ESG basics')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Enrol in Advanced ESG' }));
    expect(await screen.findByText('Pass ESG basics first.')).toBeInTheDocument();
  });

  it('the training team publishes drafts and sees team gaps', async () => {
    let acted;
    fakeApi({
      'GET /training/overview': () => ok({ year: 2026, published: 1, enrolled: 3, completionRate: 66.7, certificatesIssued: 2, hours: 12, hoursPerEmployee: 0.5 }),
      'GET /training/progress': () => ok({ enrollments: [], gaps: [], hoursThisYear: 0 }),
      'GET /training/courses': () => ok([{ ...course, status: 'draft', actions: ['publish'], enrolled: 0 }]),
      'POST /training/courses/c1/actions': (body) => { acted = body; return ok({ ...course }); },
      'GET /training/team': () => ok([{ userId: 'u1', name: 'Esg Analyst', roles: ['ESG_ANALYST'], required: 2, passed: 1, overdue: 1, hoursThisYear: 1.5,
        gaps: [{ courseId: 'c9', courseTitle: 'Climate basics', pathName: 'Analyst path', state: 'revoked' }] }]),
    });
    renderWithStore(<TrainingPage />, { preloadedState: authenticated(ADMIN) });
    expect(await screen.findByLabelText('Training overview')).toHaveTextContent('66.7%');
    await userEvent.click(screen.getByRole('tab', { name: 'Courses' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Publish ESG basics' }));
    await waitFor(() => expect(acted).toEqual({ action: 'publish', version: 1 }));
    await userEvent.click(screen.getByRole('tab', { name: 'Team progress' }));
    expect(await screen.findByRole('table', { name: 'Team progress' })).toHaveTextContent('Climate basics: Certificate revoked');
  });
});

describe('Certificate verification', () => {
  const open = (code) => renderWithStore(<Routes><Route path="/verify-certificate/:code" element={<VerifyCertificatePage />} /></Routes>,
    { route: `/verify-certificate/${code}` });

  it('shows a valid certificate without signing in', async () => {
    fakeApi({ 'GET /public/certificates/ABCDEFGH23': () => ok({ code: 'ABCDEFGH23', status: 'valid', holderName: 'Ada Eze', courseTitle: 'ESG basics',
      organisation: 'Atheris Demo plc', issuedOn: '2026-10-03', expiresOn: null }) });
    open('ABCDEFGH23');
    const card = await screen.findByLabelText('Certificate');
    expect(card).toHaveTextContent('This certificate is valid.');
    expect(card).toHaveTextContent('Ada Eze');
    expect(card).toHaveTextContent('Does not expire');
  });

  it('says when a certificate was revoked, and when a code is unknown', async () => {
    fakeApi({
      'GET /public/certificates/ABCDEFGH23': () => ok({ code: 'ABCDEFGH23', status: 'revoked', holderName: 'Ada Eze', courseTitle: 'ESG basics',
        organisation: 'Atheris Demo plc', issuedOn: '2026-10-03', expiresOn: '2027-10-03' }),
    });
    open('ABCDEFGH23');
    expect(await screen.findByLabelText('Certificate')).toHaveTextContent('revoked by the organisation');
  });

  it('an unknown code is reported plainly', async () => {
    fakeApi({ 'GET /public/certificates/ZZZZZZZZZZ': () => [404, { detail: 'Certificate not found' }] });
    open('ZZZZZZZZZZ');
    expect(await screen.findByText(/No certificate has this code/)).toBeInTheDocument();
  });
});

describe('Training form rules mirror the server', () => {
  const issues = (schema, value) => schema.safeParse(value).error?.issues.map((i) => i.path.join('.')) ?? [];
  const ok_ = { ...emptyCourse, code: 'ESG-101', title: 'ESG basics', durationHours: '1.5',
    modules: [{ title: 'Why', kind: 'text', body: 'Text', fileId: '', url: '', minutes: '20' }],
    questions: [{ question: 'G?', options: 'Green\nGovernance\n', correctIndex: '1' }] };

  it('courses: code pattern, hours to one decimal, pass mark, validity, module content by kind, answers among the options', () => {
    expect(issues(courseSchema, ok_)).toEqual([]);
    expect(issues(courseSchema, { ...ok_, code: 'esg 101' })).toEqual(['code']);
    expect(issues(courseSchema, { ...ok_, durationHours: '0' })).toEqual(['durationHours']);
    expect(issues(courseSchema, { ...ok_, durationHours: '1.25' })).toEqual(['durationHours']);
    expect(issues(courseSchema, { ...ok_, passingScore: '101' })).toEqual(['passingScore']);
    expect(issues(courseSchema, { ...ok_, certificateValidityMonths: '0' })).toEqual(['certificateValidityMonths']);
    expect(issues(courseSchema, { ...ok_, modules: [{ ...ok_.modules[0], body: ' ' }] })).toEqual(['modules.0.body']);
    expect(issues(courseSchema, { ...ok_, modules: [{ ...ok_.modules[0], kind: 'video', url: 'http://x' }] })).toEqual(['modules.0.url']);
    expect(issues(courseSchema, { ...ok_, modules: [{ ...ok_.modules[0], kind: 'pdf' }] })).toEqual(['modules.0.fileId']);
    expect(issues(courseSchema, { ...ok_, modules: [{ ...ok_.modules[0], minutes: '601' }] })).toEqual(['modules.0.minutes']);
    expect(issues(courseSchema, { ...ok_, questions: [{ ...ok_.questions[0], options: 'Only one' }] })).toEqual(expect.arrayContaining(['questions.0.options']));
    expect(issues(courseSchema, { ...ok_, questions: [{ ...ok_.questions[0], correctIndex: '2' }] })).toEqual(['questions.0.correctIndex']);
  });

  it('sends only the content each module kind uses, and options as a list', () => {
    expect(optionList(' A \n\nB\n')).toEqual(['A', 'B']);
    const body = toCourseBody({ ...ok_, modules: [{ title: 'Clip', kind: 'video', body: 'stale', fileId: 'f', url: ' https://v.example/x ', minutes: '5' }] });
    expect(body.modules[0]).toEqual({ title: 'Clip', kind: 'video', body: null, fileId: null, url: 'https://v.example/x', minutes: 5 });
    expect(body.questions[0]).toEqual({ question: 'G?', options: ['Green', 'Governance'], correctIndex: 1 });
    expect(body.certificateValidityMonths).toBeNull();
  });

  it('paths, assignment and attendance', () => {
    expect(issues(pathSchema, { name: 'P', description: '', targetRole: 'INVESTOR_VIEWER', courseIds: [], status: 'active' })).toEqual(['targetRole', 'courseIds']);
    expect(issues(assignSchema('2026-10-03'), { userIds: [], dueOn: '2026-10-02' })).toEqual(['userIds', 'dueOn']);
    const att = attendanceSchema('2026-10-03');
    expect(issues(att, { completedOn: '2026-10-04', hours: '0', passed: true, score: '101' })).toEqual(['completedOn', 'hours', 'score']);
    expect(issues(att, { completedOn: '2026-10-03', hours: '3.5', passed: true, score: '' })).toEqual([]);
  });
});
