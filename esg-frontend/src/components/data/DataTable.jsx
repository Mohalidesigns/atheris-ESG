import {
  Card, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, TableSortLabel,
} from '@mui/material';
import { EmptyState, ErrorState, LoadingState } from '../states/States';
import { normalizePage } from './useServerTable';

/**
 * Server-backed table (WP-16): the server pages, sorts and filters; this renders one page with loading,
 * empty and error states. `query` is an RTK Query result, `table` comes from useServerTable().
 *
 *   columns: [{ field, header, sortable?, align?, render?: (row) => node }]
 */
export default function DataTable({
  columns, query, table, ariaLabel, emptyTitle = 'No records yet', emptyMessage, getRowId = (r) => r.id,
  rowsPerPageOptions = [10, 20, 50],
}) {
  const { data, isLoading, isFetching, isError, error, refetch } = query;
  const { content, total, page } = normalizePage(data);

  return (
    <Card>
      {isError && <ErrorState error={error} onRetry={refetch} />}
      {isLoading && <LoadingState />}
      {!isLoading && !isError && content.length === 0 && <EmptyState title={emptyTitle}>{emptyMessage}</EmptyState>}
      {content.length > 0 && (
        <TableContainer sx={{ opacity: isFetching ? 0.6 : 1, transition: 'opacity .2s' }}>
          <Table size="small" aria-label={ariaLabel} aria-busy={isFetching}>
            <TableHead>
              <TableRow>
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
                <TableRow key={getRowId(row)} hover>
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
