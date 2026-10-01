import { apiSlice, unwrap } from '../../services/apiSlice';

/** Audit log viewer and data lineage (backend: AuditController, WP-15). Audit entries are read-only by design. */
export const auditApi = apiSlice.injectEndpoints({
  endpoints: (build) => ({
    listAuditLogs: build.query({
      query: (params) => ({
        url: '/audit/logs',
        params: Object.fromEntries(Object.entries(params).filter(([, v]) => v !== '' && v != null)),
      }),
      transformResponse: unwrap,
      providesTags: ['Audit'],
    }),
    lineage: build.query({
      query: ({ type, id }) => ({ url: '/audit/lineage', params: { type, id } }),
      transformResponse: unwrap,
    }),
  }),
});

export const { useListAuditLogsQuery, useLineageQuery } = auditApi;
