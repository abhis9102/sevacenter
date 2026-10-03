/** The SevaCenter mark: a diya (saffron flame over a maroon bowl), from the styleguide. */
export function Diya({ className = "size-9" }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 48 48" aria-hidden="true" focusable="false">
      <path
        className="fill-mark-flame"
        d="M24 5c3.2 5.4 1.9 8.6-.2 11.2-1.6 2-3.4 3.8-3.4 6.6a3.6 3.6 0 0 0 7.2.3c1.3 1.5 1.1 3.9-.6 5.2 3.6-.7 5.8-3.9 5.6-7.6-.2-4.3-3-6.4-4.2-9.8C24.4 8.6 24.3 6.6 24 5Z"
      />
      <path
        className="fill-mark-core"
        d="M24 12c1.5 2.8.8 4.6-.4 6.2-1.6 2.1-2.3 3.4-1.8 5.3.5-1.6 1.6-2.7 2.6-4 1.2-1.6 1.8-3.9-.4-7.5Z"
      />
      <path
        className="fill-mark-bowl"
        d="M8 33c4.5 3.6 11 5.2 16 5.2S35.5 36.6 40 33c-1.2 5-7.8 8.4-16 8.4S9.2 38 8 33Z"
      />
    </svg>
  );
}

export function Wordmark() {
  return (
    <span className="font-display text-xl font-semibold tracking-tight">
      Seva<span className="text-primary-strong">Center</span>
    </span>
  );
}
