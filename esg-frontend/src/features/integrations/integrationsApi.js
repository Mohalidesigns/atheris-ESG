import { apiSlice, unwrap } from '../../services/apiSlice';

/** WP-46 (D44): connectors on request, API keys, webhooks, and the platform team's request queue. */
export const integrationsApi = apiSlice.injectEndpoints({
  endpoints: (build) => ({
    connectors: build.query({ query: () => '/integrations/connectors', transformResponse: unwrap, providesTags: ['IntegrationRequest'] }),
    integrationRequests: build.query({ query: () => '/integrations/requests', transformResponse: unwrap, providesTags: ['IntegrationRequest'] }),
    requestConnector: build.mutation({
      query: (body) => ({ url: '/integrations/requests', method: 'POST', body }),
      transformResponse: unwrap, invalidatesTags: ['IntegrationRequest'],
    }),
    withdrawRequest: build.mutation({
      query: ({ id, version }) => ({ url: `/integrations/requests/${id}/withdraw`, method: 'POST', params: { version } }),
      transformResponse: unwrap, invalidatesTags: ['IntegrationRequest'],
    }),

    apiKeys: build.query({ query: () => '/integrations/api-keys', transformResponse: unwrap, providesTags: ['ApiKey'] }),
    apiKeyScopes: build.query({ query: () => '/integrations/api-keys/scopes', transformResponse: unwrap }),
    createApiKey: build.mutation({
      query: (body) => ({ url: '/integrations/api-keys', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['ApiKey'],
    }),
    revokeApiKey: build.mutation({
      query: (id) => ({ url: `/integrations/api-keys/${id}/revoke`, method: 'POST' }), transformResponse: unwrap, invalidatesTags: ['ApiKey'],
    }),

    webhookEvents: build.query({ query: () => '/integrations/webhooks/events', transformResponse: unwrap }),
    webhooks: build.query({ query: () => '/integrations/webhooks', transformResponse: unwrap, providesTags: ['Webhook'] }),
    createWebhook: build.mutation({
      query: (body) => ({ url: '/integrations/webhooks', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ['Webhook'],
    }),
    updateWebhook: build.mutation({
      query: ({ id, ...body }) => ({ url: `/integrations/webhooks/${id}`, method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: ['Webhook'],
    }),
    deleteWebhook: build.mutation({
      query: ({ id, version }) => ({ url: `/integrations/webhooks/${id}`, method: 'DELETE', params: { version } }), invalidatesTags: ['Webhook'],
    }),
    rotateWebhookSecret: build.mutation({
      query: (id) => ({ url: `/integrations/webhooks/${id}/rotate-secret`, method: 'POST' }), transformResponse: unwrap, invalidatesTags: ['Webhook'],
    }),
    testWebhook: build.mutation({
      query: (id) => ({ url: `/integrations/webhooks/${id}/test`, method: 'POST' }), transformResponse: unwrap,
      invalidatesTags: ['Webhook', 'WebhookDelivery'],
    }),
    webhookDeliveries: build.query({
      query: (id) => `/integrations/webhooks/${id}/deliveries`, transformResponse: unwrap, providesTags: ['WebhookDelivery'],
    }),
    redeliver: build.mutation({
      query: (id) => ({ url: `/integrations/webhook-deliveries/${id}/redeliver`, method: 'POST' }), transformResponse: unwrap,
      invalidatesTags: ['Webhook', 'WebhookDelivery'],
    }),

    // The OpenAPI description (D44): served to signed-in integrators; saved as a file.
    apiDescription: build.mutation({ query: () => ({ url: '/api-docs', responseHandler: (r) => r.text(), cache: 'no-store' }) }),

    platformRequests: build.query({
      query: () => '/platform/integration-requests', transformResponse: unwrap, providesTags: ['PlatformIntegrationRequest'],
    }),
    setRequestStatus: build.mutation({
      query: ({ id, ...body }) => ({ url: `/platform/integration-requests/${id}/status`, method: 'POST', body }),
      transformResponse: unwrap, invalidatesTags: ['PlatformIntegrationRequest'],
    }),
  }),
});

export const {
  useConnectorsQuery, useIntegrationRequestsQuery, useRequestConnectorMutation, useWithdrawRequestMutation,
  useApiKeysQuery, useApiKeyScopesQuery, useCreateApiKeyMutation, useRevokeApiKeyMutation,
  useWebhookEventsQuery, useWebhooksQuery, useCreateWebhookMutation, useUpdateWebhookMutation, useDeleteWebhookMutation,
  useRotateWebhookSecretMutation, useTestWebhookMutation, useWebhookDeliveriesQuery, useRedeliverMutation,
  useApiDescriptionMutation, usePlatformRequestsQuery, useSetRequestStatusMutation,
} = integrationsApi;
