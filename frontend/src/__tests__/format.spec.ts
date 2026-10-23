import { describe, expect, it } from 'vitest'
import { bytes, date, dateTime, due, label } from '@/format'

describe('format', () => {
  it('shows times in Vietnam whatever the browser time zone', () => {
    expect(dateTime('2026-10-22T13:52:55Z')).toBe('22/10/2026 20:52')
    expect(date('2026-10-22T20:00:00Z')).toBe('23/10/2026')
    expect(date('2003-05-14')).toBe('14/05/2003')
    expect(dateTime(null)).toBe('—')
  })

  it('describes deadlines', () => {
    const now = new Date('2026-10-23T00:00:00Z')
    expect(due('2026-10-26T00:00:00Z', now)).toBe('due in 3 days')
    expect(due('2026-10-24T00:00:00Z', now)).toBe('due in 1 day')
    expect(due('2026-10-23T05:00:00Z', now)).toBe('due today')
    expect(due('2026-10-22T20:00:00Z', now)).toBe('overdue')
    expect(due('2026-10-20T00:00:00Z', now)).toBe('3 days overdue')
  })

  it('formats sizes and constants', () => {
    expect(bytes(71)).toBe('71 B')
    expect(bytes(1536)).toBe('1.5 KB')
    expect(bytes(20 * 1024 * 1024)).toBe('20 MB')
    expect(label('IN_PROGRESS')).toBe('In progress')
  })
})
