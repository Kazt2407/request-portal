import { ApiError } from './api/http'

/** The validation message for one field path, e.g. "values.reason" or "fields[0].key". */
export function fieldError(error: unknown, path: string): string | undefined {
  return error instanceof ApiError ? error.fieldErrors[path] : undefined
}

/** A one-line description of any error, for alerts and toasts. */
export function describe(error: unknown): string {
  if (error instanceof ApiError) {
    return error.detail && error.detail !== error.title ? `${error.title}: ${error.detail}` : error.title
  }
  return error instanceof Error ? error.message : 'Something went wrong'
}
