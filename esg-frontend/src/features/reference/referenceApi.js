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
    /** IANA time zones the server accepts for the reporting time zone (F10). */
    timeZones: build.query({ query: () => '/reference/timezones', transformResponse: unwrap, providesTags: ['Reference'] }),
    frameworks: build.query({ query: () => '/compliance/frameworks', transformResponse: unwrap, providesTags: ['Framework'] }),
    requirements: build.query({
      query: (code) => `/compliance/frameworks/${encodeURIComponent(code)}/requirements`,
      transformResponse: unwrap,
      providesTags: ['Framework'],
    }),
    /** Phase 3: a framework's content versions (imports and bundled packs). */
    contentVersions: build.query({
      query: (code) => `/compliance/frameworks/${encodeURIComponent(code)}/content-versions`,
      transformResponse: unwrap,
      providesTags: ['Framework'],
    }),
    /** Phase 3: platform import of requirement content (multipart). dryRun=true only checks and diffs. */
    importFrameworkContent: build.mutation({
      query: ({ code, file, ...fields }) => {
        const form = new FormData();
        form.append('file', file);
        Object.entries(fields).forEach(([k, v]) => { if (v !== '' && v != null) form.append(k, String(v)); });
        return { url: `/platform/frameworks/${encodeURIComponent(code)}/content`, method: 'POST', body: form };
      },
      transformResponse: unwrap,
      invalidatesTags: (r) => (r?.applied ? ['Framework', 'Gap', 'ComplianceStatus', 'Reference'] : []),
    }),
    mappings: build.query({
      query: (framework) => ({ url: '/compliance/mappings', params: { framework } }),
      transformResponse: unwrap,
      providesTags: ['Framework'],
    }),
  }),
});

export const {
  useMetricsQuery, useCurrenciesQuery, useCountriesQuery, useSdgsQuery, useScope3CategoriesQuery, useSectorsQuery, useTimeZonesQuery,
  useFrameworksQuery, useRequirementsQuery, useMappingsQuery, useContentVersionsQuery, useImportFrameworkContentMutation,
} = referenceApi;
