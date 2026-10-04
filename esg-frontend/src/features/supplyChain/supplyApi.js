import { apiSlice, unwrap } from '../../services/apiSlice';

const OVERVIEW = ['SupplyOverview', 'Gap'];
const form = (file) => {
  const f = new FormData();
  f.append('file', file);
  return f;
};

/** M6 Supply Chain ESG (backend: SupplyChainController, PublicSupplierController — WP-31). */
export const supplyApi = apiSlice.injectEndpoints({
  endpoints: (build) => ({
    supplyOverview: build.query({ query: (year) => ({ url: '/supply-chain/overview', params: year ? { year } : {} }), transformResponse: unwrap, providesTags: ['SupplyOverview'] }),
    supplyPeople: build.query({ query: () => '/supply-chain/people', transformResponse: unwrap }),

    suppliers: build.query({ query: (params = {}) => ({ url: '/supply-chain/suppliers', params }), transformResponse: unwrap, providesTags: ['Supplier'] }),
    supplier: build.query({ query: (id) => `/supply-chain/suppliers/${id}`, transformResponse: unwrap, providesTags: ['Supplier'] }),
    createSupplier: build.mutation({
      query: (body) => ({ url: '/supply-chain/suppliers', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['Supplier', ...OVERVIEW],
    }),
    updateSupplier: build.mutation({
      query: ({ id, ...body }) => ({ url: `/supply-chain/suppliers/${id}`, method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: ['Supplier', ...OVERVIEW],
    }),
    deleteSupplier: build.mutation({
      query: ({ id, version }) => ({ url: `/supply-chain/suppliers/${id}`, method: 'DELETE', params: { version } }), invalidatesTags: ['Supplier', ...OVERVIEW],
    }),

    supplyTemplates: build.query({ query: () => '/supply-chain/templates', transformResponse: unwrap, providesTags: ['SupplyTemplate'] }),
    createSupplyTemplate: build.mutation({
      query: (body) => ({ url: '/supply-chain/templates', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['SupplyTemplate'],
    }),
    updateSupplyTemplate: build.mutation({
      query: ({ id, ...body }) => ({ url: `/supply-chain/templates/${id}`, method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: ['SupplyTemplate'],
    }),
    supplyTemplateAction: build.mutation({
      query: ({ id, ...body }) => ({ url: `/supply-chain/templates/${id}/actions`, method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['SupplyTemplate'],
    }),

    supplierQuestionnaires: build.query({
      query: (params = {}) => ({ url: '/supply-chain/questionnaires', params }), transformResponse: unwrap, providesTags: ['SupplierQuestionnaire'],
    }),
    supplierQuestionnaire: build.query({ query: (id) => `/supply-chain/questionnaires/${id}`, transformResponse: unwrap, providesTags: ['SupplierQuestionnaire'] }),
    sendQuestionnaire: build.mutation({
      query: (body) => ({ url: '/supply-chain/questionnaires', method: 'POST', body }), transformResponse: unwrap,
      invalidatesTags: ['SupplierQuestionnaire', 'Supplier', 'SupplyTemplate', ...OVERVIEW],
    }),
    resendQuestionnaire: build.mutation({
      query: ({ id, version }) => ({ url: `/supply-chain/questionnaires/${id}/resend`, method: 'POST', params: { version } }), transformResponse: unwrap,
      invalidatesTags: ['SupplierQuestionnaire'],
    }),
    questionnaireAction: build.mutation({
      query: ({ id, ...body }) => ({ url: `/supply-chain/questionnaires/${id}/actions`, method: 'POST', body }), transformResponse: unwrap,
      invalidatesTags: ['SupplierQuestionnaire', 'Supplier', ...OVERVIEW],
    }),

    supplyActions: build.query({ query: (params = {}) => ({ url: '/supply-chain/actions', params }), transformResponse: unwrap, providesTags: ['SupplyAction'] }),
    createSupplyAction: build.mutation({
      query: (body) => ({ url: '/supply-chain/actions', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['SupplyAction', 'Supplier', ...OVERVIEW],
    }),
    updateSupplyAction: build.mutation({
      query: ({ id, ...body }) => ({ url: `/supply-chain/actions/${id}`, method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: ['SupplyAction', ...OVERVIEW],
    }),
    supplyActionStep: build.mutation({
      query: ({ id, ...body }) => ({ url: `/supply-chain/actions/${id}/steps`, method: 'POST', body }), transformResponse: unwrap,
      invalidatesTags: ['SupplyAction', 'Supplier', ...OVERVIEW],
    }),

    supplierSpend: build.query({ query: (params = {}) => ({ url: '/supply-chain/spend', params }), transformResponse: unwrap, providesTags: ['SupplySpend'] }),
    spendCategories: build.query({ query: () => '/supply-chain/spend/categories', transformResponse: unwrap }),
    createSpend: build.mutation({
      query: (body) => ({ url: '/supply-chain/spend', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['SupplySpend', 'Emission', 'Supplier', ...OVERVIEW],
    }),
    updateSpend: build.mutation({
      query: ({ id, ...body }) => ({ url: `/supply-chain/spend/${id}`, method: 'PUT', body }), transformResponse: unwrap,
      invalidatesTags: ['SupplySpend', 'Emission', 'Supplier', ...OVERVIEW],
    }),
    deleteSpend: build.mutation({
      query: ({ id, version }) => ({ url: `/supply-chain/spend/${id}`, method: 'DELETE', params: { version } }),
      invalidatesTags: ['SupplySpend', 'Emission', 'Supplier', ...OVERVIEW],
    }),
    importSpend: build.mutation({
      query: (file) => ({ url: '/supply-chain/spend/import', method: 'POST', body: form(file) }), transformResponse: unwrap,
      invalidatesTags: ['SupplySpend', 'Emission', 'Supplier', ...OVERVIEW],
    }),

    // The supplier's side: no session, the link's token names the questionnaire.
    publicQuestionnaire: build.query({ query: (token) => `/public/supplier-questionnaires/${encodeURIComponent(token)}`, transformResponse: unwrap }),
    savePublicAnswers: build.mutation({
      query: ({ token, answers }) => ({ url: `/public/supplier-questionnaires/${encodeURIComponent(token)}/answers`, method: 'PUT', body: { answers } }),
      transformResponse: unwrap,
    }),
    uploadPublicEvidence: build.mutation({
      query: ({ token, file }) => ({ url: `/public/supplier-questionnaires/${encodeURIComponent(token)}/evidence`, method: 'POST', body: form(file) }),
      transformResponse: unwrap,
    }),
    submitPublicQuestionnaire: build.mutation({
      query: (token) => ({ url: `/public/supplier-questionnaires/${encodeURIComponent(token)}/submit`, method: 'POST' }), transformResponse: unwrap,
    }),
  }),
});

export const {
  useSupplyOverviewQuery, useSupplyPeopleQuery,
  useSuppliersQuery, useSupplierQuery, useCreateSupplierMutation, useUpdateSupplierMutation, useDeleteSupplierMutation,
  useSupplyTemplatesQuery, useCreateSupplyTemplateMutation, useUpdateSupplyTemplateMutation, useSupplyTemplateActionMutation,
  useSupplierQuestionnairesQuery, useSupplierQuestionnaireQuery, useSendQuestionnaireMutation, useResendQuestionnaireMutation, useQuestionnaireActionMutation,
  useSupplyActionsQuery, useCreateSupplyActionMutation, useUpdateSupplyActionMutation, useSupplyActionStepMutation,
  useSupplierSpendQuery, useSpendCategoriesQuery, useCreateSpendMutation, useUpdateSpendMutation, useDeleteSpendMutation, useImportSpendMutation,
  usePublicQuestionnaireQuery, useSavePublicAnswersMutation, useUploadPublicEvidenceMutation, useSubmitPublicQuestionnaireMutation,
} = supplyApi;
