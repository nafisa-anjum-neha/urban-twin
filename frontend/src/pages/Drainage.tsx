import { useState } from 'react';
import { CircleMarker, Tooltip } from 'react-leaflet';
import { useQuery } from '@tanstack/react-query';
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip as RTooltip,
  XAxis,
  YAxis,
} from 'recharts';
import Panel from '../components/Panel';
import CityMap from '../components/CityMap';
import MapCard, { ColorDot } from '../components/MapCard';
import RiskGauge, { riskForCm } from '../components/RiskGauge';
import { LoadingState, ErrorState, EmptyState } from '../components/States';
import StatusBadge from '../components/StatusBadge';
import { listZones } from '../api/cities';
import {
  listDrainageSensors,
  listWaterLevelReadings,
  getWaterloggingPredictions,
} from '../api/drainage';
import { isoDaysAgo } from '../lib/format';
import { useTheme } from '../theme';
import type { DrainageSensor } from '../types';

const RISK_COLORS: Record<string, string> = {
  low: '#34d399',
  moderate: '#fbbf24',
  high: '#fb923c',
  severe: '#f87171',
};

export default function Drainage() {
  const [sensorId, setSensorId] = useState<string>('');
  const pal = useTheme().palette;

  const zonesQ = useQuery({ queryKey: ['zones'], queryFn: () => listZones() });

  const sensorsQ = useQuery({ queryKey: ['drainage-sensors'], queryFn: () => listDrainageSensors() });
  const predQ = useQuery({
    queryKey: ['waterlogging'],
    queryFn: getWaterloggingPredictions,
    refetchInterval: 30_000,
  });
  const readingsQ = useQuery({
    queryKey: ['water-readings', sensorId],
    queryFn: () =>
      listWaterLevelReadings({ sensorId: sensorId || (sensorsQ.data?.[0]?.id as string) || undefined, from: isoDaysAgo(1) }),
    enabled: sensorId !== '' || (sensorsQ.data?.length ?? 0) > 0,
  });

  const zoneNames = new Map((zonesQ.data ?? []).map((z) => [z.id, z.name] as const));
  const latestBySensor = (s: DrainageSensor) => s.latestReading ?? null;

  if (sensorsQ.isLoading) return <LoadingState label="Gauging water levels…" />;
  if (sensorsQ.isError)
    return <ErrorState message={(sensorsQ.error as Error)?.message} onRetry={() => sensorsQ.refetch()} />;

  const sensors = sensorsQ.data ?? [];
  const critical = sensors.filter((s) => {
    const r = latestBySensor(s);
    if (r?.riskLevel) return r.riskLevel === 'high' || r.riskLevel === 'severe';
    return false;
  });
  const selReadings = readingsQ.data ?? [];
  const chartData = selReadings.slice(-48).map((r) => ({
    t: new Date(r.recordedAt).toLocaleTimeString('en-IN', { hour12: false, hour: '2-digit', minute: '2-digit' }),
    cm: r.waterLevelCm,
    rain: r.rainfallMm ?? 0,
  }));

  return (
    <div className="space-y-5">
      <div className="grid gap-5 xl:grid-cols-3">
        <div className="xl:col-span-2">
          <MapCard
            title="Drainage sensor network"
            subtitle="Markers colored by current water-level risk"
            legend={
              <>
                {Object.entries(RISK_COLORS).map(([k, c]) => (
                  <ColorDot key={k} color={c} label={k} />
                ))}
              </>
            }
          >
            <CityMap height={400}>
              {sensors.map((s) => {
                const reading = latestBySensor(s);
                const risk = riskForCm(reading?.waterLevelCm ?? 0);
                return (
                  <CircleMarker
                    key={s.id}
                    center={s.location ? [s.location[1], s.location[0]] : [centerOf(sensors)[0], centerOf(sensors)[1]]}
                    radius={reading ? 5 + Math.min(8, reading.waterLevelCm / 25) : 4}
                    pathOptions={{
                      color: risk.color,
                      fillColor: risk.color,
                      fillOpacity: 0.8,
                      weight: 2,
                    }}
                  >
                    <Tooltip sticky>
                      <b>{s.name}</b>
                      <div>{reading ? `${Math.round(reading.waterLevelCm)} cm · ${reading.riskLevel ?? risk.risk}` : 'no data'}</div>
                      {reading?.flowRateLps != null && <div>flow {Math.round(reading.flowRateLps)} l/s</div>}
                    </Tooltip>
                  </CircleMarker>
                );
              })}
            </CityMap>
          </MapCard>
        </div>

        <Panel title="Waterlogging forecast" subtitle="ML prediction per zone with affected roads">
          <div className="max-h-[400px] space-y-2 overflow-y-auto pr-1">
            {(predQ.data ?? []).map((p) => (
              <div key={p.zoneId} className="rounded-xl border border-slate-800 bg-slate-950/40 px-4 py-3">
                <div className="flex items-center justify-between text-sm">
                  <span className="font-medium text-slate-200">{zoneNames.get(p.zoneId) ?? (p.zoneId.slice(0, 8) || 'zone')}</span>
                  <StatusBadge status={p.risk} label={p.risk} />
                </div>
                <div className="mt-1.5 flex items-baseline gap-3 text-[11px] text-slate-500">
                  <span>peak level <b className="text-slate-300">{Math.round(p.maxLevelCm)} cm</b></span>
                  <span>{p.affectedRoads.length} roads impacted</span>
                  <span>conf {Math.round(p.confidence * 100)}%</span>
                </div>
              </div>
            ))}
            {(predQ.data ?? []).length === 0 && !predQ.isLoading && (
              <EmptyState message="No waterlogging expected." hint="Predictions refresh every 30 s." />
            )}
          </div>
        </Panel>
      </div>

      <div className="grid gap-5 xl:grid-cols-3">
        <Panel title="Rising water" subtitle="Sensors currently above the high-risk threshold" className="xl:col-span-1">
          <div className="space-y-2">
            {critical.map((s) => {
              const r = latestBySensor(s);
              return (
                <RiskGauge
                  key={s.id}
                  valueCm={r?.waterLevelCm ?? 0}
                  label={s.name}
                  sub={`sensor ${s.status}`}
                />
              );
            })}
            {critical.length === 0 && <EmptyState message="All sensors within safe levels." />}
          </div>
        </Panel>

        <Panel title="Water level history" subtitle="Last 24 h for a selected sensor" className="xl:col-span-2">
          <select className="select mb-3" value={sensorId} onChange={(e) => setSensorId(e.target.value)}>
            <option value="">First sensor</option>
            {sensors.map((s) => (
              <option key={s.id} value={s.id}>{s.name}</option>
            ))}
          </select>
          {chartData.length === 0 ? (
            <EmptyState message="No readings yet for this sensor." />
          ) : (
            <ResponsiveContainer width="100%" height={240}>
              <LineChart data={chartData} margin={{ top: 8, right: 12, left: -14, bottom: 0 }}>
                <CartesianGrid stroke={pal.grid} vertical={false} />
                <XAxis dataKey="t" tick={{ fill: pal.tick, fontSize: 10 }} stroke={pal.axis} interval={9} />
                <YAxis tick={{ fill: pal.tick, fontSize: 10 }} stroke={pal.axis} />
                <RTooltip
                  contentStyle={{ background: pal.tipBg, border: `1px solid ${pal.tipBorder}`, borderRadius: 12, fontSize: 12 }}
                />
                <Line type="monotone" dataKey="cm" name="water (cm)" stroke="#38bdf8" strokeWidth={2} dot={false} />
                <Line type="monotone" dataKey="rain" name="rain (mm)" stroke="#a78bfa" strokeWidth={1.4} dot={false} yAxisId={0} />
              </LineChart>
            </ResponsiveContainer>
          )}
        </Panel>
      </div>
    </div>
  );
}

function centerOf(sensors: { location?: [number, number] | null }[]): [number, number] {
  const withLoc = sensors.filter((s) => s.location);
  if (withLoc.length === 0) return [90.4125, 23.8103]; // Dhaka fallback
  const lon = withLoc.reduce((s, x) => s + (x.location as [number, number])[0], 0) / withLoc.length;
  const lat = withLoc.reduce((s, x) => s + (x.location as [number, number])[1], 0) / withLoc.length;
  return [lat, lon];
}
