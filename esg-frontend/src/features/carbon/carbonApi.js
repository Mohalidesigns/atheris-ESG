import { apiSlice, unwrap } from '../../services/apiSlice';

const crud = (build, path, tag, extraInvalidates = []) => ({
  list: build.query({ query: (params) => ({ url: path, params }), transformResponse: unwrap, providesTags: [tag] }),
  create: build.mutation({
    query: (body) => ({ url: path, method: 'POST', body }), transformResponse: unwrap, invalidatesTags: [tag, ...extraInvalidates],
  }),
  update: build.mutation({
    query: ({ id, ...body }) => ({ url: `${path}/${id}`, method: 'PUT', body }), transformResponse: unwrap,
    invalidatesTags: [tag, ...extraInvalidates],
  }),
  remove: build.mutation({
    query: ({ id, version }) => ({ url: `${path}/${id}`, method: 'DELETE', params: { version } }),
    invalidatesTags: [tag, ...extraInvalidates],
  }),
});

/** Carbon accounting (backend: com.esg.carbon.CarbonController, EmissionFactorController — WP-21). */
export const carbonApi = apiSlice.injectEndpoints({
  endpoints: (build) => {
    const flaring = crud(build, '/carbon/flaring', 'Flaring', ['Emission']);
    const instruments = crud(build, '/carbon/instruments', 'Instrument', ['Emission']);
    const targets = crud(build, '/carbon/targets', 'Target');
    const financed = crud(build, '/carbon/financed', 'Financed');
    return {
      emissions: build.query({ query: (params) => ({ url: '/carbon/emissions', params }), transformResponse: unwrap, providesTags: ['Emission'] }),
      emissionsSummary: build.query({
        query: (year) => ({ url: '/carbon/emissions/summary', params: year ? { year } : undefined }),
        transformResponse: unwrap,
        providesTags: ['Emission', 'Instrument', 'Flaring'],
      }),
      previewEmission: build.mutation({ query: (body) => ({ url: '/carbon/emissions/preview', method: 'POST', body }), transformResponse: unwrap }),
      createEmission: build.mutation({
        query: (body) => ({ url: '/carbon/emissions', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['Emission', 'Target'],
      }),
      updateEmission: build.mutation({
        query: ({ id, ...body }) => ({ url: `/carbon/emissions/${id}`, method: 'PUT', body }), transformResponse: unwrap,
        invalidatesTags: ['Emission', 'Target'],
      }),
      deleteEmission: build.mutation({
        query: ({ id, version }) => ({ url: `/carbon/emissions/${id}`, method: 'DELETE', params: { version } }),
        invalidatesTags: ['Emission', 'Target'],
      }),
      verifyEmission: build.mutation({
        query: ({ id, version }) => ({ url: `/carbon/emissions/${id}/verify`, method: 'PUT', body: { version } }),
        transformResponse: unwrap, invalidatesTags: ['Emission'],
      }),
      recalculate: build.mutation({
        query: (body) => ({ url: '/carbon/calculate', method: 'POST', body }), transformResponse: unwrap,
        invalidatesTags: ['Emission', 'Target'],
      }),
      gwp: build.query({ query: () => '/carbon/gwp', transformResponse: unwrap }),

      /** Activities (factor categories) with their GHG scope, Scope 3 category and unit (F5/F11). */
      emissionActivities: build.query({
        query: () => '/carbon/factors/activities',
        transformResponse: unwrap,
        providesTags: ['Factor'],
      }),
      /** ISO countries and factor regions for the custom factor picker (F11). */
      factorRegions: build.query({ query: () => '/carbon/factors/regions', transformResponse: unwrap }),
      factors: build.query({
        query: ({ region, category } = {}) => ({ url: '/carbon/factors', params: { region, category } }),
        transformResponse: unwrap,
        providesTags: ['Factor'],
      }),
      createFactor: build.mutation({
        query: (body) => ({ url: '/carbon/factors', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['Factor'],
      }),
      updateFactor: build.mutation({
        query: ({ id, ...body }) => ({ url: `/carbon/factors/${id}`, method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: ['Factor'],
      }),

      flaring: flaring.list,
      createFlaring: flaring.create,
      updateFlaring: flaring.update,
      deleteFlaring: flaring.remove,
      satelliteCheck: build.mutation({
        query: (id) => ({ url: `/carbon/flaring/${id}/satellite-check`, method: 'POST' }), transformResponse: unwrap, invalidatesTags: ['Flaring'],
      }),
      instruments: instruments.list,
      createInstrument: instruments.create,
      updateInstrument: instruments.update,
      deleteInstrument: instruments.remove,
      targets: targets.list,
      createTarget: targets.create,
      updateTarget: targets.update,
      deleteTarget: targets.remove,
      financed: financed.list,
      createFinanced: financed.create,
      updateFinanced: financed.update,
      deleteFinanced: financed.remove,
      financedSummary: build.query({
        query: (year) => ({ url: '/carbon/financed/summary', params: { year } }), transformResponse: unwrap, providesTags: ['Financed'],
      }),
    };
  },
});

export const {
  useEmissionsQuery, useEmissionsSummaryQuery, usePreviewEmissionMutation, useCreateEmissionMutation, useUpdateEmissionMutation,
  useDeleteEmissionMutation, useVerifyEmissionMutation, useRecalculateMutation, useGwpQuery,
  useFactorsQuery, useCreateFactorMutation, useUpdateFactorMutation, useEmissionActivitiesQuery, useFactorRegionsQuery,
  useFlaringQuery, useCreateFlaringMutation, useUpdateFlaringMutation, useDeleteFlaringMutation, useSatelliteCheckMutation,
  useInstrumentsQuery, useCreateInstrumentMutation, useUpdateInstrumentMutation, useDeleteInstrumentMutation,
  useTargetsQuery, useCreateTargetMutation, useUpdateTargetMutation, useDeleteTargetMutation,
  useFinancedQuery, useCreateFinancedMutation, useUpdateFinancedMutation, useDeleteFinancedMutation, useFinancedSummaryQuery,
} = carbonApi;
