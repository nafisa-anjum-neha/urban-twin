export type BadgeState = 'emerald' | 'amber' | 'red' | 'slate' | 'sky' | 'violet';

/**
 * Status badge colored by severity semantics:
 * red = failing/critical/open, amber = degraded/in-progress/waiting,
 * emerald = healthy/resolved/available.
 */
const CLASSES: Record<BadgeState, string> = {
  emerald:
    'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/70 dark:text-emerald-300 dark:border-emerald-800',
  amber:
    'bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-950/70 dark:text-amber-300 dark:border-amber-800',
  red: 'bg-red-50 text-red-700 border-red-200 dark:bg-red-950/70 dark:text-red-300 dark:border-red-800',
  slate:
    'bg-slate-100 text-slate-600 border-slate-300 dark:bg-slate-800/80 dark:text-slate-300 dark:border-slate-700',
  sky: 'bg-sky-50 text-sky-700 border-sky-200 dark:bg-sky-950/70 dark:text-sky-300 dark:border-sky-800',
  violet:
    'bg-violet-50 text-violet-700 border-violet-200 dark:bg-violet-950/70 dark:text-violet-300 dark:border-violet-800',
};

const RED = new Set([
  'critical', 'failed', 'faulty', 'severe', 'open', 'p1', 'offline', 'active',
  'outage', 'cancelled',
]);
const AMBER = new Set([
  'warning', 'degraded', 'moderate', 'in_progress', 'dispatched', 'planned',
  'submitted', 'watch', 'high', 'p2', 'arrived', 'acknowledged', 'assigned', 'en_route',
]);
const EMERALD = new Set([
  'normal', 'operational', 'low', 'available', 'resolved', 'completed', 'closed',
  'ok', 'active_done', 'planned_ok',
]);
const SKY = new Set(['primary', 'secondary', 'hw', 'ar', 'co', 'lo']);

export function badgeState(status: string | null | undefined): BadgeState {
  const s = (status ?? '').toLowerCase();
  if (RED.has(s)) return 'red';
  if (AMBER.has(s)) return 'amber';
  if (EMERALD.has(s)) return 'emerald';
  if (SKY.has(s)) return 'sky';
  if (['medical', 'fire_engine'].includes(s)) return 'sky';
  if (['report', 'pothole', 'leak'].includes(s)) return 'violet';
  return 'slate';
}

export default function StatusBadge({
  status,
  label,
  state,
}: {
  status?: string | null;
  label?: string;
  state?: BadgeState;
}) {
  const resolved = state ?? badgeState(status);
  if (!status && !label) return null;
  return (
    <span
      className={[
        'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-[11px] font-medium capitalize whitespace-nowrap',
        CLASSES[resolved],
      ].join(' ')}
    >
      {label ?? status}
    </span>
  );
}
