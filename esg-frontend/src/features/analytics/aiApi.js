import { apiSlice, unwrap } from '../../services/apiSlice';

/** WP-50 Grounded AI (backend: AiController — D38). Every result is an AI run, labelled and reviewed by a person. */
export const aiApi = apiSlice.injectEndpoints({
  endpoints: (build) => ({
    aiStatus: build.query({ query: () => '/analytics/ai/status', transformResponse: unwrap, providesTags: ['AiStatus'] }),

    predictions: build.query({ query: () => '/analytics/predictions', transformResponse: unwrap, providesTags: ['AiRun'] }),
    createPrediction: build.mutation({ query: (body) => ({ url: '/analytics/predictions', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['AiRun'] }),
    dismissPrediction: build.mutation({
      query: ({ id, ...body }) => ({ url: `/analytics/predictions/${id}/dismiss`, method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['AiRun'],
    }),

    mlScan: build.mutation({
      query: (body) => ({ url: '/analytics/ml-anomalies/scan', method: 'POST', body }), transformResponse: unwrap,
      invalidatesTags: ['Anomaly', 'AiRun', 'AnalyticsOverview'],
    }),

    extractions: build.query({ query: () => '/analytics/extractions', transformResponse: unwrap, providesTags: ['AiRun'] }),
    extraction: build.query({ query: (id) => `/analytics/extractions/${id}`, transformResponse: unwrap, providesTags: ['AiRun'] }),
    startExtraction: build.mutation({ query: (fileId) => ({ url: '/analytics/extractions', method: 'POST', body: { fileId } }), transformResponse: unwrap, invalidatesTags: ['AiRun'] }),
    acceptExtracted: build.mutation({
      query: ({ runId, id, ...body }) => ({ url: `/analytics/extractions/${runId}/values/${id}/accept`, method: 'POST', body }), transformResponse: unwrap,
      invalidatesTags: ['AiRun', 'DataPoint'],
    }),
    rejectExtracted: build.mutation({
      query: ({ runId, id, ...body }) => ({ url: `/analytics/extractions/${runId}/values/${id}/reject`, method: 'POST', body }), transformResponse: unwrap,
      invalidatesTags: ['AiRun'],
    }),
  }),
});

export const {
  useAiStatusQuery, usePredictionsQuery, useCreatePredictionMutation, useDismissPredictionMutation, useMlScanMutation, useExtractionsQuery, useExtractionQuery,
  useStartExtractionMutation, useAcceptExtractedMutation, useRejectExtractedMutation,
} = aiApi;
