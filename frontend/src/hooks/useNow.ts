import { useEffect, useState } from 'react'

/**
 * The current time, refreshed every `everyMs`. Components read the time from
 * here instead of calling Date.now() while rendering, so texts like
 * "5 minutes ago" stay current.
 */
export function useNow(everyMs = 30_000): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const interval = setInterval(() => setNow(Date.now()), everyMs)
    return () => clearInterval(interval)
  }, [everyMs])
  return now
}
