import { apiSlice, unwrap } from '../../services/apiSlice';

const MOVES = ['CreditLot', 'Retirement', 'OffsetProject', 'Gap', 'Report'];

/** M11 Carbon Market (backend: CarbonMarketController — WP-41, D36). Registry and exchange are Sandbox adapters. */
export const carbonMarketApi = apiSlice.injectEndpoints({
  endpoints: (build) => ({
    carbonMarketOverview: build.query({ query: (year) => ({ url: '/carbon-market/overview', params: year ? { year } : {} }), transformResponse: unwrap,
      providesTags: ['CreditLot', 'Retirement', 'CarbonMarketSettings'] }),
    offsetting: build.query({ query: (year) => ({ url: '/carbon-market/offsetting', params: { year } }), transformResponse: unwrap,
      providesTags: ['Retirement', 'CarbonMarketSettings'] }),

    offsetProjects: build.query({ query: () => '/carbon-market/projects', transformResponse: unwrap, providesTags: ['OffsetProject'] }),
    createOffsetProject: build.mutation({ query: (body) => ({ url: '/carbon-market/projects', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: MOVES }),
    updateOffsetProject: build.mutation({
      query: ({ id, ...body }) => ({ url: `/carbon-market/projects/${id}`, method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: MOVES,
    }),
    deleteOffsetProject: build.mutation({ query: (id) => ({ url: `/carbon-market/projects/${id}`, method: 'DELETE' }), invalidatesTags: MOVES }),
    listings: build.query({ query: (q) => ({ url: '/carbon-market/listings', params: { q } }), transformResponse: unwrap }),

    creditLots: build.query({ query: () => '/carbon-market/credits', transformResponse: unwrap, providesTags: ['CreditLot'] }),
    createCreditLot: build.mutation({ query: (body) => ({ url: '/carbon-market/credits', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: MOVES }),
    updateCreditLot: build.mutation({
      query: ({ id, ...body }) => ({ url: `/carbon-market/credits/${id}`, method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: MOVES,
    }),
    deleteCreditLot: build.mutation({ query: (id) => ({ url: `/carbon-market/credits/${id}`, method: 'DELETE' }), invalidatesTags: MOVES }),
    verifyCreditLot: build.mutation({
      query: ({ id, ...body }) => ({ url: `/carbon-market/credits/${id}/verify`, method: 'POST', body }), transformResponse: unwrap, invalidatesTags: MOVES,
    }),
    registryCheck: build.mutation({ query: (id) => ({ url: `/carbon-market/credits/${id}/registry-check` }), transformResponse: unwrap }),

    retirements: build.query({ query: (params = {}) => ({ url: '/carbon-market/retirements', params }), transformResponse: unwrap, providesTags: ['Retirement'] }),
    requestRetirement: build.mutation({ query: (body) => ({ url: '/carbon-market/retire', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: MOVES }),
    retirementAction: build.mutation({
      query: ({ id, ...body }) => ({ url: `/carbon-market/retirements/${id}/actions`, method: 'POST', body }), transformResponse: unwrap, invalidatesTags: MOVES,
    }),

    carbonMarketSettings: build.query({ query: () => '/carbon-market/settings', transformResponse: unwrap, providesTags: ['CarbonMarketSettings'] }),
    updateCarbonMarketSettings: build.mutation({
      query: (body) => ({ url: '/carbon-market/settings', method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: ['CarbonMarketSettings'],
    }),
    internalPrices: build.query({ query: () => '/carbon-market/internal-prices', transformResponse: unwrap, providesTags: ['InternalPrice'] }),
    createInternalPrice: build.mutation({
      query: (body) => ({ url: '/carbon-market/internal-prices', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['InternalPrice'],
    }),
    updateInternalPrice: build.mutation({
      query: ({ id, ...body }) => ({ url: `/carbon-market/internal-prices/${id}`, method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: ['InternalPrice'],
    }),
    deleteInternalPrice: build.mutation({ query: (id) => ({ url: `/carbon-market/internal-prices/${id}`, method: 'DELETE' }), invalidatesTags: ['InternalPrice'] }),
    exposure: build.query({ query: (params) => ({ url: '/carbon-market/internal-prices/exposure', params }), transformResponse: unwrap, providesTags: ['InternalPrice'] }),
  }),
});

export const {
  useCarbonMarketOverviewQuery, useOffsettingQuery, useOffsetProjectsQuery, useCreateOffsetProjectMutation, useUpdateOffsetProjectMutation,
  useDeleteOffsetProjectMutation, useListingsQuery, useCreditLotsQuery, useCreateCreditLotMutation, useUpdateCreditLotMutation, useDeleteCreditLotMutation,
  useVerifyCreditLotMutation, useRegistryCheckMutation, useRetirementsQuery, useRequestRetirementMutation, useRetirementActionMutation,
  useCarbonMarketSettingsQuery, useUpdateCarbonMarketSettingsMutation, useInternalPricesQuery, useCreateInternalPriceMutation, useUpdateInternalPriceMutation,
  useDeleteInternalPriceMutation, useExposureQuery,
} = carbonMarketApi;
