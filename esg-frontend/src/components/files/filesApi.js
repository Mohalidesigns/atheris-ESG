import { apiSlice, unwrap } from '../../services/apiSlice';

const asForm = (file, params = {}) => {
  const form = new FormData();
  form.append('file', file);
  Object.entries(params).forEach(([k, v]) => v != null && form.append(k, v));
  return form;
};

/** File service (backend: FileController, WP-15). */
export const filesApi = apiSlice.injectEndpoints({
  endpoints: (build) => ({
    storageInfo: build.query({ query: () => '/files/storage-info', transformResponse: unwrap }),
    listFiles: build.query({
      query: ({ resourceType, resourceId }) => ({ url: '/files', params: { resourceType, resourceId } }),
      transformResponse: unwrap,
      providesTags: (_r, _e, { resourceType, resourceId }) => [{ type: 'File', id: `${resourceType}:${resourceId}` }],
    }),
    uploadFile: build.mutation({
      query: ({ file, resourceType, resourceId }) => ({ url: '/files', method: 'POST', body: asForm(file, { resourceType, resourceId }) }),
      transformResponse: unwrap,
      invalidatesTags: (_r, _e, { resourceType, resourceId }) => [{ type: 'File', id: `${resourceType}:${resourceId}` }],
    }),
    uploadFileVersion: build.mutation({
      query: ({ id, file }) => ({ url: `/files/${id}/versions`, method: 'POST', body: asForm(file) }),
      transformResponse: unwrap,
      invalidatesTags: (_r, _e, { resourceType, resourceId }) => [{ type: 'File', id: `${resourceType}:${resourceId}` }],
    }),
    deleteFile: build.mutation({
      query: ({ id }) => ({ url: `/files/${id}`, method: 'DELETE' }),
      invalidatesTags: (_r, _e, { resourceType, resourceId }) => [{ type: 'File', id: `${resourceType}:${resourceId}` }],
    }),
    downloadUrl: build.mutation({
      query: ({ id, version }) => ({ url: `/files/${id}/download-url`, params: version ? { version } : undefined }),
      transformResponse: unwrap,
    }),
    // Same-origin content needs the bearer token, so it's fetched as a blob rather than linked to directly.
    fileContent: build.mutation({
      query: ({ id, version }) => ({
        url: `/files/${id}/content`, params: version ? { version } : undefined, responseHandler: (r) => r.blob(), cache: 'no-store',
      }),
    }),
  }),
});

export const {
  useStorageInfoQuery, useListFilesQuery, useUploadFileMutation, useUploadFileVersionMutation, useDeleteFileMutation,
  useDownloadUrlMutation, useFileContentMutation,
} = filesApi;
