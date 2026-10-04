import { describe, it, expect } from 'vitest';
import { renderHook, act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import DataTable from './DataTable';
import { normalizePage, useServerTable } from './useServerTable';

const columns = [
  { field: 'name', header: 'Name', sortable: true },
  { field: 'value', header: 'Value', align: 'right', render: (r) => `${r.value} kWh` },
];
const ok = (content, extra = {}) => ({ data: { content, totalElements: content.length, number: 0, size: 20, ...extra }, isLoading: false, isFetching: false, isError: false });

function Harness({ query, table }) {
  return <DataTable columns={columns} query={query} table={table} ariaLabel="Things" emptyTitle="No things" />;
}

describe('useServerTable', () => {
  it('builds TRD §6.4.1 params and toggles sort direction', () => {
    const { result } = renderHook(() => useServerTable({ defaultSort: { field: 'createdAt', direction: 'desc' } }));
    expect(result.current.params).toEqual({ page: 0, size: 20, sort: 'createdAt,desc' });
    act(() => result.current.setPage(3));
    act(() => result.current.toggleSort('name'));
    expect(result.current.params).toEqual({ page: 0, size: 20, sort: 'name,asc' });
    act(() => result.current.toggleSort('name'));
    expect(result.current.params.sort).toBe('name,desc');
    act(() => result.current.setPage(2));
    act(() => result.current.setSize(50));
    expect(result.current.params).toMatchObject({ page: 0, size: 50 });
  });

  it('reads both Spring page shapes', () => {
    expect(normalizePage({ content: [1], totalElements: 9, number: 2 })).toEqual({ content: [1], total: 9, page: 2 });
    expect(normalizePage({ content: [1], page: { totalElements: 9, number: 2, size: 1 } })).toEqual({ content: [1], total: 9, page: 2 });
    expect(normalizePage(undefined)).toEqual({ content: [], total: 0, page: 0 });
  });
});

describe('DataTable', () => {
  it('renders rows, custom cells and sort headers that call back', async () => {
    const { result } = renderHook(() => useServerTable());
    render(<Harness query={ok([{ id: 1, name: 'Lagos', value: 5 }])} table={result.current} />);
    expect(screen.getByRole('table', { name: 'Things' })).toBeInTheDocument();
    expect(screen.getByText('5 kWh')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Name' }));
    expect(result.current.params.sort).toBe('name,asc');
  });

  it('shows empty, loading and error states', async () => {
    const table = { size: 20, sort: null, setPage: () => {}, setSize: () => {}, toggleSort: () => {} };
    const { rerender } = render(<Harness query={ok([])} table={table} />);
    expect(screen.getByText('No things')).toBeInTheDocument();
    rerender(<Harness query={{ isLoading: true }} table={table} />);
    expect(screen.getByRole('status')).toBeInTheDocument();
    let retried = false;
    rerender(<Harness query={{ isError: true, error: { status: 500, data: { detail: 'Database unavailable' } }, refetch: () => { retried = true; } }} table={table} />);
    expect(screen.getByText('Database unavailable')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(retried).toBe(true);
  });
});
