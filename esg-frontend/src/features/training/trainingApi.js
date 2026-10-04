import { apiSlice, unwrap } from '../../services/apiSlice';

const blob = (url) => ({ url, responseHandler: (r) => r.blob(), cache: 'no-store' });
const ALL = ['TrainingCourse', 'TrainingPath', 'Enrollment', 'Certificate'];

/** M12 Training (backend: TrainingController, PublicCertificateController — WP-42, D37). */
export const trainingApi = apiSlice.injectEndpoints({
  endpoints: (build) => ({
    trainingOverview: build.query({ query: () => '/training/overview', transformResponse: unwrap, providesTags: ALL }),

    courses: build.query({ query: () => '/training/courses', transformResponse: unwrap, providesTags: ['TrainingCourse'] }),
    course: build.query({ query: (id) => `/training/courses/${id}`, transformResponse: unwrap, providesTags: ['TrainingCourse'] }),
    createCourse: build.mutation({ query: (body) => ({ url: '/training/courses', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ALL }),
    updateCourse: build.mutation({ query: ({ id, ...body }) => ({ url: `/training/courses/${id}`, method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: ALL }),
    deleteCourse: build.mutation({ query: (id) => ({ url: `/training/courses/${id}`, method: 'DELETE' }), invalidatesTags: ALL }),
    courseAction: build.mutation({
      query: ({ id, ...body }) => ({ url: `/training/courses/${id}/actions`, method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ALL,
    }),

    paths: build.query({ query: () => '/training/paths', transformResponse: unwrap, providesTags: ['TrainingPath'] }),
    createPath: build.mutation({ query: (body) => ({ url: '/training/paths', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ALL }),
    updatePath: build.mutation({ query: ({ id, ...body }) => ({ url: `/training/paths/${id}`, method: 'PUT', body }), transformResponse: unwrap, invalidatesTags: ALL }),
    deletePath: build.mutation({ query: (id) => ({ url: `/training/paths/${id}`, method: 'DELETE' }), invalidatesTags: ALL }),
    assignPath: build.mutation({ query: (body) => ({ url: '/training/paths/assign', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ALL }),

    trainingPeople: build.query({ query: () => '/training/people', transformResponse: unwrap }),
    enrollments: build.query({ query: (params = {}) => ({ url: '/training/enrollments', params }), transformResponse: unwrap, providesTags: ['Enrollment'] }),
    enrol: build.mutation({ query: (courseId) => ({ url: `/training/courses/${courseId}/enrol`, method: 'POST' }), transformResponse: unwrap, invalidatesTags: ALL }),
    assign: build.mutation({ query: (body) => ({ url: '/training/enrollments', method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ALL }),
    moduleDone: build.mutation({
      query: ({ id, moduleId, version }) => ({ url: `/training/enrollments/${id}/modules/${moduleId}/done`, method: 'POST', params: { version } }),
      transformResponse: unwrap, invalidatesTags: ['Enrollment'],
    }),
    submitQuiz: build.mutation({
      query: ({ id, ...body }) => ({ url: `/training/enrollments/${id}/quiz`, method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ALL,
    }),
    recordAttendance: build.mutation({
      query: ({ id, ...body }) => ({ url: `/training/enrollments/${id}/attendance`, method: 'POST', body }), transformResponse: unwrap, invalidatesTags: ALL,
    }),
    withdraw: build.mutation({
      query: ({ id, version }) => ({ url: `/training/enrollments/${id}/withdraw`, method: 'POST', params: { version } }), transformResponse: unwrap, invalidatesTags: ALL,
    }),

    myProgress: build.query({ query: () => '/training/progress', transformResponse: unwrap, providesTags: ['Enrollment', 'TrainingPath'] }),
    team: build.query({ query: () => '/training/team', transformResponse: unwrap, providesTags: ALL }),

    certificates: build.query({ query: () => '/training/certificates', transformResponse: unwrap, providesTags: ['Certificate', 'Enrollment'] }),
    downloadCertificate: build.mutation({ query: (id) => blob(`/training/certificates/${id}/pdf`) }),
    revokeCertificate: build.mutation({
      query: ({ id, note }) => ({ url: `/training/certificates/${id}/revoke`, method: 'POST', body: { note } }), transformResponse: unwrap, invalidatesTags: ALL,
    }),
    verifyCertificate: build.query({ query: (code) => `/public/certificates/${encodeURIComponent(code)}`, transformResponse: unwrap }),
  }),
});

export const {
  useTrainingOverviewQuery, useCoursesQuery, useCourseQuery, useCreateCourseMutation, useUpdateCourseMutation, useDeleteCourseMutation, useCourseActionMutation,
  usePathsQuery, useCreatePathMutation, useUpdatePathMutation, useDeletePathMutation, useAssignPathMutation, useTrainingPeopleQuery, useEnrollmentsQuery,
  useEnrolMutation, useAssignMutation, useModuleDoneMutation, useSubmitQuizMutation, useRecordAttendanceMutation, useWithdrawMutation, useMyProgressQuery,
  useTeamQuery, useCertificatesQuery, useDownloadCertificateMutation, useRevokeCertificateMutation, useVerifyCertificateQuery,
} = trainingApi;
