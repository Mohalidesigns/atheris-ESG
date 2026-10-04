import { apiSlice, unwrap } from '../../services/apiSlice';

/** In-app notifications and per-user channel preferences (backend: NotificationController, WP-15). */
export const notificationsApi = apiSlice.injectEndpoints({
  endpoints: (build) => ({
    unreadCount: build.query({
      query: () => '/notifications/unread-count',
      transformResponse: (r) => r.data.unread,
      providesTags: ['Notification'],
    }),
    listNotifications: build.query({
      query: ({ unreadOnly = false, page = 0, size = 10 } = {}) => ({ url: '/notifications', params: { unreadOnly, page, size } }),
      transformResponse: unwrap,
      providesTags: ['Notification'],
    }),
    markNotificationRead: build.mutation({
      query: (id) => ({ url: `/notifications/${id}/read`, method: 'PUT' }),
      invalidatesTags: ['Notification'],
    }),
    markAllNotificationsRead: build.mutation({
      query: () => ({ url: '/notifications/read-all', method: 'PUT' }),
      invalidatesTags: ['Notification'],
    }),
    notificationPreferences: build.query({
      query: () => '/notifications/preferences', transformResponse: unwrap, providesTags: ['NotificationPrefs'],
    }),
    updateNotificationPreferences: build.mutation({
      query: (preferences) => ({ url: '/notifications/preferences', method: 'PUT', body: { preferences } }),
      transformResponse: unwrap,
      invalidatesTags: ['NotificationPrefs'],
    }),
  }),
});

export const {
  useUnreadCountQuery, useListNotificationsQuery, useMarkNotificationReadMutation, useMarkAllNotificationsReadMutation,
  useNotificationPreferencesQuery, useUpdateNotificationPreferencesMutation,
} = notificationsApi;
