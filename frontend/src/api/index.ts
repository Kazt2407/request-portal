import { download, http, type Query } from './http'
import type {
  Account,
  BulkDownloadJob,
  BulkDownloadPreview,
  BulkResult,
  Department,
  DownloadLink,
  FileInfo,
  Form,
  FormInput,
  Notification,
  Page,
  RequestDetail,
  RequestFilter,
  RequestStatus,
  RequestSummary,
  Role,
  Statistics,
  StatisticsFilter,
  StudentProfile,
  StudentProfileInput,
  Template,
  TemplateInput,
  TemplateStatus,
  TemplateSummary,
  Term,
} from './types'

export * from './types'
export { ApiError, saveFile } from './http'

export interface Paging {
  page?: number
  size?: number
  sort?: string
}

export const requests = {
  mine: (status: RequestStatus | '' = '', paging: Paging = {}) =>
    http.get<Page<RequestSummary>>('/requests/mine', { status, ...paging }),
  search: (filter: RequestFilter, paging: Paging = {}) =>
    http.get<Page<RequestSummary>>('/requests', { ...(filter as Query), ...paging }),
  get: (id: number) => http.get<RequestDetail>(`/requests/${id}`),
  /** The key makes a retried click return the first request instead of creating a second. */
  submit: (templateId: number, values: Record<string, unknown>, idempotencyKey: string) =>
    http.post<RequestDetail>('/requests', { templateId, values }, { 'Idempotency-Key': idempotencyKey }),
  cancel: (id: number) => http.post<RequestDetail>(`/requests/${id}/cancel`),
  complete: (id: number, position: number, note: string, attachmentIds: string[]) =>
    http.post<RequestDetail>(`/requests/${id}/steps/${position}/complete`, { note: note || null, attachmentIds }),
  reject: (id: number, position: number, reason: string) =>
    http.post<RequestDetail>(`/requests/${id}/steps/${position}/reject`, { reason }),
  bulkComplete: (requestIds: number[], note: string) =>
    http.post<BulkResult>('/requests/bulk/complete', { requestIds, note: note || null }),
  bulkReject: (requestIds: number[], reason: string) =>
    http.post<BulkResult>('/requests/bulk/reject', { requestIds, reason }),
  resultReceived: (id: number, received: boolean) =>
    http.put<RequestDetail>(`/requests/${id}/result-received`, { received }),
  bulkResultReceived: (requestIds: number[], received: boolean) =>
    http.put<BulkResult>('/requests/result-received', { requestIds, received }),
  document: (id: number) => download('GET', `/requests/${id}/document`),
  export: (filter: RequestFilter) => download('GET', '/requests/export', { query: filter as Query }),
  statistics: (filter: StatisticsFilter) => http.get<Statistics>('/requests/statistics', filter as Query),
  exportStatistics: (filter: StatisticsFilter) =>
    download('GET', '/requests/statistics/export', { query: filter as Query }),
}

export const templates = {
  available: () => http.get<TemplateSummary[]>('/templates/available'),
  list: (status: TemplateStatus | '' = '') => http.get<TemplateSummary[]>('/templates', { status }),
  get: (id: number) => http.get<Template>(`/templates/${id}`),
  create: (input: TemplateInput) => http.post<Template>('/templates', input),
  update: (id: number, input: TemplateInput) => http.put<Template>(`/templates/${id}`, input),
}

export const files = {
  get: (id: string) => http.get<FileInfo>(`/files/${id}`),
  downloadLink: (id: string) => http.get<DownloadLink>(`/files/${id}/download`),
  previewBulk: (requestIds: number[]) => http.post<BulkDownloadPreview>('/files/bulk-downloads/preview', { requestIds }),
  startBulk: (requestIds: number[]) => http.post<BulkDownloadJob>('/files/bulk-downloads', { requestIds }),
  bulkJob: (id: string) => http.get<BulkDownloadJob>(`/files/bulk-downloads/${id}`),
}

export const forms = {
  available: () => http.get<Form[]>('/forms/available'),
  list: () => http.get<Form[]>('/forms'),
  get: (id: number) => http.get<Form>(`/forms/${id}`),
  create: (input: FormInput) => http.post<Form>('/forms', input),
  update: (id: number, input: FormInput) => http.put<Form>(`/forms/${id}`, input),
  fill: (id: number, values: Record<string, string>) => download('POST', `/forms/${id}/document`, { body: { values } }),
}

export const accounts = {
  /** Also creates the account on the first call after sign-in. */
  me: () => http.get<Account>('/accounts/me'),
  search: (filter: { q?: string; role?: Role | ''; departmentId?: number | null }, paging: Paging = {}) =>
    http.get<Page<Account>>('/accounts', { ...filter, ...paging }),
  updateAccess: (id: string, roles: Role[], departmentId: number | null) =>
    http.put<Account>(`/accounts/${id}/access`, { roles, departmentId }),
}

export const departments = {
  list: () => http.get<Department[]>('/departments'),
  create: (input: { code: string; name: string; email: string | null }) => http.post<Department>('/departments', input),
  update: (id: number, input: { name: string; email: string | null; version: number }) =>
    http.put<Department>(`/departments/${id}`, input),
}

export const terms = {
  list: (schoolYear = '') => http.get<Term[]>('/terms', { schoolYear }),
  schoolYears: () => http.get<string[]>('/terms/school-years'),
  create: (input: { schoolYear: string; termNumber: number; name: string; startsOn: string; endsOn: string }) =>
    http.post<Term>('/terms', input),
}

export const profiles = {
  mine: () => http.get<StudentProfile>('/profiles/me'),
  get: (accountId: string) => http.get<StudentProfile>(`/profiles/${accountId}`),
  save: (accountId: string, input: StudentProfileInput) => http.put<StudentProfile>(`/profiles/${accountId}`, input),
}

export const notifications = {
  forRequest: (requestId: number) => http.get<Page<Notification>>('/notifications', { requestId, size: 50 }),
  retry: (id: number) => http.post<Notification>(`/notifications/${id}/retry`),
}
