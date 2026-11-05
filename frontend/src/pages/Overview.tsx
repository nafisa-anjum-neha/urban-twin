import { useMemo } from 'react';
import { Polygon, CircleMarker, Tooltip } from 'react-leaflet';
import { useQuery } from '@tanstack/react-query';
import Panel from '../components/Panel';
import KpiCard from '../components/KpiCard';
import CityMap from '../components/CityMap';
import { LoadingState, ErrorState, EmptyState } from '../components/States';
import StatusBadge from '../components/StatusBadge';
import { useTwinLive } from '../hooks/useTwinLive';
import { listZones } from '../api/cities';
import type { Zone } from '../types';
import { parseGeometry, geometryToLatLng, riskColor, type LatLngTuple } from '../hooks/geo';
import { useMapData } from '../hooks/useMapData';
import { pct, timeAgo } from '../lib/format';
import type { GeoFeatureCollection } from '../types';

interface Zoned {
  zone: Zone;
  ring: LatLngTuple[];
  center: LatLngTuple;
  congestion: number;
}

export default function Overview() {
  const { snapshot, error: liveError } = useTwinLive();
  const zonesQ = useQuery({ queryKey: ['zones'], queryFn: () => listZones() });
  const { fc } = useMapData(['incidents']);
  const incidentsFc: GeoFeatureCollection | undefined = fc['incidents'];

  const congestionByZone = useMemo(() => {
    const m = new Map<string, number>();
    for (const z of snapshot?.zones ?? []) m.set(z.zoneId, z.congestion);
    return m;
  }, [snapshot]);

  const zoned = useMemo<Zoned[]>(
    () =>
      (zonesQ.data ?? []).flatMap((z) => {
        const ring = geometryToLatLng(parseGeometry(z.geojson));
        if (ring.length < 3) return [];
        const lat = ring.reduce((s, p) => s + p[0], 0) / ring.length;
        const lng = ring.reduce((s, p) => s + p[1], 0) / ring.length;
        return [
          {
            zone: z,
            ring,
            center: [lat, lng],
            congestion: congestionByZone.get(z.id) ?? 0,
          },
        ];
      }),
    [zonesQ.data, congestionByZone],
  );

  if (zonesQ.isLoading) return <LoadingState label="Assembling the city…" />;
  if (zonesQ.isError)
    return (
      <ErrorState message={(zonesQ.error as Error)?.message} onRetry={() => zonesQ.refetch()} />
    );

  const zones = zonesQ.data ?? [];
  if (zones.length === 0)
    return <EmptyState message="No zones seeded yet — start the backend to seed the city." />;

  const total = snapshot?.totals;

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-2 gap-4 xl:grid-cols-5">
        <KpiCard label="Active incidents" value={total?.activeIncidents ?? '—'} icon="🚨" accent="red" href="/emergency" />
        <KpiCard label="Open maintenance" value={total?.openMaintenance ?? '—'} icon="🛠️" accent="amber" href="/roads" />
        <KpiCard label="Bins overfull" value={total?.binsOverfull ?? '—'} icon="🗑️" accent="violet" href="/waste" />
        <KpiCard label="Avg congestion" value={total ? pct(total.avgCongestion, 1) : '—'} icon="🚦" accent="amber" href="/traffic" />
        <KpiCard
          label="Zones tracked"
          value={zoned.length}
          icon="🏙️"
          accent="sky"
          sub={snapshot ? `snapshot ${timeAgo(snapshot.updatedAt)}` : liveError ?? ''}
        />
      </div>

      <div className="grid gap-5 xl:grid-cols-3">
        <div className="xl:col-span-2">
          <Panel
            title="City map"
            subtitle="Zones colored by live congestion · red dots are active incidents"
            bodyClassName="!px-5"
          >
            <CityMap height={470}>
              {zoned.map(({ zone, ring, congestion }) => (
                <Polygon
                  key={zone.id}
                  pathOptions={{
                    color: '#334155',
                    weight: 1,
                    fillColor: riskColor(congestion),
                    fillOpacity: 0.28,
                  }}
                  positions={[ring]}
                >
                  <Tooltip sticky>
                    <b>{zone.name}</b> · congestion {pct(congestion, 1)}
                  </Tooltip>
                </Polygon>
              ))}
              {zoned.map(({ zone, center, congestion }) => (
                <CircleMarker
                  key={`c-${zone.id}`}
                  center={center}
                  radius={6 + congestion * 10}
                  pathOptions={{ color: riskColor(congestion), fillOpacity: 0.9, weight: 2 }}
                />
              ))}
              {(incidentsFc?.features ?? [])
                .filter((f) => (f.properties as { status?: string }).status !== 'resolved')
                .map((f, i) => {
                  const pts = geometryToLatLng(parseGeometry(f.geometry));
                  if (!pts[0]) return null;
                  const props = f.properties as { name?: string; status?: string };
                  return (
                    <CircleMarker
                      key={`inc-${f.properties.id ?? i}`}
                      center={pts[0]}
                      radius={7}
                      pathOptions={{ color: '#f87171', fillColor: '#ef4444', fillOpacity: 0.85, weight: 2 }}
                    >
                      <Tooltip sticky>
                        <b>{props.name ?? 'Incident'}</b>
                        <div>status: {props.status}</div>
                      </Tooltip>
                    </CircleMarker>
                  );
                })}
            </CityMap>
          </Panel>
        </div>

        <Panel title="Zone health" subtitle="Congestion · flood risk · waste backlog">
          <div className="space-y-3">
            {(snapshot?.zones ?? []).map((z) => (
              <div key={z.zoneId} className="rounded-xl border border-slate-800 bg-slate-950/40 px-4 py-3">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-sm font-medium text-slate-200">{z.name}</span>
                  <StatusBadge status={z.powerState} label={`power ${z.powerState}`} />
                </div>
                <Bar label="Congestion" value={z.congestion} />
                <Bar label="Flood risk" value={z.floodRisk} />
                <div className="mt-1 flex items-center justify-between text-[11px] text-slate-500">
                  <span>asset health {Math.round(z.assetHealth ?? 0)}%</span>
                  <span>{z.binsOverfull} bins overfull</span>
                </div>
              </div>
            ))}
            {!snapshot && <LoadingState label="Waiting for twin snapshot…" />}
          </div>
        </Panel>
      </div>
    </div>
  );
}

function Bar({ label, value }: { label: string; value: number }) {
  return (
    <div className="mt-2 flex items-center gap-2">
      <span className="w-20 shrink-0 text-[10px] uppercase tracking-wide text-slate-500">{label}</span>
      <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-slate-800">
        <div
          className="h-full rounded-full transition-all duration-500"
          style={{ width: `${Math.min(100, Math.max(2, value * 100))}%`, backgroundColor: riskColor(value) }}
        />
      </div>
      <span className="w-10 shrink-0 text-right text-[10px] text-slate-400">{pct(value, 1)}</span>
    </div>
  );
}
