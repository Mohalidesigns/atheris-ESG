import { apiSlice, unwrap } from '../../services/apiSlice';

const blob = (url) => ({ url, responseHandler: (r) => r.blob(), cache: 'no-store' });
const ALL = ['InvestorScorecard', 'SharedReport', 'DataRoom', 'Bond', 'ImpactMetric'];

/** M10 Investor Portal (backend: InvestorController — WP-40, D35). */
export const investorApi = apiSlice.injectEndpoints({
  endpoints: (build) => ({
    investorDashboard: build.query({ query: () => '/investor/dashboards', transformResponse: unwrap, providesTags: ALL }),

    investorSettings: build.query({ query: () => '/investor/settings', transformResponse: unwrap, providesTags: ['InvestorSettings'] }),
    updateInvestorSettings: build.mutation({
      query: (body) => ({ url: '/investor/settings', method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: ['InvestorSettings'],
    }),
    scorecards: build.query({ query: () => '/investor/scorecards', transformResponse: unwrap, providesTags: ['InvestorScorecard'] }),
    scorecardPreview: build.query({ query: (year) => ({ url: '/investor/scorecards/preview', params: { year } }), transformResponse: unwrap,
      providesTags: ['InvestorScorecard', 'InvestorSettings'] }),
    generateScorecard: build.mutation({
      query: (body) => ({ url: '/investor/scorecards', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['InvestorScorecard'],
    }),
    scorecardAction: build.mutation({
      query: ({ id, ...body }) => ({ url: `/investor/scorecards/${id}/actions`, method: 'POST', body }), transformResponse: unwrap,
      invalidatesTags: ['InvestorScorecard', 'Report'],
    }),

    sharedReports: build.query({ query: () => '/investor/shared-reports', transformResponse: unwrap, providesTags: ['SharedReport'] }),
    shareReport: build.mutation({
      query: (body) => ({ url: '/investor/shared-reports', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['SharedReport', 'Report'],
    }),
    revokeSharedReport: build.mutation({ query: (id) => ({ url: `/investor/shared-reports/${id}`, method: 'DELETE' }), invalidatesTags: ['SharedReport'] }),
    downloadSharedReport: build.mutation({ query: (id) => blob(`/investor/shared-reports/${id}/download`) }),
    dfiTemplates: build.query({ query: () => '/investor/dfi-templates', transformResponse: unwrap }),
    createDfiReport: build.mutation({
      query: (body) => ({ url: '/investor/dfi-reports', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['Report'],
    }),

    investorPeople: build.query({ query: () => '/investor/people', transformResponse: unwrap }),
    dataRooms: build.query({ query: () => '/investor/data-rooms', transformResponse: unwrap, providesTags: ['DataRoom'] }),
    dataRoom: build.query({ query: (id) => `/investor/data-rooms/${id}`, transformResponse: unwrap, providesTags: ['DataRoom'] }),
    createDataRoom: build.mutation({
      query: (body) => ({ url: '/investor/data-rooms', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['DataRoom'],
    }),
    updateDataRoom: build.mutation({
      query: ({ id, ...body }) => ({ url: `/investor/data-rooms/${id}`, method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: ['DataRoom'],
    }),
    deleteDataRoom: build.mutation({ query: (id) => ({ url: `/investor/data-rooms/${id}`, method: 'DELETE' }), invalidatesTags: ['DataRoom'] }),
    addRoomDocument: build.mutation({
      query: ({ id, ...body }) => ({ url: `/investor/data-rooms/${id}/documents`, method: 'POST', body }), transformResponse: unwrap,
      invalidatesTags: ['DataRoom'],
    }),
    removeRoomDocument: build.mutation({
      query: ({ id, documentId }) => ({ url: `/investor/data-rooms/${id}/documents/${documentId}`, method: 'DELETE' }), transformResponse: unwrap,
      invalidatesTags: ['DataRoom'],
    }),
    downloadRoomDocument: build.mutation({ query: ({ id, documentId }) => blob(`/investor/data-rooms/${id}/documents/${documentId}/download`) }),
    roomAccessLog: build.query({ query: (id) => `/investor/data-rooms/${id}/access-log`, transformResponse: unwrap, providesTags: ['DataRoom'] }),

    bonds: build.query({ query: () => '/investor/bonds', transformResponse: unwrap, providesTags: ['Bond'] }),
    createBond: build.mutation({ query: (body) => ({ url: '/investor/bonds', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['Bond'] }),
    updateBond: build.mutation({
      query: ({ id, ...body }) => ({ url: `/investor/bonds/${id}`, method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: ['Bond'],
    }),
    deleteBond: build.mutation({ query: (id) => ({ url: `/investor/bonds/${id}`, method: 'DELETE' }), invalidatesTags: ['Bond'] }),
    saveAllocation: build.mutation({
      query: ({ bondId, id, ...body }) => ({ url: `/investor/bonds/${bondId}/allocations${id ? `/${id}` : ''}`, method: id ? 'PUT' : 'POST', body }),
      transformResponse: unwrap, invalidatesTags: ['Bond'],
    }),
    deleteAllocation: build.mutation({
      query: ({ bondId, id }) => ({ url: `/investor/bonds/${bondId}/allocations/${id}`, method: 'DELETE' }), transformResponse: unwrap, invalidatesTags: ['Bond'],
    }),

    impactMetrics: build.query({ query: () => '/investor/impact-metrics', transformResponse: unwrap, providesTags: ['ImpactMetric'] }),
    createImpactMetric: build.mutation({
      query: (body) => ({ url: '/investor/impact-metrics', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['ImpactMetric'],
    }),
    updateImpactMetric: build.mutation({
      query: ({ id, ...body }) => ({ url: `/investor/impact-metrics/${id}`, method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: ['ImpactMetric'],
    }),
    deleteImpactMetric: build.mutation({ query: (id) => ({ url: `/investor/impact-metrics/${id}`, method: 'DELETE' }), invalidatesTags: ['ImpactMetric'] }),
  }),
});

export const {
  useInvestorDashboardQuery, useInvestorSettingsQuery, useUpdateInvestorSettingsMutation, useScorecardsQuery, useScorecardPreviewQuery,
  useGenerateScorecardMutation, useScorecardActionMutation, useSharedReportsQuery, useShareReportMutation, useRevokeSharedReportMutation,
  useDownloadSharedReportMutation, useDfiTemplatesQuery, useCreateDfiReportMutation, useInvestorPeopleQuery, useDataRoomsQuery, useDataRoomQuery,
  useCreateDataRoomMutation, useUpdateDataRoomMutation, useDeleteDataRoomMutation, useAddRoomDocumentMutation, useRemoveRoomDocumentMutation,
  useDownloadRoomDocumentMutation, useRoomAccessLogQuery, useBondsQuery, useCreateBondMutation, useUpdateBondMutation, useDeleteBondMutation,
  useSaveAllocationMutation, useDeleteAllocationMutation, useImpactMetricsQuery, useCreateImpactMetricMutation, useUpdateImpactMetricMutation,
  useDeleteImpactMetricMutation,
} = investorApi;
