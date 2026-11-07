export function LoadingState({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="flex items-center gap-3 py-8 text-sm text-slate-500">
      <span className="h-4 w-4 rounded-full border-2 border-slate-700 border-t-emerald-400 animate-spin" />
      {label}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message?: string; onRetry?: () => void }) {
  return (
    <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 flex items-center justify-between gap-4 dark:border-red-900/60 dark:bg-red-950/30 dark:text-red-300">
      <span>⚠ {message ?? 'Failed to load data from the UrbanTwin API.'}</span>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="shrink-0 rounded-lg border border-red-300 px-2.5 py-1 text-xs text-red-700 hover:bg-red-100 dark:border-red-800 dark:text-red-200 dark:hover:bg-red-950"
        >
          Retry
        </button>
      )}
    </div>
  );
}

export function EmptyState({ message, hint }: { message: string; hint?: string }) {
  return (
    <div className="rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-8 text-center dark:border-slate-700 dark:bg-slate-950/40">
      <p className="text-sm text-slate-500 dark:text-slate-400">{message}</p>
      {hint && <p className="mt-1 text-[11px] text-slate-400 dark:text-slate-600">{hint}</p>}
    </div>
  );
}
