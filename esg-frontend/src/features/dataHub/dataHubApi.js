import { apiSlice, unwrap } from '../../services/apiSlice';

/** ESG Data Hub (backend: DataPointController, DataHubSetupController, ImportController — WP-20). */
export const dataHubApi = apiSlice.injectEndpoints({
  endpoints: (build) => ({
    dataPoints: build.query({
      query: (params) => ({ url: '/data/points', params }),
      transformResponse: unwrap,
      providesTags: ['DataPoint'],
    }),
    dataPoint: build.query({
      query: (id) => `/data/points/${id}`,
      transformResponse: unwrap,
      providesTags: (_r, _e, id) => [{ type: 'DataPoint', id }],
    }),
    dataPointHistory: build.query({
      query: (id) => `/data/points/${id}/history`,
      transformResponse: unwrap,
      providesTags: (_r, _e, id) => [{ type: 'DataPointHistory', id }],
    }),
    createDataPoint: build.mutation({
      query: (body) => ({ url: '/data/points', method: 'POST', body }),
      transformResponse: unwrap,
      invalidatesTags: ['DataPoint', 'File'],
    }),
    updateDataPoint: build.mutation({
      query: ({ id, ...body }) => ({ url: `/data/points/${id}`, method: 'PUT', body }),
      transformResponse: unwrap,
      invalidatesTags: (_r, _e, { id }) => ['DataPoint', { type: 'DataPointHistory', id }, 'File'],
    }),
    deleteDataPoint: build.mutation({
      query: ({ id, version }) => ({ url: `/data/points/${id}`, method: 'DELETE', params: { version } }),
      invalidatesTags: ['DataPoint'],
    }),
    /** action: submit | validate | approve | reject | reopen. */
    transitionDataPoint: build.mutation({
      query: ({ id, action, version, reason }) => ({
        url: `/data/points/${id}/${action}`, method: 'PUT', body: { version, ...(reason ? { reason } : {}) },
      }),
      transformResponse: unwrap,
      invalidatesTags: (_r, _e, { id }) => ['DataPoint', { type: 'DataPointHistory', id }],
    }),
    dataRules: build.query({ query: () => '/data/rules', transformResponse: unwrap }),

    dataSources: build.query({
      query: (params = {}) => ({ url: '/data/sources', params }),
      transformResponse: unwrap,
      providesTags: ['DataSource'],
    }),
    createDataSource: build.mutation({
      query: (body) => ({ url: '/data/sources', method: 'POST', body }),
      transformResponse: unwrap,
      invalidatesTags: ['DataSource'],
    }),
    updateDataSource: build.mutation({
      query: ({ id, ...body }) => ({ url: `/data/sources/${id}`, method: 'PUT', body }),
      transformResponse: unwrap,
      invalidatesTags: ['DataSource'],
    }),

    fxRates: build.query({ query: () => '/data/fx-rates', transformResponse: unwrap, providesTags: ['FxRate'] }),
    createFxRate: build.mutation({
      query: (body) => ({ url: '/data/fx-rates', method: 'POST', body }),
      transformResponse: unwrap,
      invalidatesTags: ['FxRate'],
    }),
    updateFxRate: build.mutation({
      query: ({ id, ...body }) => ({ url: `/data/fx-rates/${id}`, method: 'PUT', body }),
      transformResponse: unwrap,
      invalidatesTags: ['FxRate'],
    }),
    deleteFxRate: build.mutation({
      query: (id) => ({ url: `/data/fx-rates/${id}`, method: 'DELETE' }),
      invalidatesTags: ['FxRate'],
    }),

    imports: build.query({ query: () => '/data/import', transformResponse: unwrap, providesTags: ['Import'] }),
    importJob: build.query({
      query: (id) => `/data/import/${id}`,
      transformResponse: unwrap,
      providesTags: (_r, _e, id) => [{ type: 'Import', id }],
    }),
    uploadImport: build.mutation({
      query: (file) => {
        const form = new FormData();
        form.append('file', file);
        return { url: '/data/import', method: 'POST', body: form };
      },
      transformResponse: unwrap,
      invalidatesTags: ['Import'],
    }),
    // Authenticated download: fetched as text, saved by the browser (no bearer token in a plain link).
    importTemplate: build.mutation({
      query: () => ({ url: '/data/import/template', responseHandler: (r) => r.text(), cache: 'no-store' }),
    }),
    startImport: build.mutation({
      query: ({ id, ...body }) => ({ url: `/data/import/${id}/start`, method: 'POST', body }),
      transformResponse: unwrap,
      invalidatesTags: (_r, _e, { id }) => ['Import', { type: 'Import', id }, 'DataPoint'],
    }),
  }),
});

export const {
  useDataPointsQuery, useDataPointQuery, useDataPointHistoryQuery, useCreateDataPointMutation, useUpdateDataPointMutation,
  useDeleteDataPointMutation, useTransitionDataPointMutation, useDataRulesQuery,
  useDataSourcesQuery, useCreateDataSourceMutation, useUpdateDataSourceMutation,
  useFxRatesQuery, useCreateFxRateMutation, useUpdateFxRateMutation, useDeleteFxRateMutation,
  useImportsQuery, useImportJobQuery, useUploadImportMutation, useStartImportMutation, useImportTemplateMutation,
} = dataHubApi;
