import { apiSlice, unwrap } from '../../services/apiSlice';

/** M8 Governance & Ethics (backend: GovernanceController, PublicWhistleblowerController — WP-34). */
export const governanceApi = apiSlice.injectEndpoints({
  endpoints: (build) => ({
    boardMembers: build.query({
      query: (params = {}) => ({ url: '/governance/board-members', params }), transformResponse: unwrap, providesTags: ['Board'],
    }),
    boardComposition: build.query({ query: () => '/governance/board-composition', transformResponse: unwrap, providesTags: ['Board'] }),
    codeCheck: build.query({
      query: (ruleSet) => ({ url: '/governance/code-check', params: ruleSet ? { ruleSet } : {} }), transformResponse: unwrap, providesTags: ['Board'],
    }),
    createBoardMember: build.mutation({
      query: (body) => ({ url: '/governance/board-members', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['Board', 'Gap'],
    }),
    updateBoardMember: build.mutation({
      query: ({ id, ...body }) => ({ url: `/governance/board-members/${id}`, method: 'PUT', body }), transformResponse: unwrap,
      invalidatesTags: ['Board', 'Gap'],
    }),
    deleteBoardMember: build.mutation({
      query: ({ id, version }) => ({ url: `/governance/board-members/${id}`, method: 'DELETE', params: { version } }), invalidatesTags: ['Board', 'Gap'],
    }),

    policies: build.query({
      query: (status) => ({ url: '/governance/policies', params: status ? { status } : {} }), transformResponse: unwrap, providesTags: ['Policy'],
    }),
    policy: build.query({ query: (id) => `/governance/policies/${id}`, transformResponse: unwrap, providesTags: (_r, _e, id) => [{ type: 'Policy', id }] }),
    createPolicy: build.mutation({
      query: (body) => ({ url: '/governance/policies', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['Policy'],
    }),
    updatePolicy: build.mutation({
      query: ({ id, ...body }) => ({ url: `/governance/policies/${id}`, method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: ['Policy'],
    }),
    deletePolicy: build.mutation({
      query: ({ id, version }) => ({ url: `/governance/policies/${id}`, method: 'DELETE', params: { version } }), invalidatesTags: ['Policy'],
    }),
    /** action: submit | approve | send_back | publish | review | retire | revise */
    transitionPolicy: build.mutation({
      query: ({ id, action, version, reason }) => ({ url: `/governance/policies/${id}/${action}`, method: 'PUT', body: { version, ...(reason ? { reason } : {}) } }),
      transformResponse: unwrap, invalidatesTags: ['Policy', 'MyPolicy', 'Gap'],
    }),
    myPolicies: build.query({ query: () => '/governance/my-policies', transformResponse: unwrap, providesTags: ['MyPolicy'] }),
    acknowledgePolicy: build.mutation({
      query: (id) => ({ url: `/governance/my-policies/${id}/acknowledge`, method: 'POST' }), transformResponse: unwrap, invalidatesTags: ['MyPolicy', 'Policy'],
    }),

    whistleblowerCases: build.query({
      query: (status) => ({ url: '/governance/whistleblower/cases', params: status ? { status } : {} }), transformResponse: unwrap, providesTags: ['Case'],
    }),
    whistleblowerCase: build.query({
      query: (id) => `/governance/whistleblower/cases/${id}`, transformResponse: unwrap, providesTags: (_r, _e, id) => [{ type: 'Case', id }],
    }),
    updateCase: build.mutation({
      query: ({ id, ...body }) => ({ url: `/governance/whistleblower/cases/${id}`, method: 'PUT', body }), transformResponse: unwrap,
      invalidatesTags: ['Case'],
    }),
    addCaseNote: build.mutation({
      query: ({ id, note }) => ({ url: `/governance/whistleblower/cases/${id}/notes`, method: 'POST', body: { note } }), transformResponse: unwrap,
      invalidatesTags: (_r, _e, { id }) => [{ type: 'Case', id }],
    }),
    investigators: build.query({ query: () => '/governance/whistleblower/investigators', transformResponse: unwrap }),
    caseStatistics: build.query({ query: () => '/governance/whistleblower/statistics', transformResponse: unwrap, providesTags: ['Case'] }),
    whistleblowerChannel: build.query({ query: () => '/governance/whistleblower/channel', transformResponse: unwrap }),

    // Public (no session): the anonymous channel.
    submitWhistleblowerReport: build.mutation({
      query: ({ tenant, ...body }) => ({ url: `/public/whistleblower/${encodeURIComponent(tenant)}`, method: 'POST', body }), transformResponse: unwrap,
    }),
    whistleblowerStatus: build.mutation({
      query: ({ tenant, ...body }) => ({ url: `/public/whistleblower/${encodeURIComponent(tenant)}/status`, method: 'POST', body }), transformResponse: unwrap,
    }),
  }),
});

export const {
  useBoardMembersQuery, useBoardCompositionQuery, useCodeCheckQuery, useCreateBoardMemberMutation, useUpdateBoardMemberMutation,
  useDeleteBoardMemberMutation, usePoliciesQuery, usePolicyQuery, useCreatePolicyMutation, useUpdatePolicyMutation, useDeletePolicyMutation,
  useTransitionPolicyMutation, useMyPoliciesQuery, useAcknowledgePolicyMutation, useWhistleblowerCasesQuery, useWhistleblowerCaseQuery,
  useUpdateCaseMutation, useAddCaseNoteMutation, useInvestigatorsQuery, useCaseStatisticsQuery, useWhistleblowerChannelQuery,
  useSubmitWhistleblowerReportMutation, useWhistleblowerStatusMutation,
} = governanceApi;
