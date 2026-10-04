import { apiSlice, unwrap } from '../../services/apiSlice';

/** WP-43 (D43): POST /data/sync and the conflicts that wait for the person who captured the value. */
export const offlineApi = apiSlice.injectEndpoints({
  endpoints: (build) => ({
    syncOffline: build.mutation({
      query: (body) => ({ url: '/data/sync', method: 'POST', body }),
      transformResponse: unwrap,
      invalidatesTags: (result) => (result?.applied || result?.conflicts ? ['DataPoint', 'Incident', 'SyncConflict', 'SocialOverview'] : []),
    }),
    syncConflicts: build.query({ query: () => '/data/sync/conflicts', transformResponse: unwrap, providesTags: ['SyncConflict'] }),
    resolveConflict: build.mutation({
      query: ({ id, resolution, version }) => ({ url: `/data/sync/conflicts/${id}/resolve`, method: 'POST', body: { resolution, version } }),
      transformResponse: unwrap,
      invalidatesTags: ['SyncConflict', 'DataPoint'],
    }),
  }),
});

export const { useSyncOfflineMutation, useSyncConflictsQuery, useResolveConflictMutation } = offlineApi;
