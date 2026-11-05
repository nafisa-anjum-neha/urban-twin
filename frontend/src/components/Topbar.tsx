import { useTwinLive } from '../hooks/useTwinLive';
import { fmtClock, fmtNum, pct } from '../lib/format';
import StatusBadge from './StatusBadge';

const NET_STATE: Record<string, 'emerald' | 'amber' | 'red'> = {
  ok: 'emerald',
  degraded: 'amber',
  critical: 'red',
};

export default function Topbar() {
  const { snapshot, mode, lastUpdateMs, error } = useTwinLive();

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
        <div className="text-sm font-semibold text-white truncate">
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
        {liveBadge}
      </div>
    </header>
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
      <div className="text-white font-semibold">{v}</div>
      <div className="text-[11px] text-slate-500">{label}</div>
    </div>
  );
}
