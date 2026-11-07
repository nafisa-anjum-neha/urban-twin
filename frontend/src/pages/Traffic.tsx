import { useMemo, useState } from 'react';
import { Polygon, CircleMarker, Tooltip } from 'react-leaflet';
import { useQuery } from '@tanstack/react-query';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip as RTooltip,
  XAxis,
  YAxis,
} from 'recharts';
import Panel from '../components/Panel';
import CityMap from '../components/CityMap';
import MapCard, { ColorDot } from '../components/MapCard';
import { LoadingState, ErrorState, EmptyState } from '../components/States';
import StatusBadge from '../components/StatusBadge';
import {
  parseGeometry,
  geometryToLatLng,
  riskColor,
  type LatLngTuple,
} from '../hooks/geo';
import { listZones } from '../api/cities';
import {
  getCongestionFirehose,
  getCongestionPrediction,
  listTrafficSensors,
} from '../api/traffic';
import { fmtNum, pct } from '../lib/format';
import { useTheme } from '../theme';

export default function Traffic() {
  const [mode, setMode] = useState<'now' | 'predicted'>('now');
  const pal = useTheme().palette;

  const firehoseQ = useQuery({
    queryKey: ['firehose'],
    queryFn: getCongestionFirehose,
    refetchInterval: 15_000,
  });
  const predictionQ = useQuery({
    queryKey: ['prediction'],
    queryFn: () => getCongestionPrediction({ horizonMinutes: 30 }),
    staleTime: 60_000,
  });
  const zonesQ = useQuery({ queryKey: ['zones'], queryFn: () => listZones() });
  const sensorsQ = useQuery({ queryKey: ['traffic-sensors'], queryFn: () => listTrafficSensors({}) });

  const congestions = useMemo(() => {
    const m = new Map<string, number>();
    if (mode === 'predicted') {
      for (const p of predictionQ.data ?? []) m.set(p.zoneId, p.predictedCongestion);
    } else {
      for (const z of firehoseQ.data ?? []) m.set(z.zoneId, z.congestionLevel);
    }
    return m;
  }, [mode, firehoseQ.data, predictionQ.data]);

  const zoned = useMemo(() => {
    return (zonesQ.data ?? []).flatMap((z) => {
      const ring = geometryToLatLng(parseGeometry(z.geojson));
      if (ring.length < 3) return [];
      return [{ zone: z, ring }];
    });
  }, [zonesQ.data]);

  const chartData = useMemo(
    () =>
      (firehoseQ.data ?? []).map((z) => ({
        name: z.name,
        now: z.congestionLevel,
        predicted: (predictionQ.data ?? []).find((p) => p.zoneId === z.zoneId)?.predictedCongestion ?? 0,
        speed: z.avgSpeedKph,
      })),
    [firehoseQ.data, predictionQ.data],
  );

  if (firehoseQ.isLoading || zonesQ.isLoading) return <LoadingState label="Reading the roads…" />;
  if (firehoseQ.isError)
    return (
      <ErrorState message={(firehoseQ.error as Error)?.message} onRetry={() => firehoseQ.refetch()} />
    );

  const hotspots = [...(chartData ?? [])].sort((a, b2) => b2.now - a.now).slice(0, 3);

  return (
    <div className="space-y-5">
      <Panel
        title="Congestion by zone"
        subtitle="Live average edge congestion (0–1) from the latest traffic sensor readings"
        right={
          <div className="flex overflow-hidden rounded-lg border border-slate-700 text-xs">
            {(['now', 'predicted'] as const).map((m) => (
              <button
                key={m}
                type="button"
                onClick={() => setMode(m)}
                className={`px-3 py-1.5 ${mode === m ? 'bg-slate-700 text-slate-50' : 'text-slate-400 hover:text-slate-200'}`}
              >
                {m === 'now' ? 'Now' : '+30 min (ML)'}
              </button>
            ))}
          </div>
        }
      >
        {chartData.length === 0 ? (
          <EmptyState message="No congestion data yet." />
        ) : (
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={chartData} margin={{ top: 8, right: 8, left: -18, bottom: 0 }}>
              <CartesianGrid stroke={pal.grid} vertical={false} />
              <XAxis dataKey="name" tick={{ fill: pal.tick, fontSize: 11 }} stroke={pal.axis} />
              <YAxis tick={{ fill: pal.tick, fontSize: 11 }} domain={[0, 1]} stroke={pal.axis} />
              <RTooltip
                contentStyle={{
                  background: pal.tipBg,
                  border: `1px solid ${pal.tipBorder}`,
                  borderRadius: 12,
                  fontSize: 12,
                }}
                formatter={(v: number) => [v != null ? v.toFixed(2) : '—', '']}
              />
              <Legend wrapperStyle={{ fontSize: 11 }} />
              <Bar dataKey="now" name="Now" fill="#38bdf8" radius={[6, 6, 0, 0]} />
              <Bar dataKey="predicted" name="+30 min" fill="#fb923c" radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </Panel>

      <div className="grid gap-5 xl:grid-cols-3">
        <div className="xl:col-span-2">
          <MapCard
            title="Congestion map"
            subtitle={mode === 'now' ? 'Current levels' : 'ML 30-minute forecast'}
            legend={
              <>
                <ColorDot color="#34d399" label="low" />
                <ColorDot color="#fbbf24" label="moderate" />
                <ColorDot color="#f87171" label="high" />
              </>
            }
          >
            <CityMap height={400}>
              {zoned.map(({ zone, ring }) => {
                const v = congestions.get(zone.id) ?? 0;
                return (
                  <Polygon
                    key={zone.id}
                    pathOptions={{ color: '#334155', weight: 1, fillColor: riskColor(v), fillOpacity: 0.3 }}
                    positions={[ring]}
                  >
                    <Tooltip sticky>
                      <b>{zone.name}</b> · {pct(v, 1)} congestion
                    </Tooltip>
                  </Polygon>
                );
              })}
              {(sensorsQ.data ?? []).map((s) => {
                if (!s.location) return null;
                const speed = s.latestReading?.avgSpeedKph;
                return (
                  <CircleMarker
                    key={s.id}
                    center={[s.location[1], s.location[0]]}
                    radius={4}
                    pathOptions={{
                      color: s.status === 'active' ? '#38bdf8' : '#64748b',
                      fillOpacity: 0.8,
                      weight: 1.5,
                    }}
                  >
                    <Tooltip sticky>
                      <b>{s.name}</b>
                      <div>{speed != null ? `${fmtNum(speed, 0)} km/h avg` : 'no reading yet'}</div>
                    </Tooltip>
                  </CircleMarker>
                );
              })}
              {zoned.map(({ zone, ring }) => {
                const v = congestions.get(zone.id) ?? 0;
                const ringCent = ringCenter(ring);
                if (!ringCent) return null;
                return (
                  <CircleMarker
                    key={`heat-${zone.id}`}
                    center={ringCent}
                    radius={8 + v * 14}
                    pathOptions={{ color: riskColor(v), fillColor: riskColor(v), fillOpacity: 0.35, weight: 0 }}
                  />
                );
              })}
            </CityMap>
          </MapCard>
        </div>

        <Panel title="Top hotspots" subtitle="Highest congestion right now">
          <div className="space-y-3">
            {hotspots.map((h) => (
              <div key={h.name} className="rounded-xl border border-slate-800 bg-slate-950/40 px-4 py-3">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-sm font-medium text-slate-200">{h.name}</span>
                  <StatusBadge status={h.now >= 0.66 ? 'critical' : h.now >= 0.33 ? 'warning' : 'normal'} label={h.now >= 0.66 ? 'gridlock risk' : h.now >= 0.33 ? 'slow' : 'free flow'} />
                </div>
                <div className="mt-1 text-[11px] text-slate-500">
                  {fmtNum(h.speed, 0)} km/h average · forecast {pct(h.predicted, 0)} in 30 min
                </div>
              </div>
            ))}
            {hotspots.length === 0 && <EmptyState message="No hotspots." />}
          </div>
        </Panel>
      </div>
    </div>
  );
}

function ringCenter(ring: LatLngTuple[]): LatLngTuple | null {
  if (ring.length < 3) return null;
  const lat = ring.reduce((s, p) => s + p[0], 0) / ring.length;
  const lng = ring.reduce((s, p) => s + p[1], 0) / ring.length;
  return [lat, lng];
}
