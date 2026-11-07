import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip as RTooltip,
  XAxis,
  YAxis,
} from 'recharts';
import Panel from '../components/Panel';
import KpiCard from '../components/KpiCard';
import { LoadingState, ErrorState, EmptyState } from '../components/States';
import StatusBadge from '../components/StatusBadge';
import { getEnergySummary, listGrids, listGridReadings } from '../api/energy';
import { fmtNum, isoDaysAgo } from '../lib/format';
import { useTheme } from '../theme';

export default function Energy() {
  const pal = useTheme().palette;
  const [gridId, setGridId] = useState('');
  const summaryQ = useQuery({ queryKey: ['energy-summary'], queryFn: getEnergySummary, refetchInterval: 30_000 });
  const gridsQ = useQuery({ queryKey: ['grids'], queryFn: () => listGrids() });
  const readingsQ = useQuery({
    queryKey: ['grid-readings', gridId],
    queryFn: () =>
      listGridReadings({ gridId: gridId || (gridsQ.data?.[0]?.id as string), from: isoDaysAgo(1) }),
    enabled: gridId !== '' || (gridsQ.data?.length ?? 0) > 0,
  });

  if (summaryQ.isLoading || gridsQ.isLoading) return <LoadingState label="Metering the grid…" />;
  if (summaryQ.isError)
    return <ErrorState message={(summaryQ.error as Error)?.message} onRetry={() => summaryQ.refetch()} />;

  const summary = summaryQ.data ?? [];
  const totalKw = summary.reduce((s, z) => s + z.currentKw, 0);
  const peak = Math.max(1, ...summary.map((z) => z.peakKw24h));
  const outages = summary.reduce((s, z) => s + z.outages24h, 0);
  const grids = gridsQ.data ?? [];
  const readings = (readingsQ.data ?? []).slice(-48).map((r) => ({
    t: new Date(r.recordedAt).toLocaleTimeString('en-IN', { hour12: false, hour: '2-digit', minute: '2-digit' }),
    kw: r.consumptionKw,
  }));

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-2 gap-4 xl:grid-cols-4">
        <KpiCard label="City load" value={fmtNum(totalKw, 0)} sub="kW across all zones" icon="⚡" accent="amber" />
        <KpiCard label="24 h peak zone load" value={fmtNum(peak, 0)} sub="kW" icon="📈" accent="sky" />
        <KpiCard label="Outages (24 h)" value={outages} sub="report events" icon="🔌" accent="red" />
        <KpiCard label="Grids online" value={grids.filter((g) => !g.latestReading?.outageReported).length} sub={`of ${grids.length}`} icon="🏗️" accent="emerald" />
      </div>

      <div className="grid gap-5 xl:grid-cols-2">
        <Panel title="Consumption by zone" subtitle="Current draw vs 24 h peak (kW)">
          {summary.length === 0 ? (
            <EmptyState message="No energy data yet." />
          ) : (
            <ResponsiveContainer width="100%" height={280}>
              <BarChart
                data={summary.map((z) => ({ name: z.name, current: z.currentKw, peak: z.peakKw24h }))}
                margin={{ top: 8, right: 8, left: -12, bottom: 0 }}
              >
                <CartesianGrid stroke={pal.grid} vertical={false} />
                <XAxis dataKey="name" tick={{ fill: pal.tick, fontSize: 11 }} stroke={pal.axis} />
                <YAxis tick={{ fill: pal.tick, fontSize: 11 }} stroke={pal.axis} />
                <RTooltip contentStyle={{ background: pal.tipBg, border: `1px solid ${pal.tipBorder}`, borderRadius: 12, fontSize: 12 }} />
                <Legend wrapperStyle={{ fontSize: 11 }} />
                <Bar dataKey="current" name="Now (kW)" fill="#facc15" radius={[6, 6, 0, 0]} />
                <Bar dataKey="peak" name="24 h peak" fill="#f472b6" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </Panel>

        <Panel title="Zone load table" subtitle="Trend vs previous hour">
          <table className="table">
            <thead>
              <tr><th>Zone</th><th>Current</th><th>Peak 24h</th><th>Outages</th><th>Trend</th></tr>
            </thead>
            <tbody>
              {summary.map((z) => (
                <tr key={z.zoneId}>
                  <td className="font-medium text-slate-200">{z.name}</td>
                  <td>{fmtNum(z.currentKw)} kW</td>
                  <td>{fmtNum(z.peakKw24h)} kW</td>
                  <td>{z.outages24h}</td>
                  <td>
                    <StatusBadge
                      state={z.trend === 'up' ? 'amber' : z.trend === 'down' ? 'emerald' : 'slate'}
                      label={z.trend === 'up' ? '▲ rising' : z.trend === 'down' ? '▼ easing' : '— stable'}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Panel>
      </div>

      <Panel title="Grid consumption history" subtitle="Last 24 h for a selected grid">
        <select className="select mb-3 w-64" value={gridId} onChange={(e) => setGridId(e.target.value)}>
          <option value="">{grids[0]?.name ?? 'First grid'}</option>
          {grids.map((g) => (
            <option key={g.id} value={g.id}>{g.name}</option>
          ))}
        </select>
        {readings.length === 0 ? (
          <EmptyState message="No meter readings for this grid yet." />
        ) : (
          <ResponsiveContainer width="100%" height={260}>
            <LineChart data={readings} margin={{ top: 8, right: 12, left: -6, bottom: 0 }}>
              <CartesianGrid stroke={pal.grid} vertical={false} />
              <XAxis dataKey="t" tick={{ fill: pal.tick, fontSize: 10 }} stroke={pal.axis} interval={7} />
              <YAxis tick={{ fill: pal.tick, fontSize: 10 }} stroke={pal.axis} />
              <RTooltip contentStyle={{ background: pal.tipBg, border: `1px solid ${pal.tipBorder}`, borderRadius: 12, fontSize: 12 }} />
              <Line type="monotone" dataKey="kw" name="kW" stroke="#facc15" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        )}
      </Panel>
    </div>
  );
}
