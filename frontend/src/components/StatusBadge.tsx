export type BadgeState = 'emerald' | 'amber' | 'red' | 'slate' | 'sky' | 'violet';

/**
 * Status badge colored by severity semantics:
 * red = failing/critical/open, amber = degraded/in-progress/waiting,
 * emerald = healthy/resolved/available.
 */
const CLASSES: Record<BadgeState, string> = {
  emerald: 'bg-emerald-950/70 text-emerald-300 border-emerald-800',
  amber: 'bg-amber-950/70 text-amber-300 border-amber-800',
  red: 'bg-red-950/70 text-red-300 border-red-800',
  slate: 'bg-slate-800/80 text-slate-300 border-slate-700',
  sky: 'bg-sky-950/70 text-sky-300 border-sky-800',
  violet: 'bg-violet-950/70 text-violet-300 border-violet-800',
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
