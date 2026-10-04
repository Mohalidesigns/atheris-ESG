import { apiSlice, unwrap } from '../../services/apiSlice';

const RISK = ['RiskOverview', 'Gap'];

/** M5 Risk & Materiality (backend: RiskController — WP-32). */
export const riskApi = apiSlice.injectEndpoints({
  endpoints: (build) => ({
    riskOverview: build.query({ query: () => '/risk/overview', transformResponse: unwrap, providesTags: ['RiskOverview'] }),
    riskPeople: build.query({ query: () => '/risk/people', transformResponse: unwrap }),

    assessments: build.query({ query: () => '/risk/assessments', transformResponse: unwrap, providesTags: ['Assessment'] }),
    assessment: build.query({ query: (id) => `/risk/assessments/${id}`, transformResponse: unwrap, providesTags: ['Assessment'] }),
    createAssessment: build.mutation({
      query: (body) => ({ url: '/risk/assessments', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['Assessment', ...RISK],
    }),
    updateAssessment: build.mutation({
      query: ({ id, ...body }) => ({ url: `/risk/assessments/${id}`, method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: ['Assessment', ...RISK],
    }),
    deleteAssessment: build.mutation({
      query: ({ id, version }) => ({ url: `/risk/assessments/${id}`, method: 'DELETE', params: { version } }), invalidatesTags: ['Assessment', ...RISK],
    }),
    assessmentAction: build.mutation({
      query: ({ id, ...body }) => ({ url: `/risk/assessments/${id}/actions`, method: 'POST', body }), transformResponse: unwrap,
      invalidatesTags: ['Assessment', ...RISK],
    }),
    addTopic: build.mutation({
      query: ({ assessmentId, ...body }) => ({ url: `/risk/assessments/${assessmentId}/topics`, method: 'POST', body }), transformResponse: unwrap,
      invalidatesTags: ['Assessment', ...RISK],
    }),
    updateTopic: build.mutation({
      query: ({ assessmentId, id, ...body }) => ({ url: `/risk/assessments/${assessmentId}/topics/${id}`, method: 'PUT', body }), transformResponse: unwrap,
      invalidatesTags: ['Assessment', ...RISK],
    }),
    deleteTopic: build.mutation({
      query: ({ assessmentId, id, version }) => ({ url: `/risk/assessments/${assessmentId}/topics/${id}`, method: 'DELETE', params: { version } }),
      transformResponse: unwrap, invalidatesTags: ['Assessment', ...RISK],
    }),
    riskTopics: build.query({ query: () => '/risk/topics', transformResponse: unwrap, providesTags: ['Assessment'] }),

    risks: build.query({ query: (params = {}) => ({ url: '/risk/risks', params }), transformResponse: unwrap, providesTags: ['RiskItem'] }),
    createRisk: build.mutation({
      query: (body) => ({ url: '/risk/risks', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['RiskItem', ...RISK],
    }),
    updateRisk: build.mutation({
      query: ({ id, ...body }) => ({ url: `/risk/risks/${id}`, method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: ['RiskItem', ...RISK],
    }),
    deleteRisk: build.mutation({
      query: ({ id, version }) => ({ url: `/risk/risks/${id}`, method: 'DELETE', params: { version } }), invalidatesTags: ['RiskItem', ...RISK],
    }),
    heatmap: build.query({ query: (params) => ({ url: '/risk/heatmap', params }), transformResponse: unwrap, providesTags: ['RiskItem'] }),

    scenarios: build.query({ query: () => '/risk/scenarios', transformResponse: unwrap, providesTags: ['Scenario'] }),
    setCarbonPrice: build.mutation({
      query: (body) => ({ url: '/risk/scenarios/prices', method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: ['Scenario', ...RISK],
    }),
    deleteCarbonPrice: build.mutation({
      query: ({ code, year }) => ({ url: `/risk/scenarios/${code}/prices/${year}`, method: 'DELETE' }), transformResponse: unwrap,
      invalidatesTags: ['Scenario', ...RISK],
    }),
    transition: build.query({
      query: (baselineYear) => ({ url: '/risk/scenarios/transition', params: baselineYear ? { baselineYear } : {} }), transformResponse: unwrap,
      providesTags: ['Scenario', 'Emission'],
    }),
    physicalRisks: build.query({ query: () => '/risk/physical', transformResponse: unwrap, providesTags: ['Physical'] }),
    createPhysical: build.mutation({
      query: (body) => ({ url: '/risk/physical', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['Physical', ...RISK],
    }),
    updatePhysical: build.mutation({
      query: ({ id, ...body }) => ({ url: `/risk/physical/${id}`, method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: ['Physical', ...RISK],
    }),
    deletePhysical: build.mutation({
      query: ({ id, version }) => ({ url: `/risk/physical/${id}`, method: 'DELETE', params: { version } }), invalidatesTags: ['Physical', ...RISK],
    }),
    climateDataSource: build.query({ query: () => '/risk/physical/data-source', transformResponse: unwrap }),
    suggestHazard: build.mutation({ query: (params) => ({ url: '/risk/physical/suggest', params }), transformResponse: unwrap }),
  }),
});

export const {
  useRiskOverviewQuery, useRiskPeopleQuery,
  useAssessmentsQuery, useAssessmentQuery, useCreateAssessmentMutation, useUpdateAssessmentMutation, useDeleteAssessmentMutation,
  useAssessmentActionMutation, useAddTopicMutation, useUpdateTopicMutation, useDeleteTopicMutation, useRiskTopicsQuery,
  useRisksQuery, useCreateRiskMutation, useUpdateRiskMutation, useDeleteRiskMutation, useHeatmapQuery,
  useScenariosQuery, useSetCarbonPriceMutation, useDeleteCarbonPriceMutation, useTransitionQuery, usePhysicalRisksQuery,
  useCreatePhysicalMutation, useUpdatePhysicalMutation, useDeletePhysicalMutation, useClimateDataSourceQuery, useSuggestHazardMutation,
} = riskApi;
