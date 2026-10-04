import { apiSlice, unwrap } from '../../services/apiSlice';

const blob = (url) => ({ url, responseHandler: (r) => r.blob(), cache: 'no-store' });

/** NDPA 2023 controls (backend: PrivacyController — WP-52, D40). */
export const privacyApi = apiSlice.injectEndpoints({
  endpoints: (build) => ({
    myNotice: build.query({ query: () => '/privacy/notice', transformResponse: unwrap, providesTags: ['PrivacyNotice'] }),
    acknowledgeNotice: build.mutation({
      query: (version) => ({ url: '/privacy/notice/acknowledge', method: 'POST', body: { version } }), transformResponse: unwrap, invalidatesTags: ['PrivacyNotice'],
    }),
    myData: build.mutation({ query: () => blob('/privacy/my-data') }),

    notices: build.query({ query: () => '/privacy/notices', transformResponse: unwrap, providesTags: ['PrivacyNotice'] }),
    publishNotice: build.mutation({ query: (body) => ({ url: '/privacy/notices', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['PrivacyNotice'] }),
    privacyPeople: build.query({ query: () => '/privacy/people', transformResponse: unwrap }),
    subjectRequests: build.query({ query: () => '/privacy/requests', transformResponse: unwrap, providesTags: ['SubjectRequest'] }),
    createSubjectRequest: build.mutation({ query: (body) => ({ url: '/privacy/requests', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['SubjectRequest'] }),
    subjectRequestAction: build.mutation({
      query: ({ id, ...body }) => ({ url: `/privacy/requests/${id}/actions`, method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['SubjectRequest'],
    }),
    exportSubjectData: build.mutation({ query: (id) => blob(`/privacy/requests/${id}/export`) }),
    eraseSubject: build.mutation({
      query: ({ id, version }) => ({ url: `/privacy/requests/${id}/erase`, method: 'POST', params: { version } }), transformResponse: unwrap,
      invalidatesTags: ['SubjectRequest', 'User'],
    }),
    retention: build.query({ query: () => '/privacy/retention', transformResponse: unwrap, providesTags: ['Retention'] }),
    updateRetention: build.mutation({ query: (body) => ({ url: '/privacy/retention', method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: ['Retention'] }),
  }),
});

export const {
  useMyNoticeQuery, useAcknowledgeNoticeMutation, useMyDataMutation, useNoticesQuery, usePublishNoticeMutation, usePrivacyPeopleQuery, useSubjectRequestsQuery,
  useCreateSubjectRequestMutation, useSubjectRequestActionMutation, useExportSubjectDataMutation, useEraseSubjectMutation, useRetentionQuery,
  useUpdateRetentionMutation,
} = privacyApi;
