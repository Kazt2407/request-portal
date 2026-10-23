import { accessToken } from '@/auth'

/** An RFC 9457 problem from the API, with per-field messages for validation errors. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly title: string,
    readonly detail: string | undefined,
    /** Field path (e.g. "fields[0].key") to message. */
    readonly fieldErrors: Record<string, string> = {},
    /** For bulk actions: request to the reason it failed. */
    readonly failures: Record<string, string> = {},
  ) {
    super(detail ?? title)
  }
}

type QueryValue = string | number | boolean | null | undefined
export type Query = Record<string, QueryValue | QueryValue[]>

interface Options {
  query?: Query
  body?: unknown
  headers?: Record<string, string>
}

const BASE = '/api/v1'
const TITLES: Record<number, string> = {
  401: 'Your session has ended',
  403: 'You are not allowed to do this',
  404: 'Not found',
  429: 'Too many requests; wait a moment and try again',
  502: 'A service is not responding',
  503: 'The service is unavailable',
}

export function url(path: string, query?: Query): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query ?? {})) {
    for (const item of Array.isArray(value) ? value : [value]) {
      if (item !== null && item !== undefined && item !== '') {
        params.append(key, String(item))
      }
    }
  }
  const search = params.toString()
  return BASE + path + (search ? `?${search}` : '')
}

async function send(method: string, path: string, options: Options, accept: string): Promise<Response> {
  const headers: Record<string, string> = {
    Accept: accept,
    Authorization: `Bearer ${await accessToken()}`,
    ...options.headers,
  }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  const response = await fetch(url(path, options.query), {
    method,
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })
  if (!response.ok) {
    throw await toApiError(response)
  }
  return response
}

export async function toApiError(response: Response): Promise<ApiError> {
  let problem: { title?: string; detail?: string; errors?: Record<string, string>; failures?: Record<string, string> } = {}
  if (response.headers.get('Content-Type')?.includes('json')) {
    problem = (await response.json().catch(() => ({}))) as typeof problem
  }
  return new ApiError(
    response.status,
    TITLES[response.status] ?? problem.title ?? `Request failed (${response.status})`,
    problem.detail,
    problem.errors ?? {},
    problem.failures ?? {},
  )
}

async function json<T>(method: string, path: string, options: Options = {}): Promise<T> {
  const response = await send(method, path, options, 'application/json')
  return (response.status === 204 ? undefined : await response.json()) as T
}

export interface DownloadedFile {
  blob: Blob
  fileName: string
}

/** Calls an endpoint that answers with a file, such as an Excel export. */
export async function download(method: 'GET' | 'POST', path: string, options: Options = {}): Promise<DownloadedFile> {
  const response = await send(method, path, options, '*/*')
  return { blob: await response.blob(), fileName: fileNameOf(response.headers.get('Content-Disposition')) }
}

/** The file name from a Content-Disposition header, preferring the UTF-8 form (Vietnamese names). */
export function fileNameOf(disposition: string | null): string {
  const encoded = disposition?.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  if (encoded) {
    return decodeURIComponent(encoded)
  }
  return disposition?.match(/filename="?([^";]+)"?/i)?.[1] ?? 'download'
}

/** Hands a downloaded file to the browser. */
export function saveFile({ blob, fileName }: DownloadedFile): void {
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = fileName
  link.click()
  setTimeout(() => URL.revokeObjectURL(link.href), 1000)
}

export const http = {
  get: <T>(path: string, query?: Query) => json<T>('GET', path, { query }),
  post: <T>(path: string, body?: unknown, headers?: Record<string, string>) => json<T>('POST', path, { body, headers }),
  put: <T>(path: string, body?: unknown) => json<T>('PUT', path, { body }),
}
