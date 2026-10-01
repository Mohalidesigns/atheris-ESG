import { apiSlice, unwrap } from '../../services/apiSlice';

/** Carbon accounting (backend: CarbonController, EmissionFactorController). */
export const carbonApi = apiSlice.injectEndpoints({
  endpoints: (build) => ({
    emissions: build.query({
      query: (params) => ({ url: '/carbon/emissions', params }),
      transformResponse: unwrap,
      providesTags: ['Emission'],
    }),
    emissionsSummary: build.query({ query: () => '/carbon/emissions/summary', transformResponse: unwrap, providesTags: ['Emission'] }),
    factors: build.query({
      query: ({ region, category } = {}) => ({ url: '/carbon/factors', params: { region, category } }),
      transformResponse: unwrap,
      providesTags: ['Factor'],
    }),
    createFactor: build.mutation({
      query: (body) => ({ url: '/carbon/factors', method: 'POST', body }),
      transformResponse: unwrap,
      invalidatesTags: ['Factor'],
    }),
    updateFactor: build.mutation({
      query: ({ id, ...body }) => ({ url: `/carbon/factors/${id}`, method: 'PUT', body }),
      transformResponse: unwrap,
      invalidatesTags: ['Factor'],
    }),
  }),
});

export const {
  useEmissionsQuery, useEmissionsSummaryQuery, useFactorsQuery, useCreateFactorMutation, useUpdateFactorMutation,
} = carbonApi;
