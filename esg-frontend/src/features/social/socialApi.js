import { apiSlice, unwrap } from '../../services/apiSlice';

const SOCIAL = ['SocialOverview', 'Gap'];

/** M7 Social Impact (backend: SocialController — WP-33). */
export const socialApi = apiSlice.injectEndpoints({
  endpoints: (build) => ({
    socialOverview: build.query({ query: (year) => ({ url: '/social/overview', params: year ? { year } : {} }), transformResponse: unwrap, providesTags: ['SocialOverview'] }),
    socialPeople: build.query({ query: () => '/social/people', transformResponse: unwrap }),

    workforceSnapshots: build.query({
      query: (params = {}) => ({ url: '/social/workforce', params }), transformResponse: unwrap, providesTags: ['Workforce'],
    }),
    workforceSnapshot: build.query({ query: (id) => `/social/workforce/${id}`, transformResponse: unwrap, providesTags: ['Workforce'] }),
    createWorkforceSnapshot: build.mutation({
      query: (body) => ({ url: '/social/workforce', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['Workforce', ...SOCIAL],
    }),
    updateWorkforceSnapshot: build.mutation({
      query: ({ id, ...body }) => ({ url: `/social/workforce/${id}`, method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: ['Workforce', ...SOCIAL],
    }),
    deleteWorkforceSnapshot: build.mutation({
      query: ({ id, version }) => ({ url: `/social/workforce/${id}`, method: 'DELETE', params: { version } }), invalidatesTags: ['Workforce', ...SOCIAL],
    }),
    workforceAction: build.mutation({
      query: ({ id, ...body }) => ({ url: `/social/workforce/${id}/actions`, method: 'POST', body }), transformResponse: unwrap,
      invalidatesTags: ['Workforce', 'Incident', ...SOCIAL],
    }),

    incidents: build.query({ query: (params = {}) => ({ url: '/social/incidents', params }), transformResponse: unwrap, providesTags: ['Incident'] }),
    incident: build.query({ query: (id) => `/social/incidents/${id}`, transformResponse: unwrap, providesTags: ['Incident'] }),
    safetyStatistics: build.query({
      query: (params = {}) => ({ url: '/social/safety/statistics', params }), transformResponse: unwrap, providesTags: ['Incident', 'Workforce'],
    }),
    createIncident: build.mutation({
      query: (body) => ({ url: '/social/incidents', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['Incident', ...SOCIAL],
    }),
    updateIncident: build.mutation({
      query: ({ id, ...body }) => ({ url: `/social/incidents/${id}`, method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: ['Incident', ...SOCIAL],
    }),
    deleteIncident: build.mutation({
      query: ({ id, version }) => ({ url: `/social/incidents/${id}`, method: 'DELETE', params: { version } }), invalidatesTags: ['Incident', ...SOCIAL],
    }),
    incidentAction: build.mutation({
      query: ({ id, ...body }) => ({ url: `/social/incidents/${id}/actions`, method: 'POST', body }), transformResponse: unwrap,
      invalidatesTags: ['Incident', ...SOCIAL],
    }),

    projects: build.query({ query: (status) => ({ url: '/social/projects', params: status ? { status } : {} }), transformResponse: unwrap, providesTags: ['Project'] }),
    project: build.query({ query: (id) => `/social/projects/${id}`, transformResponse: unwrap, providesTags: ['Project'] }),
    createProject: build.mutation({
      query: (body) => ({ url: '/social/projects', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['Project', ...SOCIAL],
    }),
    updateProject: build.mutation({
      query: ({ id, ...body }) => ({ url: `/social/projects/${id}`, method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: ['Project', ...SOCIAL],
    }),
    deleteProject: build.mutation({
      query: ({ id, version }) => ({ url: `/social/projects/${id}`, method: 'DELETE', params: { version } }), invalidatesTags: ['Project', ...SOCIAL],
    }),
    addExpenditure: build.mutation({
      query: ({ id, ...body }) => ({ url: `/social/projects/${id}/expenditures`, method: 'POST', body }), transformResponse: unwrap,
      invalidatesTags: ['Project', ...SOCIAL],
    }),
    deleteExpenditure: build.mutation({
      query: ({ id, expenditureId, version }) => ({ url: `/social/projects/${id}/expenditures/${expenditureId}`, method: 'DELETE', params: { version } }),
      transformResponse: unwrap, invalidatesTags: ['Project', ...SOCIAL],
    }),
    addEvidence: build.mutation({
      query: ({ id, ...body }) => ({ url: `/social/projects/${id}/evidence`, method: 'POST', body }), transformResponse: unwrap,
      invalidatesTags: ['Project', 'File'],
    }),
    deleteEvidence: build.mutation({
      query: ({ id, evidenceId }) => ({ url: `/social/projects/${id}/evidence/${evidenceId}`, method: 'DELETE' }), transformResponse: unwrap,
      invalidatesTags: ['Project'],
    }),

    grievances: build.query({ query: (params = {}) => ({ url: '/social/grievances', params }), transformResponse: unwrap, providesTags: ['Grievance'] }),
    grievance: build.query({ query: (id) => `/social/grievances/${id}`, transformResponse: unwrap, providesTags: ['Grievance'] }),
    createGrievance: build.mutation({
      query: (body) => ({ url: '/social/grievances', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['Grievance', 'Project', ...SOCIAL],
    }),
    updateGrievance: build.mutation({
      query: ({ id, ...body }) => ({ url: `/social/grievances/${id}`, method: 'PUT', body }), transformResponse: unwrap,
      invalidatesTags: ['Grievance', 'Project', ...SOCIAL],
    }),
    escalateOverdue: build.mutation({
      query: () => ({ url: '/social/grievances/escalate-overdue', method: 'POST' }), transformResponse: unwrap, invalidatesTags: ['Grievance', ...SOCIAL],
    }),
    grievanceAction: build.mutation({
      query: ({ id, ...body }) => ({ url: `/social/grievances/${id}/actions`, method: 'POST', body }), transformResponse: unwrap,
      invalidatesTags: ['Grievance', 'Project', ...SOCIAL],
    }),
  }),
});

export const {
  useSocialOverviewQuery, useSocialPeopleQuery,
  useWorkforceSnapshotsQuery, useWorkforceSnapshotQuery, useCreateWorkforceSnapshotMutation, useUpdateWorkforceSnapshotMutation,
  useDeleteWorkforceSnapshotMutation, useWorkforceActionMutation,
  useIncidentsQuery, useIncidentQuery, useSafetyStatisticsQuery, useCreateIncidentMutation, useUpdateIncidentMutation, useDeleteIncidentMutation,
  useIncidentActionMutation,
  useProjectsQuery, useProjectQuery, useCreateProjectMutation, useUpdateProjectMutation, useDeleteProjectMutation, useAddExpenditureMutation,
  useDeleteExpenditureMutation, useAddEvidenceMutation, useDeleteEvidenceMutation,
  useGrievancesQuery, useGrievanceQuery, useCreateGrievanceMutation, useUpdateGrievanceMutation, useGrievanceActionMutation, useEscalateOverdueMutation,
} = socialApi;
