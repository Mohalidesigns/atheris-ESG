import { useState } from 'react';

/**
 * Page / size / sort state for a server-backed list, as query params in the TRD §6.4.1 format
 * (?page=0&size=20&sort=createdAt,desc). Filters reset to page 0 via resetPage().
 */
export function useServerTable({ defaultSort = null, defaultSize = 20 } = {}) {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(defaultSize);
  const [sort, setSort] = useState(defaultSort);

  const toggleSort = (field) => {
    setPage(0);
    setSort((current) => (current?.field === field
      ? { field, direction: current.direction === 'asc' ? 'desc' : 'asc' }
      : { field, direction: 'asc' }));
  };

  return {
    page, size, sort, setPage,
    setSize: (n) => { setSize(n); setPage(0); },
    toggleSort,
    resetPage: () => setPage(0),
    params: { page, size, ...(sort ? { sort: `${sort.field},${sort.direction}` } : {}) },
  };
}

/** Accepts both Spring page shapes: PageImpl ({content, number, size, totalElements}) and PagedModel ({content, page: {...}}). */
export function normalizePage(data) {
  if (!data) return { content: [], total: 0, page: 0 };
  const meta = typeof data.page === 'object' && data.page !== null ? data.page : data;
  return {
    content: data.content ?? [],
    total: meta.totalElements ?? 0,
    page: typeof data.page === 'number' ? data.page : (meta.number ?? 0),
  };
}
