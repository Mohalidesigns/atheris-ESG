import { apiSlice, unwrap } from '../../services/apiSlice';

/** Admin Console API (backend: AdminUserController, AdminController). */
export const adminApi = apiSlice.injectEndpoints({
  endpoints: (build) => ({
    listUsers: build.query({
      query: ({ search = '', page = 0, size = 20 } = {}) => ({ url: '/admin/users', params: { search, page, size } }),
      transformResponse: unwrap,
      providesTags: (result) => [
        { type: 'User', id: 'LIST' },
        ...(result?.content ?? []).map((u) => ({ type: 'User', id: u.id })),
      ],
    }),
    getUser: build.query({
      query: (id) => `/admin/users/${id}`,
      transformResponse: unwrap,
      providesTags: (_r, _e, id) => [{ type: 'User', id }],
    }),
    createUser: build.mutation({
      query: (body) => ({ url: '/admin/users', method: 'POST', body }),
      transformResponse: unwrap,
      invalidatesTags: [{ type: 'User', id: 'LIST' }],
    }),
    updateUser: build.mutation({
      query: ({ id, ...body }) => ({ url: `/admin/users/${id}`, method: 'PUT', body }),
      transformResponse: unwrap,
      invalidatesTags: (_r, _e, { id }) => [{ type: 'User', id }, { type: 'User', id: 'LIST' }],
    }),
    deactivateUser: build.mutation({
      query: (id) => ({ url: `/admin/users/${id}/deactivate`, method: 'POST' }),
      transformResponse: unwrap,
      invalidatesTags: (_r, _e, id) => [{ type: 'User', id }, { type: 'User', id: 'LIST' }],
    }),
    replaceUserRoles: build.mutation({
      query: ({ id, assignments }) => ({ url: `/admin/users/${id}/roles`, method: 'PUT', body: { assignments } }),
      transformResponse: unwrap,
      invalidatesTags: (_r, _e, { id }) => [{ type: 'User', id }, { type: 'User', id: 'LIST' }],
    }),
    /** { assignable: true }: only roles the caller may grant (the role picker; S4). */
    listRoles: build.query({
      query: (arg) => (arg?.assignable ? { url: '/admin/roles', params: { assignable: true } } : '/admin/roles'),
      transformResponse: unwrap,
      providesTags: ['Role'],
    }),
    resetUserMfa: build.mutation({
      query: (id) => ({ url: `/admin/users/${id}/reset-mfa`, method: 'POST' }),
      transformResponse: unwrap,
      invalidatesTags: (_r, _e, id) => [{ type: 'User', id }, { type: 'User', id: 'LIST' }],
    }),
    setUserPassword: build.mutation({
      query: ({ id, password }) => ({ url: `/admin/users/${id}/password`, method: 'POST', body: { password } }),
      transformResponse: unwrap,
    }),

    // Organisations (WP-14) — flat list with parentOrgId; see components/org/orgTree.js.
    listOrganisations: build.query({ query: () => '/organisations', transformResponse: unwrap, providesTags: ['Organisation'] }),
    createOrganisation: build.mutation({
      query: (body) => ({ url: '/organisations', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['Organisation'],
    }),
    updateOrganisation: build.mutation({
      query: ({ id, ...body }) => ({ url: `/organisations/${id}`, method: 'PUT', body }),
      transformResponse: unwrap, invalidatesTags: ['Organisation'],
    }),
    deleteOrganisation: build.mutation({
      query: (id) => ({ url: `/organisations/${id}`, method: 'DELETE' }), invalidatesTags: ['Organisation'],
    }),

    // Reporting periods
    listPeriods: build.query({ query: () => '/reporting-periods', transformResponse: unwrap, providesTags: ['Period'] }),
    createPeriod: build.mutation({
      query: (body) => ({ url: '/reporting-periods', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['Period'],
    }),
    updatePeriod: build.mutation({
      query: ({ id, ...body }) => ({ url: `/reporting-periods/${id}`, method: 'PUT', body }),
      transformResponse: unwrap, invalidatesTags: ['Period'],
    }),
    setPeriodLock: build.mutation({
      query: ({ id, locked }) => ({ url: `/reporting-periods/${id}/${locked ? 'lock' : 'unlock'}`, method: 'POST' }),
      transformResponse: unwrap, invalidatesTags: ['Period'],
    }),

    // Tenant settings
    getSettings: build.query({ query: () => '/settings', transformResponse: unwrap, providesTags: ['Settings'] }),
    updateSettings: build.mutation({
      query: (body) => ({ url: '/settings', method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: ['Settings'],
    }),
    listFrameworks: build.query({ query: () => '/compliance/frameworks', transformResponse: unwrap }),

    // Platform tenants (SUPER_ADMIN)
    listTenants: build.query({ query: () => '/platform/tenants', transformResponse: unwrap, providesTags: ['Tenant'] }),
    createTenant: build.mutation({
      query: (body) => ({ url: '/platform/tenants', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['Tenant'],
    }),
    updateTenant: build.mutation({
      query: ({ id, ...body }) => ({ url: `/platform/tenants/${id}`, method: 'PUT', body }),
      transformResponse: unwrap, invalidatesTags: ['Tenant', 'Subscription'],
    }),

    // Subscription and module entitlements (WP-19). Tenants read theirs; the platform changes them.
    getSubscription: build.query({ query: () => '/subscription', transformResponse: unwrap, providesTags: ['Subscription'] }),
    getTenantSubscription: build.query({
      query: (id) => `/platform/tenants/${id}/subscription`, transformResponse: unwrap,
      providesTags: (_r, _e, id) => [{ type: 'Subscription', id }],
    }),
    setTenantModule: build.mutation({
      query: ({ tenantId, moduleCode, enabled, reason }) => ({
        url: `/platform/tenants/${tenantId}/modules/${moduleCode}`, method: 'PUT', body: { enabled, reason },
      }),
      transformResponse: unwrap,
      invalidatesTags: (_r, _e, { tenantId }) => [{ type: 'Subscription', id: tenantId }, 'Subscription'],
    }),
  }),
});

export const {
  useListUsersQuery, useGetUserQuery, useCreateUserMutation, useUpdateUserMutation, useDeactivateUserMutation,
  useReplaceUserRolesMutation, useListRolesQuery, useResetUserMfaMutation, useSetUserPasswordMutation,
  useListOrganisationsQuery, useCreateOrganisationMutation, useUpdateOrganisationMutation, useDeleteOrganisationMutation,
  useListPeriodsQuery, useCreatePeriodMutation, useUpdatePeriodMutation, useSetPeriodLockMutation,
  useGetSettingsQuery, useUpdateSettingsMutation, useListFrameworksQuery,
  useListTenantsQuery, useCreateTenantMutation, useUpdateTenantMutation,
  useGetSubscriptionQuery, useGetTenantSubscriptionQuery, useSetTenantModuleMutation,
} = adminApi;
