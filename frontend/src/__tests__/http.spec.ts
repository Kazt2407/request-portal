import { describe, expect, it } from 'vitest'
import { ApiError, fileNameOf, toApiError, url } from '@/api/http'

describe('url', () => {
  it('leaves out empty filters and repeats array values', () => {
    expect(url('/requests', { q: '', status: 'IN_PROGRESS', templateId: null, awaitingMe: false, page: 0 })).toBe(
      '/api/v1/requests?status=IN_PROGRESS&awaitingMe=false&page=0',
    )
    expect(url('/accounts', { role: ['STAFF', 'ADMIN'] })).toBe('/api/v1/accounts?role=STAFF&role=ADMIN')
    expect(url('/templates')).toBe('/api/v1/templates')
  })
})

describe('fileNameOf', () => {
  it('prefers the UTF-8 file name, so Vietnamese names survive', () => {
    expect(fileNameOf(`attachment; filename="Bang diem.pdf"; filename*=UTF-8''B%E1%BA%A3ng%20%C4%91i%E1%BB%83m.pdf`)).toBe(
      'Bảng điểm.pdf',
    )
    expect(fileNameOf('attachment; filename="ACAD-0042.docx"')).toBe('ACAD-0042.docx')
    expect(fileNameOf(null)).toBe('download')
  })
})

describe('toApiError', () => {
  const problem = (status: number, body: unknown) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/problem+json' } })

  it('keeps per-field messages of validation errors', async () => {
    const error = await toApiError(problem(400, { title: 'Bad Request', errors: { reason: 'is required' } }))

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(400)
    expect(error.fieldErrors).toEqual({ reason: 'is required' })
  })

  it('keeps the reason each request of a bulk action failed', async () => {
    const error = await toApiError(problem(422, { title: 'Unprocessable', detail: '1 request could not be changed', failures: { 'ACAD-0042': 'step 1 is not current' } }))

    expect(error.failures).toEqual({ 'ACAD-0042': 'step 1 is not current' })
    expect(error.message).toBe('1 request could not be changed')
  })

  it('explains statuses that come without a body, such as the rate limit', async () => {
    const error = await toApiError(new Response(null, { status: 429 }))

    expect(error.title).toMatch(/too many requests/i)
  })
})
