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
    listRoles: build.query({ query: () => '/admin/roles', transformResponse: unwrap, providesTags: ['Role'] }),
    listOrganisations: build.query({
      query: () => '/admin/organisations', transformResponse: unwrap, providesTags: ['Organisation'],
    }),
  }),
});

export const {
  useListUsersQuery, useGetUserQuery, useCreateUserMutation, useUpdateUserMutation, useDeactivateUserMutation,
  useReplaceUserRolesMutation, useListRolesQuery, useListOrganisationsQuery,
} = adminApi;
