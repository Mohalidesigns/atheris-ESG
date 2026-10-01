import { apiSlice, unwrap } from '../../services/apiSlice';

/** Reference catalogues and the framework library (backend: ReferenceController, ComplianceController; WP-17). */
export const referenceApi = apiSlice.injectEndpoints({
  endpoints: (build) => ({
    metrics: build.query({
      query: ({ pillar, framework } = {}) => ({ url: '/reference/metrics', params: { pillar, framework } }),
      transformResponse: unwrap,
      providesTags: ['Reference'],
    }),
    currencies: build.query({ query: () => '/reference/currencies', transformResponse: unwrap, providesTags: ['Reference'] }),
    countries: build.query({ query: () => '/reference/countries', transformResponse: unwrap, providesTags: ['Reference'] }),
    sdgs: build.query({ query: () => '/reference/sdgs', transformResponse: unwrap, providesTags: ['Reference'] }),
    scope3Categories: build.query({ query: () => '/reference/scope3-categories', transformResponse: unwrap, providesTags: ['Reference'] }),
    sectors: build.query({ query: () => '/reference/sectors', transformResponse: unwrap, providesTags: ['Reference'] }),
    frameworks: build.query({ query: () => '/compliance/frameworks', transformResponse: unwrap, providesTags: ['Framework'] }),
    requirements: build.query({
      query: (code) => `/compliance/frameworks/${encodeURIComponent(code)}/requirements`,
      transformResponse: unwrap,
      providesTags: ['Framework'],
    }),
    mappings: build.query({
      query: (framework) => ({ url: '/compliance/mappings', params: { framework } }),
      transformResponse: unwrap,
      providesTags: ['Framework'],
    }),
    gapAnalysis: build.query({
      query: ({ framework, year }) => ({ url: '/compliance/gaps', params: { framework, year } }),
      transformResponse: unwrap,
      providesTags: ['Gap'],
    }),
  }),
});

export const {
  useMetricsQuery, useCurrenciesQuery, useCountriesQuery, useSdgsQuery, useScope3CategoriesQuery, useSectorsQuery,
  useFrameworksQuery, useRequirementsQuery, useMappingsQuery, useGapAnalysisQuery,
} = referenceApi;
