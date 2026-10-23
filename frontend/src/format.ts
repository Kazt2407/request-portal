/** Dates are shown in the university's time zone, whatever the browser's. */
const TIME_ZONE = 'Asia/Ho_Chi_Minh'

const dateTimeFormat = new Intl.DateTimeFormat('en-GB', {
  timeZone: TIME_ZONE,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})
const dateFormat = new Intl.DateTimeFormat('en-GB', { timeZone: TIME_ZONE, day: '2-digit', month: '2-digit', year: 'numeric' })

export function dateTime(iso: string | null | undefined): string {
  return iso ? dateTimeFormat.format(new Date(iso)).replace(',', '') : '—'
}

/** For ISO timestamps and plain dates (2026-10-23) alike. */
export function date(iso: string | null | undefined): string {
  if (!iso) {
    return '—'
  }
  return /^\d{4}-\d{2}-\d{2}$/.test(iso) ? iso.split('-').reverse().join('/') : dateFormat.format(new Date(iso))
}

/** "due in 2 days", "due today", "3 days overdue". */
export function due(iso: string | null | undefined, now = new Date()): string {
  if (!iso) {
    return ''
  }
  const hours = (new Date(iso).getTime() - now.getTime()) / 3_600_000
  if (hours < 0) {
    const days = Math.ceil(-hours / 24)
    return days <= 1 ? 'overdue' : `${days} days overdue`
  }
  if (hours < 24) {
    return 'due today'
  }
  const days = Math.round(hours / 24)
  return `due in ${days} day${days === 1 ? '' : 's'}`
}

export function bytes(size: number | null | undefined): string {
  if (size === null || size === undefined) {
    return '—'
  }
  if (size < 1024) {
    return `${size} B`
  }
  const units = ['KB', 'MB', 'GB']
  let value = size / 1024
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit++
  }
  return `${value < 10 ? value.toFixed(1) : Math.round(value)} ${units[unit]}`
}

/** IN_PROGRESS becomes "In progress". */
export function label(constant: string | null | undefined): string {
  if (!constant) {
    return ''
  }
  const words = constant.replace(/_/g, ' ').toLowerCase()
  return words.charAt(0).toUpperCase() + words.slice(1)
}

export function percent(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : `${Math.round(value * 100)}%`
}

export function decimal(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : value.toFixed(1)
}
