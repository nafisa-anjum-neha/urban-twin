import { useTwinLive } from '../hooks/useTwinLive';
import { fmtClock, fmtNum, pct } from '../lib/format';
import { useTheme } from '../theme';
import { useQuery } from '@tanstack/react-query';
import StatusBadge from './StatusBadge';

const NET_STATE: Record<string, 'emerald' | 'amber' | 'red'> = {
  ok: 'emerald',
  degraded: 'amber',
  critical: 'red',
};

export default function Topbar() {
  const { snapshot, mode, lastUpdateMs, error } = useTwinLive();
  const { theme, toggle } = useTheme();

  // Push the ML service's self-reported backend tier (tf / sklearn / heuristic)
  // into the chrome so the inference quality is always visible.
  const ml = useQuery({
    queryKey: ['ml-health-topbar'],
    queryFn: async () => {
      const res = await fetch('/ml/v1/health');
      if (!res.ok) throw new Error(String(res.status));
      return (await res.json()) as { backend: string; status: string };
    },
    refetchInterval: 30_000,
    retry: 1,
    staleTime: 20_000,
  });
  const mlTier = ml.data?.backend;

  const liveBadge = (() => {
    switch (mode) {
      case 'sse':
        return (
          <span className="inline-flex items-center gap-2 rounded-full border border-emerald-800 bg-emerald-950/60 px-3 py-1 text-xs font-medium text-emerald-300">
            <span className="h-2 w-2 rounded-full bg-emerald-400 animate-pulse-dot" />
            Live · SSE
          </span>
        );
      case 'polling':
        return (
          <span className="inline-flex items-center gap-2 rounded-full border border-amber-800 bg-amber-950/60 px-3 py-1 text-xs font-medium text-amber-300">
            <span className="h-2 w-2 rounded-full bg-amber-400 animate-pulse-dot" />
            Polling 10 s
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-2 rounded-full border border-slate-700 bg-slate-900 px-3 py-1 text-xs text-slate-400">
            <span className="h-2 w-2 rounded-full bg-slate-500 animate-pulse-dot" />
            Connecting…
          </span>
        );
    }
  })();

  return (
    <header className="h-16 shrink-0 border-b border-slate-800 bg-slate-950/80 backdrop-blur flex items-center gap-6 px-6">
      <div className="min-w-0">
        <div className="text-sm font-semibold text-slate-50 truncate">
          {snapshot?.city?.name ?? 'UrbanTwin'} · Cross-service twin
        </div>
        <div className="text-[11px] text-slate-500">
          {error
            ? `Feed error: ${error}`
            : `Last snapshot ${fmtClock(lastUpdateMs)}${
                snapshot?.totals?.dataFreshness ? ` · freshness ${snapshot.totals.dataFreshness}` : ''
              }`}
        </div>
      </div>

      <div className="ml-auto flex items-center gap-5">
        <StatusBadge
          state={NET_STATE[snapshot?.totals.networkStatus ?? ''] ?? 'slate'}
          label={`Network ${snapshot?.totals.networkStatus ?? '—'}`}
        />
        <div className="hidden lg:flex items-center gap-5 text-sm">
          <Kpi label="Incidents" value={snapshot?.totals.activeIncidents} />
          <Kpi label="Maintenance" value={snapshot?.totals.openMaintenance} />
          <Kpi label="Bins overfull" value={snapshot?.totals.binsOverfull} />
          <Kpi label="Avg congestion" value={snapshot ? pct(snapshot.totals.avgCongestion, 1) : '—'} />
        </div>
        <MlChip tier={mlTier} loading={ml.isLoading} />
        {liveBadge}
        <button
          type="button"
          onClick={toggle}
          title={theme === 'light' ? 'Switch to black theme' : 'Switch to white theme'}
          aria-label="Toggle white/black theme"
          className="inline-flex h-9 w-9 items-center justify-center rounded-xl border border-slate-800 bg-slate-950/60 text-slate-400 transition-colors hover:border-slate-600 hover:text-slate-50"
        >
          {theme === 'light' ? '🌙' : '☀️'}
        </button>
      </div>
    </header>
  );
}

/** ML inference tier chip: which backend (tf / sklearn / heuristic) is serving. */
function MlChip({ tier, loading }: { tier: string | undefined; loading: boolean }) {
  const style = loading
    ? { label: 'ML …', cls: 'border-slate-300 bg-slate-50 text-slate-500 dark:border-slate-800 dark:bg-slate-950/60 dark:text-slate-500' }
    : tier
      ? {
          label: `ML ${tier}`,
          cls:
            tier === 'tensorflow' || tier === 'sklearn'
              ? 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300'
              : 'border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-300',
        }
      : { label: 'ML down', cls: 'border-slate-300 bg-slate-50 text-slate-500 dark:border-slate-800 dark:bg-slate-950/60 dark:text-slate-500' };
  return (
    <span className={`hidden md:inline-flex items-center rounded-full border px-3 py-1 text-xs font-medium ${style.cls}`}>
      {style.label}
    </span>
  );
}

function Kpi({ label, value }: { label: string; value: string | number | undefined | null }) {
  const v =
    value === undefined || value === null
      ? '—'
      : typeof value === 'number'
        ? fmtNum(value, 0)
        : value;
  return (
    <div className="leading-tight text-right">
      <div className="text-slate-50 font-semibold">{v}</div>
      <div className="text-[11px] text-slate-500">{label}</div>
    </div>
  );
}
