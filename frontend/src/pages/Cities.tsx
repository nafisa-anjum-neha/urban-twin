import { useQuery } from '@tanstack/react-query';
import { getCityCompare } from '../api/twin';
import { useCity } from '../api/city';
import { errMessage } from '../api/client';
import Panel from '../components/Panel';
import { LoadingState, ErrorState } from '../components/States';
import { pct } from '../lib/format';

/**
 * Cross-city comparison (`GET /twin/compare`): one KPI row per city with a
 * congestion bar. Clicking a row switches the whole app to that city.
 */
export default function Cities() {
  const { switchById, cityId } = useCity();
  const q = useQuery({
    queryKey: ['cities-compare'],
    queryFn: getCityCompare,
    refetchInterval: 5_000,
  });

  if (q.isLoading || !q.data) return <LoadingState label="Loading cities…" />;
  if (q.isError) {
    return <ErrorState message={`Cities unavailable — ${errMessage(q.error)}`} />;
  }

  const maxCong = Math.max(...q.data.map((r) => r.avgCongestion), 0.01);
  const sorted = [...q.data].sort((a, b) => a.avgCongestion - b.avgCongestion);
  const best = sorted[0];
  const busiest = sorted[sorted.length - 1];

  return (
    <div className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <Panel title="Cleanest network" subtitle="lowest average congestion right now">
          <div className="flex items-center gap-3">
            <span className="text-4xl">{best?.flag ?? '🏙️'}</span>
            <div>
              <div className="text-lg font-semibold text-slate-50">
                {best?.name}
              </div>
              <div className="text-xs text-slate-500">
                {best?.country} · congestion {pct(best?.avgCongestion ?? 0, 1)}
              </div>
            </div>
          </div>
        </Panel>
        <Panel title="Busiest network" subtitle="highest average congestion right now">
          <div className="flex items-center gap-3">
            <span className="text-4xl">{busiest?.flag ?? '🏙️'}</span>
            <div>
              <div className="text-lg font-semibold text-slate-50">
                {busiest?.name}
              </div>
              <div className="text-xs text-slate-500">
                {busiest?.country} · congestion {pct(busiest?.avgCongestion ?? 0, 1)}
              </div>
            </div>
          </div>
        </Panel>
      </div>

      <Panel
        title="Cities in the twin"
        subtitle="click a row to switch · refreshes every 5 s"
      >
        <div className="space-y-2">
          {q.data.map((r) => (
            <button
              key={r.cityId}
              type="button"
              onClick={() => switchById(r.cityId, r.slug)}
              className="w-full rounded-xl border border-slate-800 bg-slate-900/50 px-4 py-3 text-left transition-colors hover:border-slate-600"
            >
              <div className="flex items-center gap-3">
                <span className="text-2xl">{r.flag ?? '🏙️'}</span>
                <div className="min-w-0 flex-1">
                  <div className="flex items-baseline gap-2">
                    <span className="font-semibold text-slate-50">{r.name}</span>
                    <span className="text-xs text-slate-500">
                      {r.country} · {r.zones} zones
                    </span>
                    {r.cityId === cityId && (
                      <span className="rounded-full border border-emerald-700 bg-emerald-950/60 px-2 py-0.5 text-[10px] font-medium text-emerald-300">
                        current
                      </span>
                    )}
                  </div>
                  <div className="mt-1.5 flex items-center gap-3">
                    <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-slate-800">
                      <div
                        className={[
                          'h-full rounded-full',
                          r.avgCongestion > 0.7 ? 'bg-red-500' : r.avgCongestion > 0.5 ? 'bg-amber-400' : 'bg-emerald-400',
                        ].join(' ')}
                        style={{ width: `${(r.avgCongestion / maxCong) * 100}%` }}
                      />
                    </div>
                    <span className="w-28 text-right text-xs text-slate-400">
                      congestion {pct(r.avgCongestion, 1)}
                    </span>
                  </div>
                  <div className="mt-1.5 flex gap-4 text-[11px] text-slate-500">
                    <span>🚨 {r.activeIncidents} incidents</span>
                    <span>🔧 {r.openMaintenance} open maintenance</span>
                    <span>🗑️ {r.binsOverfull} bins overfull</span>
                  </div>
                </div>
              </div>
            </button>
          ))}
        </div>
      </Panel>
    </div>
  );
}
