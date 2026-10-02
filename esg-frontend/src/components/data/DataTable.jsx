import {
  Card, Checkbox, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, TableSortLabel,
} from '@mui/material';
import { EmptyState, ErrorState, LoadingState } from '../states/States';
import { normalizePage } from './useServerTable';

/**
 * Server-backed table (WP-16): the server pages, sorts and filters; this renders one page with loading,
 * empty and error states. `query` is an RTK Query result, `table` comes from useServerTable().
 *
 *   columns: [{ field, header, sortable?, align?, render?: (row) => node }]
 *   selection (optional, Phase 3 bulk actions): { selected: Map<id, row>, onChange(nextMap), rowLabel(row) }
 *     adds a checkbox per row and a "select this page" checkbox; selection survives paging.
 */
export default function DataTable({
  columns, query, table, ariaLabel, emptyTitle = 'No records yet', emptyMessage, getRowId = (r) => r.id,
  rowsPerPageOptions = [10, 20, 50], selection,
}) {
  const { data, isLoading, isFetching, isError, error, refetch } = query;
  const { content, total, page } = normalizePage(data);
  const selectedOnPage = selection ? content.filter((r) => selection.selected.has(getRowId(r))).length : 0;
  const toggle = (row) => {
    const next = new Map(selection.selected);
    if (next.has(getRowId(row))) next.delete(getRowId(row)); else next.set(getRowId(row), row);
    selection.onChange(next);
  };
  const togglePage = (checked) => {
    const next = new Map(selection.selected);
    content.forEach((r) => (checked ? next.set(getRowId(r), r) : next.delete(getRowId(r))));
    selection.onChange(next);
  };

  return (
    <Card>
      {isError && <ErrorState error={error} onRetry={refetch} />}
      {isLoading && <LoadingState />}
      {!isLoading && !isError && content.length === 0 && <EmptyState title={emptyTitle}>{emptyMessage}</EmptyState>}
      {content.length > 0 && (
        <TableContainer sx={{ opacity: isFetching ? 0.6 : 1, transition: 'opacity .2s' }}>
          <Table size="small" aria-label={ariaLabel} aria-busy={isFetching}
            data-sticky-actions={['actions', 'open'].includes(columns.at(-1)?.field) ? '' : undefined}>
            <TableHead>
              <TableRow>
                {selection && (
                  <TableCell padding="checkbox">
                    <Checkbox size="small" inputProps={{ 'aria-label': 'Select all on this page' }}
                      checked={selectedOnPage > 0 && selectedOnPage === content.length}
                      indeterminate={selectedOnPage > 0 && selectedOnPage < content.length}
                      onChange={(e) => togglePage(e.target.checked)} />
                  </TableCell>
                )}
                {columns.map((c) => (
                  <TableCell key={c.field} align={c.align}
                    sortDirection={table.sort?.field === c.field ? table.sort.direction : false}>
                    {c.sortable ? (
                      <TableSortLabel active={table.sort?.field === c.field}
                        direction={table.sort?.field === c.field ? table.sort.direction : 'asc'}
                        onClick={() => table.toggleSort(c.field)}>
                        {c.header}
                      </TableSortLabel>
                    ) : c.header}
                  </TableCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {content.map((row) => (
                <TableRow key={getRowId(row)} hover selected={selection?.selected.has(getRowId(row))}>
                  {selection && (
                    <TableCell padding="checkbox">
                      <Checkbox size="small" checked={selection.selected.has(getRowId(row))} onChange={() => toggle(row)}
                        inputProps={{ 'aria-label': `Select ${selection.rowLabel ? selection.rowLabel(row) : getRowId(row)}` }} />
                    </TableCell>
                  )}
                  {columns.map((c) => (
                    <TableCell key={c.field} align={c.align}>{c.render ? c.render(row) : (row[c.field] ?? '—')}</TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      {data && total > 0 && (
        <TablePagination component="div" count={total} page={page} rowsPerPage={table.size}
          rowsPerPageOptions={rowsPerPageOptions}
          onPageChange={(_, p) => table.setPage(p)}
          onRowsPerPageChange={(e) => table.setSize(Number(e.target.value))} />
      )}
    </Card>
  );
}
