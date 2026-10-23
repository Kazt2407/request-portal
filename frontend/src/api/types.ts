// JSON shapes of the API, mirroring the services' records. Dates are ISO-8601 strings.

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

// request-service

export type RequestStatus = 'IN_PROGRESS' | 'COMPLETED' | 'REJECTED' | 'CANCELLED'
export type StepStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'REJECTED' | 'CANCELLED'
export type FieldType = 'TEXT' | 'TEXTAREA' | 'SELECT' | 'FILE' | 'IMAGE' | 'TERM' | 'SCHOOL_YEAR'
export type Audience = 'STUDENT' | 'LECTURER' | 'ALL'
export type TemplateStatus = 'ACTIVE' | 'INACTIVE'
export type HistoryAction =
  | 'SUBMITTED'
  | 'STEP_COMPLETED'
  | 'STEP_REJECTED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'RESULT_RECEIVED'
  | 'RESULT_NOT_RECEIVED'

export interface Actor {
  id: string
  name: string
}

export interface Requester {
  id: string
  name: string
  email: string
}

export interface CurrentStep {
  position: number
  departmentCode: string
  description: string
  dueAt: string
  overdue: boolean
}

export interface RequestSummary {
  id: number
  code: string
  title: string
  departmentCode: string
  requester: Requester
  status: RequestStatus
  currentStep: CurrentStep | null
  resultReceived: boolean
  submittedAt: string
  finishedAt: string | null
}

/** Text for most fields; file ids for FILE and IMAGE fields. */
export interface FieldValue {
  key: string
  label: string
  type: FieldType
  value: string | string[] | null
}

export interface RequestStep {
  position: number
  departmentCode: string
  description: string
  slaDays: number
  requiresAttachment: boolean
  status: StepStatus
  startedAt: string | null
  dueAt: string | null
  overdue: boolean
  finishedAt: string | null
  finishedBy: Actor | null
  note: string | null
  attachmentIds: string[]
}

export interface HistoryEntry {
  action: HistoryAction
  stepPosition: number | null
  actor: Actor
  note: string | null
  at: string
}

export interface RequestDetail {
  id: number
  code: string
  templateId: number
  title: string
  departmentCode: string
  requester: Requester
  status: RequestStatus
  /** Position of the step in progress. */
  currentStep: number | null
  resultReceived: boolean
  submittedAt: string
  finishedAt: string | null
  values: FieldValue[]
  steps: RequestStep[]
  history: HistoryEntry[]
  version: number
}

export interface RequestFilter {
  q?: string
  templateId?: number | null
  status?: RequestStatus | ''
  awaitingMe?: boolean
  resultReceived?: boolean | null
  submittedFrom?: string
  submittedTo?: string
  departmentCode?: string
}

export interface BulkResult {
  count: number
  codes: string[]
}

export interface TemplateSummary {
  id: number
  title: string
  description: string | null
  departmentCode: string
  audience: Audience
  status: TemplateStatus
  fieldCount: number
  stepCount: number
  totalSlaDays: number
  updatedAt: string
}

export interface TemplateField {
  key: string
  label: string
  description: string | null
  hint: string | null
  type: FieldType
  required: boolean
  options: string[]
  uploadFolder: string | null
  uploadNote: string | null
}

export interface TemplateStep {
  position?: number
  departmentCode: string
  description: string
  slaDays: number
  requiresAttachment: boolean
}

export interface Template {
  id: number
  title: string
  description: string | null
  departmentCode: string
  audience: Audience
  status: TemplateStatus
  showProcessingTime: boolean
  fields: TemplateField[]
  steps: TemplateStep[]
  createdAt: string
  updatedAt: string
  version: number
}

export interface TemplateInput {
  title: string
  description: string | null
  departmentCode: string
  audience: Audience
  status: TemplateStatus
  showProcessingTime: boolean
  fields: TemplateField[]
  steps: TemplateStep[]
  version: number | null
}

export interface TemplateStatistics {
  templateId: number
  title: string
  total: number
  inProgress: number
  completed: number
  rejected: number
  cancelled: number
  overdue: number
  averageDaysToComplete: number | null
}

export interface DepartmentStatistics {
  departmentCode: string
  inProgress: number
  overdue: number
  completed: number
  rejected: number
  onTimeRate: number | null
  averageDaysPerStep: number | null
}

export interface Statistics {
  byTemplate: TemplateStatistics[]
  byDepartment: DepartmentStatistics[]
}

export interface StatisticsFilter {
  submittedFrom?: string
  submittedTo?: string
  departmentCode?: string
  templateId?: number | null
}

// org-service

export type Role = 'STUDENT' | 'LECTURER' | 'STAFF' | 'ADMIN'

export interface Account {
  id: string
  email: string
  fullName: string
  roles: Role[]
  department: { id: number; code: string; name: string } | null
  lastSeenAt: string | null
  createdAt: string
}

export interface Department {
  id: number
  code: string
  name: string
  email: string | null
  version: number
}

export interface Term {
  id: number
  code: string
  schoolYear: string
  termNumber: number
  name: string
  startsOn: string
  endsOn: string
}

export interface StudentProfile {
  accountId: string
  studentCode: string
  dateOfBirth: string | null
  className: string | null
  faculty: string | null
  major: string | null
  phone: string | null
  version: number
}

export type StudentProfileInput = Omit<StudentProfile, 'accountId' | 'version'> & { version: number | null }

// document-service

export type FilePurpose = 'REQUEST_FIELD' | 'STEP_ATTACHMENT' | 'FORM_TEMPLATE'
export type FileStatus = 'PENDING' | 'UPLOADED' | 'ATTACHED'

export interface UploadTicket {
  fileId: string
  uploadUrl: string
  method: string
  headers: Record<string, string>
  expiresAt: string
}

export interface FileInfo {
  id: string
  fileName: string
  contentType: string
  size: number
  purpose: FilePurpose
  status: FileStatus
  requestId: number | null
  createdAt: string
}

export interface DownloadLink {
  url: string
  expiresAt: string
}

export type FormFieldType = 'TEXT' | 'NUMBER' | 'DATE'

export interface FormField {
  key: string
  label: string
  hint: string | null
  type: FormFieldType
  required: boolean
}

export interface Form {
  id: number
  title: string
  description: string | null
  audience: Audience
  active: boolean
  templateFileId: string
  /** Placeholders found in the .docx, e.g. full_name or amount_words. */
  placeholders: string[]
  fields: FormField[]
  updatedAt: string
  version: number
}

export interface FormInput {
  title: string
  description: string | null
  audience: Audience
  active: boolean
  templateFileId: string | null
  fields: FormField[]
  version: number | null
}

export type BulkDownloadStatus = 'PENDING' | 'RUNNING' | 'READY' | 'FAILED' | 'EXPIRED'

export interface BulkDownloadPreview {
  requests: number
  visibleRequests: number
  files: number
  totalBytes: number
  problem: string | null
}

export interface BulkDownloadJob {
  id: string
  status: BulkDownloadStatus
  requests: number
  files: number | null
  totalBytes: number | null
  error: string | null
  createdAt: string
  finishedAt: string | null
  expiresAt: string | null
  downloadUrl: string | null
}

// notification-service

export type NotificationStatus = 'PENDING' | 'SENT' | 'FAILED' | 'SKIPPED'

export interface Notification {
  id: number
  requestId: number
  requestCode: string
  kind: string
  recipient: string
  subject: string
  status: NotificationStatus
  attempts: number
  nextAttemptAt: string | null
  lastError: string | null
  createdAt: string
  sentAt: string | null
}
