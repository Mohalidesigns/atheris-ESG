import { apiSlice, unwrap } from '../../services/apiSlice';

/** Executive dashboard (backend: DashboardController, WP-24). Sections the caller can't read come back empty. */
export const dashboardApi = apiSlice.injectEndpoints({
  endpoints: (build) => ({
    executiveDashboard: build.query({
      query: (granularity = 'year') => ({ url: '/dashboard/executive', params: { granularity } }),
      transformResponse: unwrap,
      providesTags: ['DataPoint', 'Emission', 'ComplianceStatus', 'Deadline', 'Report', 'RegChange', 'Period', 'SocialOverview', 'RiskOverview', 'SupplyOverview'],
    }),
  }),
});

export const { useExecutiveDashboardQuery } = dashboardApi;
