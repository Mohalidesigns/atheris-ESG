import { apiSlice, unwrap } from '../../services/apiSlice';

const transition = (build, path, tag, extra = []) => build.mutation({
  query: ({ id, ...body }) => ({ url: `${path}/${id}`, method: 'PUT', body }),
  transformResponse: unwrap,
  invalidatesTags: [tag, ...extra],
});

/** Regulatory Compliance Manager (backend: com.esg.compliance.ComplianceController — WP-22). */
export const complianceApi = apiSlice.injectEndpoints({
  endpoints: (build) => ({
    complianceStatus: build.query({
      query: (year) => ({ url: '/compliance/status', params: { year } }), transformResponse: unwrap, providesTags: ['ComplianceStatus'],
    }),
    gapAnalysis: build.query({
      query: ({ framework, year }) => ({ url: '/compliance/gaps', params: { framework, year } }), transformResponse: unwrap, providesTags: ['Gap'],
    }),
    attest: build.mutation({
      query: (body) => ({ url: '/compliance/attestations', method: 'PUT', body }), transformResponse: unwrap,
      invalidatesTags: ['Gap', 'ComplianceStatus'],
    }),
    mapFrameworks: build.mutation({ query: (body) => ({ url: '/compliance/map', method: 'POST', body }), transformResponse: unwrap }),
    setActiveFrameworks: build.mutation({
      query: (codes) => ({ url: '/compliance/frameworks/active', method: 'PUT', body: { codes } }), transformResponse: unwrap,
      invalidatesTags: ['ComplianceStatus', 'Settings'],
    }),
    owners: build.query({ query: () => '/compliance/owners', transformResponse: unwrap, providesTags: ['Owner'] }),

    deadlines: build.query({
      query: (params) => ({ url: '/compliance/deadlines', params }), transformResponse: unwrap, providesTags: ['Deadline'],
    }),
    createDeadline: build.mutation({
      query: (body) => ({ url: '/compliance/deadlines', method: 'POST', body }), transformResponse: unwrap,
      invalidatesTags: ['Deadline', 'ComplianceStatus', 'Gap'],
    }),
    updateDeadline: transition(build, '/compliance/deadlines', 'Deadline', ['ComplianceStatus', 'Gap']),
    submitDeadline: build.mutation({
      query: ({ id, ...body }) => ({ url: `/compliance/deadlines/${id}/submit`, method: 'PUT', body }), transformResponse: unwrap,
      invalidatesTags: ['Deadline', 'ComplianceStatus', 'Gap'],
    }),
    waiveDeadline: build.mutation({
      query: ({ id, ...body }) => ({ url: `/compliance/deadlines/${id}/waive`, method: 'PUT', body }), transformResponse: unwrap,
      invalidatesTags: ['Deadline', 'ComplianceStatus', 'Gap'],
    }),
    reopenDeadline: build.mutation({
      query: ({ id, version }) => ({ url: `/compliance/deadlines/${id}/reopen`, method: 'PUT', body: { version } }), transformResponse: unwrap,
      invalidatesTags: ['Deadline', 'ComplianceStatus', 'Gap'],
    }),
    deleteDeadline: build.mutation({
      query: ({ id, version }) => ({ url: `/compliance/deadlines/${id}`, method: 'DELETE', params: { version } }),
      invalidatesTags: ['Deadline', 'ComplianceStatus', 'Gap'],
    }),
    runReminders: build.mutation({
      query: () => ({ url: '/compliance/deadlines/reminders/run', method: 'POST' }), transformResponse: unwrap,
      invalidatesTags: ['Deadline', 'Notification'],
    }),

    changes: build.query({ query: (status) => ({ url: '/compliance/changes', params: { status } }), transformResponse: unwrap, providesTags: ['RegChange'] }),
    createChange: build.mutation({
      query: (body) => ({ url: '/compliance/changes', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['RegChange'],
    }),
    updateChange: transition(build, '/compliance/changes', 'RegChange'),
    startChange: build.mutation({
      query: ({ id, version }) => ({ url: `/compliance/changes/${id}/start`, method: 'PUT', body: { version } }), transformResponse: unwrap,
      invalidatesTags: ['RegChange'],
    }),
    assessChange: build.mutation({
      query: ({ id, ...body }) => ({ url: `/compliance/changes/${id}/assess`, method: 'PUT', body }), transformResponse: unwrap,
      invalidatesTags: ['RegChange'],
    }),
    closeChange: build.mutation({
      query: ({ id, version }) => ({ url: `/compliance/changes/${id}/close`, method: 'PUT', body: { version } }), transformResponse: unwrap,
      invalidatesTags: ['RegChange'],
    }),
    reopenChange: build.mutation({
      query: ({ id, version }) => ({ url: `/compliance/changes/${id}/reopen`, method: 'PUT', body: { version } }), transformResponse: unwrap,
      invalidatesTags: ['RegChange'],
    }),
    deleteChange: build.mutation({
      query: ({ id, version }) => ({ url: `/compliance/changes/${id}`, method: 'DELETE', params: { version } }), invalidatesTags: ['RegChange'],
    }),
  }),
});

export const {
  useComplianceStatusQuery, useGapAnalysisQuery, useAttestMutation, useMapFrameworksMutation, useSetActiveFrameworksMutation, useOwnersQuery,
  useDeadlinesQuery, useCreateDeadlineMutation, useUpdateDeadlineMutation, useSubmitDeadlineMutation, useWaiveDeadlineMutation,
  useReopenDeadlineMutation, useDeleteDeadlineMutation, useRunRemindersMutation,
  useChangesQuery, useCreateChangeMutation, useUpdateChangeMutation, useStartChangeMutation, useAssessChangeMutation,
  useCloseChangeMutation, useReopenChangeMutation, useDeleteChangeMutation,
} = complianceApi;
