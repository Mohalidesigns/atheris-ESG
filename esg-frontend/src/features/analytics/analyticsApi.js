import { apiSlice, unwrap } from '../../services/apiSlice';

// Anything that changes the figures refreshes the overview, the dashboard card and the gap analysis.
const OVERVIEW = ['AnalyticsOverview', 'Gap', 'Report'];

/** M9 Analytics (backend: AnalyticsController — WP-35, D34). Rules and statistics only. */
export const analyticsApi = apiSlice.injectEndpoints({
  endpoints: (build) => ({
    analyticsOverview: build.query({ query: (year) => ({ url: '/analytics/overview', params: year ? { year } : {} }), transformResponse: unwrap,
      providesTags: ['AnalyticsOverview'] }),
    analyticsSettings: build.query({ query: () => '/analytics/settings', transformResponse: unwrap, providesTags: ['AnalyticsSettings'] }),
    updateAnalyticsSettings: build.mutation({
      query: (body) => ({ url: '/analytics/settings', method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: ['AnalyticsSettings'],
    }),

    anomalies: build.query({ query: (params = {}) => ({ url: '/analytics/anomalies', params }), transformResponse: unwrap, providesTags: ['Anomaly'] }),
    anomaly: build.query({ query: (id) => `/analytics/anomalies/${id}`, transformResponse: unwrap, providesTags: ['Anomaly'] }),
    scanAnomalies: build.mutation({
      query: (year) => ({ url: '/analytics/anomalies/scan', method: 'POST', body: { year } }), transformResponse: unwrap,
      invalidatesTags: ['Anomaly', ...OVERVIEW],
    }),
    anomalyAction: build.mutation({
      query: ({ id, ...body }) => ({ url: `/analytics/anomalies/${id}/actions`, method: 'POST', body }), transformResponse: unwrap,
      invalidatesTags: ['Anomaly', ...OVERVIEW],
    }),

    kpis: build.query({ query: (params = {}) => ({ url: '/analytics/kpis', params }), transformResponse: unwrap, providesTags: ['Kpi'] }),
    kpi: build.query({ query: (id) => `/analytics/kpis/${id}`, transformResponse: unwrap, providesTags: ['Kpi'] }),
    createKpi: build.mutation({
      query: (body) => ({ url: '/analytics/kpis', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['Kpi', 'Maturity', 'SdgPriority', ...OVERVIEW],
    }),
    updateKpi: build.mutation({
      query: ({ id, ...body }) => ({ url: `/analytics/kpis/${id}`, method: 'PUT', body }), transformResponse: unwrap,
      invalidatesTags: ['Kpi', 'Maturity', 'SdgPriority', ...OVERVIEW],
    }),
    deleteKpi: build.mutation({
      query: (id) => ({ url: `/analytics/kpis/${id}`, method: 'DELETE' }), invalidatesTags: ['Kpi', 'Maturity', 'SdgPriority', ...OVERVIEW],
    }),

    benchmarks: build.query({ query: (params = {}) => ({ url: '/analytics/benchmarks', params }), transformResponse: unwrap, providesTags: ['BenchmarkDataset'] }),
    internalBenchmark: build.query({ query: (params) => ({ url: '/analytics/benchmarks/internal', params }), transformResponse: unwrap }),
    benchmarkDatasets: build.query({ query: () => '/analytics/benchmark-datasets', transformResponse: unwrap, providesTags: ['BenchmarkDataset'] }),
    createBenchmarkDataset: build.mutation({
      query: (body) => ({ url: '/analytics/benchmark-datasets', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['BenchmarkDataset'],
    }),
    updateBenchmarkDataset: build.mutation({
      query: ({ id, ...body }) => ({ url: `/analytics/benchmark-datasets/${id}`, method: 'PUT', body }), transformResponse: unwrap,
      invalidatesTags: ['BenchmarkDataset'],
    }),
    deleteBenchmarkDataset: build.mutation({
      query: (id) => ({ url: `/analytics/benchmark-datasets/${id}`, method: 'DELETE' }), invalidatesTags: ['BenchmarkDataset'],
    }),

    maturity: build.query({ query: (year) => ({ url: '/analytics/maturity', params: year ? { year } : {} }), transformResponse: unwrap, providesTags: ['Maturity'] }),
    maturityHistory: build.query({ query: () => '/analytics/maturity/assessments', transformResponse: unwrap, providesTags: ['Maturity'] }),
    assessMaturity: build.mutation({
      query: (body) => ({ url: '/analytics/maturity/assessments', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['Maturity'],
    }),

    sdgAlignment: build.query({ query: (year) => ({ url: '/analytics/sdg-alignment', params: year ? { year } : {} }), transformResponse: unwrap,
      providesTags: ['SdgPriority'] }),
    setSdgPriority: build.mutation({
      query: ({ year, ...body }) => ({ url: '/analytics/sdg-priorities', method: 'PUT', body, params: { year } }), transformResponse: unwrap,
      invalidatesTags: ['SdgPriority'],
    }),
    removeSdgPriority: build.mutation({
      query: ({ goal, year }) => ({ url: `/analytics/sdg-priorities/${goal}`, method: 'DELETE', params: { year } }), transformResponse: unwrap,
      invalidatesTags: ['SdgPriority'],
    }),
  }),
});

export const {
  useAnalyticsOverviewQuery, useAnalyticsSettingsQuery, useUpdateAnalyticsSettingsMutation,
  useAnomaliesQuery, useAnomalyQuery, useScanAnomaliesMutation, useAnomalyActionMutation,
  useKpisQuery, useKpiQuery, useCreateKpiMutation, useUpdateKpiMutation, useDeleteKpiMutation,
  useBenchmarksQuery, useInternalBenchmarkQuery, useBenchmarkDatasetsQuery, useCreateBenchmarkDatasetMutation, useUpdateBenchmarkDatasetMutation,
  useDeleteBenchmarkDatasetMutation,
  useMaturityQuery, useMaturityHistoryQuery, useAssessMaturityMutation,
  useSdgAlignmentQuery, useSetSdgPriorityMutation, useRemoveSdgPriorityMutation,
} = analyticsApi;
