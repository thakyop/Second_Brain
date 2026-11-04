/** 1536 → "1.5 KB". Uses 1024, like the backend's "20MB" upload limit. */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  const units = ['KB', 'MB', 'GB']
  let value = bytes / 1024
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit++
  }
  return `${value < 10 ? value.toFixed(1) : Math.round(value)} ${units[unit]}`
}

const relative = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })

/** "just now", "5 minutes ago", "yesterday", or a date for anything older than a week. */
export function timeAgo(isoTime: string, now = Date.now()): string {
  const seconds = Math.round((now - Date.parse(isoTime)) / 1000)
  if (seconds < 45) {
    return 'just now'
  }
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) {
    return relative.format(-minutes, 'minute')
  }
  const hours = Math.round(minutes / 60)
  if (hours < 24) {
    return relative.format(-hours, 'hour')
  }
  const days = Math.round(hours / 24)
  if (days < 7) {
    return relative.format(-days, 'day')
  }
  return formatDate(isoTime)
}

/** "4 Nov 2026" */
export function formatDate(isoTime: string): string {
  return new Date(isoTime).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
}
