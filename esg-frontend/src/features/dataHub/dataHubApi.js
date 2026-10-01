import { apiSlice, unwrap } from '../../services/apiSlice';

/** ESG Data Hub (backend: DataHubController). */
export const dataHubApi = apiSlice.injectEndpoints({
  endpoints: (build) => ({
    dataPoints: build.query({
      query: (params) => ({ url: '/data/points', params }),
      transformResponse: unwrap,
      providesTags: ['DataPoint'],
    }),
    createDataPoint: build.mutation({
      query: (body) => ({ url: '/data/points', method: 'POST', body }),
      transformResponse: unwrap,
      invalidatesTags: ['DataPoint'],
    }),
    reviewDataPoint: build.mutation({
      query: ({ id, status }) => ({ url: `/data/points/${id}/validate`, method: 'PUT', body: { status } }),
      transformResponse: unwrap,
      invalidatesTags: ['DataPoint'],
    }),
  }),
});

export const { useDataPointsQuery, useCreateDataPointMutation, useReviewDataPointMutation } = dataHubApi;
