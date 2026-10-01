import { apiSlice, unwrap } from '../../services/apiSlice';

const reportTags = (r) => (r ? ['Report', { type: 'Report', id: r.id }] : ['Report']);

/** Reporting & Disclosure Studio (backend: com.esg.reporting.ReportController — WP-23; TRD §6.4.2). */
export const reportingApi = apiSlice.injectEndpoints({
  endpoints: (build) => ({
    reportTemplates: build.query({ query: () => '/reports/templates', transformResponse: unwrap, providesTags: ['ReportTemplate'] }),
    reports: build.query({ query: (status) => ({ url: '/reports', params: { status } }), transformResponse: unwrap, providesTags: ['Report'] }),
    report: build.query({ query: (id) => `/reports/${id}`, transformResponse: unwrap, providesTags: (r) => reportTags(r) }),
    createReport: build.mutation({
      query: (body) => ({ url: '/reports', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['Report'],
    }),
    updateReport: build.mutation({
      query: ({ id, ...body }) => ({ url: `/reports/${id}`, method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: ['Report'],
    }),
    saveSections: build.mutation({
      query: ({ id, ...body }) => ({ url: `/reports/${id}/sections`, method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: ['Report'],
    }),
    refreshReport: build.mutation({
      query: ({ id, version }) => ({ url: `/reports/${id}/refresh`, method: 'POST', body: { version } }), transformResponse: unwrap,
      invalidatesTags: ['Report'],
    }),
    submitReport: build.mutation({
      query: ({ id, ...body }) => ({ url: `/reports/${id}/approve`, method: 'POST', body }), transformResponse: unwrap,
      invalidatesTags: ['Report', 'Notification'],
    }),
    decideStep: build.mutation({
      query: ({ id, stepId, ...body }) => ({ url: `/reports/${id}/approval-steps/${stepId}`, method: 'PUT', body }), transformResponse: unwrap,
      invalidatesTags: ['Report', 'Notification'],
    }),
    reopenReport: build.mutation({
      query: ({ id, version }) => ({ url: `/reports/${id}/reopen`, method: 'POST', body: { version } }), transformResponse: unwrap,
      invalidatesTags: ['Report'],
    }),
    deleteReport: build.mutation({
      query: ({ id, version }) => ({ url: `/reports/${id}`, method: 'DELETE', params: { version } }), invalidatesTags: ['Report'],
    }),
    reportVersion: build.query({
      query: ({ id, versionId }) => `/reports/${id}/versions/${versionId}`, transformResponse: unwrap,
    }),
    generateReport: build.mutation({
      query: ({ id, format }) => ({ url: `/reports/${id}/generate`, method: 'POST', body: { format } }), transformResponse: unwrap,
      invalidatesTags: ['Report'],
    }),
  }),
});

export const {
  useReportTemplatesQuery, useReportsQuery, useReportQuery, useCreateReportMutation, useUpdateReportMutation, useSaveSectionsMutation,
  useRefreshReportMutation, useSubmitReportMutation, useDecideStepMutation, useReopenReportMutation, useDeleteReportMutation,
  useReportVersionQuery, useLazyReportVersionQuery, useGenerateReportMutation,
} = reportingApi;
