import { describe, it, expect, afterEach, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, Route, Routes } from 'react-router-dom';
import MainLayout from './MainLayout';
import { authenticated, renderWithStore } from '../../test/renderWithStore';
import { fakeApi, ok } from '../../test/fakeApi';

/** jsdom has no layout: pretend the viewport is narrower than MUI's md breakpoint (900 px). */
const narrowScreen = () => vi.stubGlobal('matchMedia', (query) => ({
  matches: /max-width:\s*899\.95px/.test(query), media: query, addListener() {}, removeListener() {},
  addEventListener() {}, removeEventListener() {}, onchange: null, dispatchEvent: () => false,
}));

const app = (
  <Routes>
    <Route element={<MainLayout />}>
      <Route path="/dashboard" element={<><span>dashboard page</span><Link to="/data-hub">go to data hub</Link></>} />
      <Route path="/data-hub" element={<span>data hub page</span>} />
    </Route>
  </Routes>
);
const PERMS = ['dashboard:read', 'data_points:read', 'notifications:read'];
const api = () => fakeApi({
  'GET /notifications/unread-count': () => ok({ unread: 0 }),
  'GET /notifications': () => ok({ content: [], unread: 0, page: 0, size: 10, totalElements: 0 }),
});

describe('MainLayout (Phase 2: responsive shell)', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('below 900 px the sidebar is a drawer opened from the menu button and closed by choosing a page', async () => {
    narrowScreen();
    api();
    renderWithStore(app, { preloadedState: authenticated(PERMS, { modules: ['platform', 'data_hub'] }), route: '/dashboard' });
    expect(screen.queryByRole('navigation', { name: 'Main' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Open menu' }));
    const nav = await screen.findByRole('navigation', { name: 'Main' });
    await userEvent.click(within(nav).getByText('Data Hub'));
    expect(await screen.findByText('data hub page')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('navigation', { name: 'Main' })).not.toBeInTheDocument());
  });

  it('on wide screens the sidebar is always there, without opening it', () => {
    // (The menu button is hidden from md up by CSS media queries, which jsdom doesn't evaluate; E2E covers it.)
    api();
    renderWithStore(app, { preloadedState: authenticated(PERMS, { modules: ['platform', 'data_hub'] }), route: '/dashboard' });
    expect(screen.getByRole('navigation', { name: 'Main' })).toBeInTheDocument();
  });

  it('the notifications popover closes on Escape', async () => {
    api();
    renderWithStore(app, { preloadedState: authenticated(PERMS), route: '/dashboard' });
    await userEvent.click(await screen.findByRole('button', { name: 'Notifications' }));
    expect(await screen.findByText("You're all caught up.")).toBeInTheDocument();
    await userEvent.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByText("You're all caught up.")).not.toBeInTheDocument());
  });
});
