import { useMemo, useState } from 'react';
import { Polyline, CircleMarker, Tooltip } from 'react-leaflet';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Panel from '../components/Panel';
import CityMap from '../components/CityMap';
import MapCard, { ColorDot } from '../components/MapCard';
import { LoadingState, ErrorState, EmptyState } from '../components/States';
import StatusBadge from '../components/StatusBadge';
import { listWasteBins, collectBin, optimizeRoute, listWasteRoutes } from '../api/waste';
import { listZones } from '../api/cities';
import { timeAgo } from '../lib/format';
import type { WasteRoute } from '../types';

function fillColor(pct: number): string {
  if (pct >= 85) return '#f87171';
  if (pct >= 60) return '#fbbf24';
  return '#34d399';
}

export default function Waste() {
  const qc = useQueryClient();
  const [zoneId, setZoneId] = useState('');
  const [route, setRoute] = useState<WasteRoute | null>(null);

  const binsQ = useQuery({ queryKey: ['bins'], queryFn: () => listWasteBins({}), refetchInterval: 30_000 });
  const zonesQ = useQuery({ queryKey: ['zones'], queryFn: () => listZones() });
  const routesQ = useQuery({ queryKey: ['waste-routes'], queryFn: () => listWasteRoutes({}) });

  const optimizeM = useMutation({
    mutationFn: (z: string) => optimizeRoute({ zoneId: z }),
    onSuccess: (r) => {
      setRoute(r);
      void qc.invalidateQueries({ queryKey: ['waste-routes'] });
    },
  });
  const collectM = useMutation({
    mutationFn: (id: string) => collectBin(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['bins'] });
    },
  });

  const overfull = useMemo(
    () => (binsQ.data ?? []).filter((b) => b.fillLevelPct >= 85).length,
    [binsQ.data],
  );

  if (binsQ.isLoading || zonesQ.isLoading) return <LoadingState label="Emptying bins…" />;
  if (binsQ.isError)
    return <ErrorState message={(binsQ.error as Error)?.message} onRetry={() => binsQ.refetch()} />;

  const bins = (binsQ.data ?? []).filter((b) => !zoneId || b.zoneId === zoneId);
  const stopsGeo = (route?.stops ?? [])
    .sort((a, b) => a.order - b.order)
    .map((s) => [s.lat, s.lon] as [number, number]);

  return (
    <div className="space-y-5">
      <div className="grid gap-5 xl:grid-cols-3">
        <div className="xl:col-span-2">
          <MapCard
            title="Bin fill levels"
            subtitle="Route optimization targets bins filled ≥ 60 %"
            legend={
              <>
                <ColorDot color="#34d399" label="< 60 % (skip)" />
                <ColorDot color="#fbbf24" label="60–84 %" />
                <ColorDot color="#f87171" label="≥ 85 % overfull" />
              </>
            }
          >
            <CityMap height={430}>
              {bins.map((b) =>
                b.location ? (
                  <CircleMarker
                    key={b.id}
                    center={[b.location[1], b.location[0]]}
                    radius={5 + (b.fillLevelPct / 100) * 6}
                    pathOptions={{ color: fillColor(b.fillLevelPct), fillOpacity: 0.85, weight: 2 }}
                  >
                    <Tooltip sticky>
                      <b>{b.name}</b>
                      <div>{Math.round(b.fillLevelPct)} % full</div>
                      <div>collected {timeAgo(b.lastCollectedAt)}</div>
                    </Tooltip>
                  </CircleMarker>
                ) : null,
              )}
              {stopsGeo.length > 1 && (
                <Polyline
                  pathOptions={{ color: '#38bdf8', weight: 3, dashArray: '6 8', opacity: 0.9 }}
                  positions={stopsGeo}
                />
              )}
            </CityMap>
          </MapCard>
        </div>

        <Panel title="Route optimizer" subtitle="Nearest-neighbour ordering with 2-opt improvement">
          <select className="select mb-2 w-full" value={zoneId} onChange={(e) => setZoneId(e.target.value)}>
            <option value="">All zones</option>
            {(zonesQ.data ?? []).map((z) => (
              <option key={z.id} value={z.id}>{z.name}</option>
            ))}
          </select>
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="button"
              onClick={() => optimizeM.mutate(zoneId || (zonesQ.data?.[0]?.id as string))}
              disabled={optimizeM.isPending}
            >
              {optimizeM.isPending ? 'Solving…' : 'Optimize collection route'}
            </button>
          </div>
          {optimizeM.isError && <p className="mt-2 text-xs text-red-400">{(optimizeM.error as Error).message}</p>}

          {route && (
            <div className="mt-4 rounded-xl border border-sky-900/60 bg-sky-950/30 px-4 py-3">
              <div className="text-sm font-semibold text-sky-200">{route.name}</div>
              <div className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-slate-400">
                <span>{route.stops.length} stops</span>
                <span>{route.totalDistanceKm.toFixed(1)} km</span>
                <span>~{Math.round(route.estimatedDurationMin)} min</span>
                <StatusBadge status={route.status} />
              </div>
              <ol className="mt-2 max-h-40 space-y-1 overflow-y-auto text-[11px] text-slate-500">
                {route.stops
                  .slice()
                  .sort((a, b) => a.order - b.order)
                  .map((s, i) => (
                    <li key={s.binId}>
                      {i + 1}. {s.name} — {Math.round(s.fillLevel)} % full
                    </li>
                  ))}
              </ol>
            </div>
          )}

          <div className="mt-4 border-t border-slate-800 pt-3 text-[11px] text-slate-500">
            {overfull} bins overfull across the city · {bins.length} bins in the current view
          </div>
        </Panel>
      </div>

      <Panel title="Overfull bin queue" subtitle="Trigger a collection run to reset levels">
        {bins.filter((b) => b.fillLevelPct >= 60).length === 0 ? (
          <EmptyState message="Nothing needs collecting in view." />
        ) : (
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {bins
              .filter((b) => b.fillLevelPct >= 60)
              .sort((a, b) => b.fillLevelPct - a.fillLevelPct)
              .slice(0, 12)
              .map((b) => (
                <div key={b.id} className="rounded-xl border border-slate-800 bg-slate-950/40 px-4 py-3">
                  <div className="flex items-center justify-between gap-2">
                    <span className="truncate text-sm font-medium text-slate-200">{b.name}</span>
                    <span className="text-sm font-semibold" style={{ color: fillColor(b.fillLevelPct) }}>
                      {Math.round(b.fillLevelPct)}%
                    </span>
                  </div>
                  <button
                    type="button"
                    className="ticket mt-2"
                    onClick={() => collectM.mutate(b.id)}
                    disabled={collectM.isPending}
                  >
                    Mark collected
                  </button>
                </div>
              ))}
          </div>
        )}
      </Panel>

      {(routesQ.data ?? []).length > 0 && (
        <Panel title="Planned routes" subtitle="Persisted optimization runs">
          <table className="table">
            <thead>
              <tr><th>Route</th><th>Stops</th><th>Distance</th><th>Duration</th><th>Status</th></tr>
            </thead>
            <tbody>
              {(routesQ.data ?? []).slice(0, 8).map((r) => (
                <tr key={r.id}>
                  <td className="font-medium text-slate-200">{r.name}</td>
                  <td>{r.stops?.length ?? 0}</td>
                  <td>{r.totalDistanceKm?.toFixed?.(1) ?? r.totalDistanceKm} km</td>
                  <td>{Math.round(r.estimatedDurationMin)} min</td>
                  <td><StatusBadge status={r.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </Panel>
      )}
    </div>
  );
}
