/** The app mark: a few connected notes. */
export function Logo({ size = 28 }: { size?: number }) {
  return (
    <svg className="logo" viewBox="0 0 32 32" width={size} height={size} aria-hidden="true">
      <rect width="32" height="32" rx="8" className="logo-bg" />
      <g className="logo-lines" strokeWidth="2" strokeLinecap="round" fill="none">
        <path d="M10 11 16 16 22 10M16 16l-4 6m4-6 6 5" />
      </g>
      <g className="logo-dots">
        <circle cx="10" cy="11" r="2.5" />
        <circle cx="22" cy="10" r="2.5" />
        <circle cx="16" cy="16" r="3" />
        <circle cx="12" cy="22" r="2.5" />
        <circle cx="22" cy="21" r="2.5" />
      </g>
    </svg>
  )
}
