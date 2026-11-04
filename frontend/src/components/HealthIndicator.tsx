import { useEffect, useState } from 'react'
import { isServerUp } from '../api/endpoints.ts'
import { sleep } from '../lib/sleep.ts'

const CHECK_EVERY_MS = 20_000

/** A dot showing whether the backend answers /actuator/health. */
export function HealthIndicator() {
  const [up, setUp] = useState<boolean | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    const { signal } = controller

    async function watch() {
      while (!signal.aborted) {
        const result = await isServerUp(signal)
        if (signal.aborted) {
          return
        }
        setUp(result)
        await sleep(CHECK_EVERY_MS, signal)
      }
    }

    watch()
    return () => controller.abort()
  }, [])

  const state = up === null ? 'unknown' : up ? 'up' : 'down'
  const label = up === null ? 'Checking server' : up ? 'Server online' : 'Server offline'
  return (
    <span className={`health health-${state}`} role="status">
      <span className="health-dot" aria-hidden="true" />
      <span className="health-label">{label}</span>
    </span>
  )
}
