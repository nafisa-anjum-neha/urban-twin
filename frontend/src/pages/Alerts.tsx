import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import Panel from '../components/Panel';
import { LoadingState, ErrorState } from '../components/States';
import { ColorDot } from '../components/MapCard';
import StatusBadge from '../components/StatusBadge';
import { getCongestionFirehose } from '../api/traffic';
import { getWaterloggingPredictions } from '../api/drainage';
import { getEnergySummary } from '../api/energy';
import { listWasteBins } from '../api/waste';
import { listRoads } from '../api/roads';
import { listZones } from '../api/cities';
import { downloadCsv } from '../lib/csv';
import { timeAgo } from '../lib/format';

/**
 * Alert Center — threshold engine over every subsystem's live feed.
 * Data comes straight from the REST endpoints already used by the other
 * pages; this page purely derives when the city needs human attention.
 */

type Severity = 'critical' | 'high' | 'medium' | 'watch';

interface AlertItem {
  id: string;
  severity: Severity;
  system: 'traffic' | 'drainage' | 'energy' | 'waste' | 'roads';
  title: string;
  detail: string;
  zone?: string;
  icon: string;
  stamp?: string;
}

const SEV_RANK: Record<Severity, number> = { critical: 0, high: 1, medium: 2, watch: 3 };
const SEV_STYLE: Record<Severity, 'red' | 'amber' | 'emerald' | 'slate'> = {
  critical: 'red',
  high: 'red',
  medium: 'amber',
  watch: 'slate',
};
const SEV_TINT: Record<Severity, string> = {
  critical: 'border-l-red-500',
  high: 'border-l-red-400',
  medium: 'border-l-amber-400',
  watch: 'border-l-slate-400',
};

// thresholds (documented in the tooltip of the severity legend)
const CONGESTION_CRITICAL = 0.8;
const CONGESTION_HIGH = 0.65;
const CONGESTION_WATCH = 0.5;
const OVERFULL = 85;
const ROADS_POOR = 45;

export default function Alerts() {
  const zonesQ = useQuery({ queryKey: ['zones'], queryFn: () => listZones() });
  const firehoseQ = useQuery({
    queryKey: ['firehose'],
    queryFn: getCongestionFirehose,
    refetchInterval: 15_000,
  });
  const waterQ = useQuery({
    queryKey: ['waterlogging'],
    queryFn: getWaterloggingPredictions,
    refetchInterval: 30_000,
  });
  const energyQ = useQuery({
    queryKey: ['energy-summary'],
    queryFn: getEnergySummary,
    refetchInterval: 30_000,
  });
  const binsQ = useQuery({
    queryKey: ['waste-bins'],
    queryFn: () => listWasteBins({}),
    refetchInterval: 60_000,
  });
  const roadsQ = useQuery({ queryKey: ['roads'], queryFn: () => listRoads({}) });

  const zoneName = useMemo(() => {
    const m = new Map<string, string>();
    for (const z of zonesQ.data ?? []) m.set(z.id, z.name);
    return m;
  }, [zonesQ.data]);

  const alerts = useMemo<AlertItem[]>(() => {
    const out: AlertItem[] = [];
    const push = (a: AlertItem) => out.push(a);

    // traffic
    for (const z of firehoseQ.data ?? []) {
      const c = z.congestionLevel ?? 0;
      if (c >= CONGESTION_CRITICAL)
        push({
          id: `traffic-${z.zoneId}`,
          severity: 'critical',
          system: 'traffic',
          icon: '🚦',
          zone: z.name,
          title: `Gridlock building in ${z.name}`,
          detail: `Zone congestion ${(c * 100).toFixed(0)}% — consider rerouting or adjusting signal timings.`,
          stamp: z.updatedAt,
        });
      else if (c >= CONGESTION_HIGH)
        push({
          id: `traffic-${z.zoneId}`,
          severity: 'high',
          system: 'traffic',
          icon: '🚦',
          zone: z.name,
          title: `Moderate jam in ${z.name}`,
          detail: `Congestion ${(c * 100).toFixed(0)}% and rising within the key corridors.`,
          stamp: z.updatedAt,
        });
      else if (c >= CONGESTION_WATCH)
        push({
          id: `traffic-${z.zoneId}`,
          severity: 'watch',
          system: 'traffic',
          icon: '🚦',
          zone: z.name,
          title: `Traffic accumulating in ${z.name}`,
          detail: `Congestion ${(c * 100).toFixed(0)}% crossed the watch threshold.`,
          stamp: z.updatedAt,
        });
    }

    // drainage — ML/ML-fallback waterlogging predictions
    for (const p of waterQ.data ?? []) {
      if (p.risk === 'high' || p.risk === 'severe')
        push({
          id: `water-${p.zoneId}`,
          severity: p.risk === 'severe' ? 'critical' : 'high',
          system: 'drainage',
          icon: '🌧️',
          zone: zoneName.get(p.zoneId),
          title: `Waterlogging risk: ${p.risk} in ${zoneName.get(p.zoneId) ?? 'zone'}`,
          detail: `Projected peak level ${p.maxLevelCm.toFixed(0)} cm within the horizon (confidence ${(p.confidence * 100).toFixed(0)}%). ${p.affectedRoads?.length ?? 0} road(s) impacted.`,
        });
    }

    // energy
    for (const e of energyQ.data ?? []) {
      const load = e.peakKw24h > 0 ? e.currentKw / e.peakKw24h : 0;
      if (e.outages24h > 0)
        push({
          id: `energy-outage-${e.zoneId}`,
          severity: e.outages24h > 2 ? 'critical' : 'high',
          system: 'energy',
          icon: '⚡',
          zone: e.name,
          title: `Grid outages in ${e.name}`,
          detail: `${e.outages24h} outage event(s) in the last 24 h; district draw is now ${e.currentKw.toFixed(0)} kW.`,
        });
      if (load >= 0.95)
        push({
          id: `energy-load-${e.zoneId}`,
          severity: 'medium',
          system: 'energy',
          icon: '⚡',
          zone: e.name,
          title: `Feeder near capacity in ${e.name}`,
          detail: `Current draw is ${(load * 100).toFixed(0)}% of the 24 h peak.`,
        });
    }

    // waste
    const overfull = (binsQ.data ?? []).filter((b) => b.fillLevelPct >= OVERFULL);
    for (const b of overfull.slice(0, 12))
      push({
        id: `bin-${b.id}`,
        severity: b.fillLevelPct >= 95 ? 'high' : 'medium',
        system: 'waste',
        icon: '🗑️',
        zone: zoneName.get(b.zoneId ?? ''),
        title: `Bin overfull: ${b.name}`,
        detail: `${b.fillLevelPct}% full — schedule collection on the next optimized route.`,
      });

    // roads
    for (const r of (roadsQ.data ?? []).filter((r) => r.conditionScore < ROADS_POOR).slice(0, 12))
      push({
        id: `road-${r.id}`,
        severity: r.conditionScore < 30 ? 'high' : 'medium',
        system: 'roads',
        icon: '🛣️',
        title: `Deteriorating road: ${r.name}`,
        detail: `Condition score ${r.conditionScore}/100${r.openMaintenanceRequests ? ` · ${r.openMaintenanceRequests} open request(s)` : ''}.`,
      });

    return out.sort((a, b) => SEV_RANK[a.severity] - SEV_RANK[b.severity]);
  }, [firehoseQ.data, waterQ.data, energyQ.data, binsQ.data, roadsQ.data, zoneName]);

  const counts = useMemo(() => {
    const acc = { critical: 0, high: 0, medium: 0, watch: 0 } as Record<Severity, number>;
    for (const a of alerts) acc[a.severity] += 1;
    return acc;
  }, [alerts]);

  if (firehoseQ.isLoading) return <LoadingState label="Deriving alerts from live feeds…" />;
  if (firehoseQ.isError) return <ErrorState message="Failed to load live congestion feed. Is the API up?" />;

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <SevCard sev="critical" label="Critical" n={counts.critical} />
        <SevCard sev="high" label="High" n={counts.high} />
        <SevCard sev="medium" label="Medium" n={counts.medium} />
        <SevCard sev="watch" label="Watch" n={counts.watch} />
      </div>

      <Panel
        title="Alert feed"
        subtitle="Refined every 15 s from the live congestion, waterlogging, energy, waste and road feeds"
        right={
          <button
            type="button"
            className="button-ghost"
            onClick={() =>
              downloadCsv(
                'urbantwin-alerts',
                alerts.map(({ system, severity, title, detail, zone }) => ({
                  system,
                  severity,
                  zone: zone ?? '',
                  title,
                  detail,
                })),
              )
            }
          >
            ⬇ Export CSV
          </button>
        }
      >
        {alerts.length === 0 ? (
          <div className="flex flex-col items-center justify-center gap-2 py-12 text-center">
            <div className="text-4xl">✅</div>
            <div className="text-sm text-slate-400">All subsystems inside thresholds — nothing to escalate.</div>
          </div>
        ) : (
          <div className="space-y-3">
            {alerts.map((a) => (
              <div
                key={a.id}
                className={`rounded-xl border border-slate-800 border-l-4 bg-slate-950/60 px-4 py-3 ${SEV_TINT[a.severity]}`}
              >
                <div className="flex items-center gap-3">
                  <span className="text-lg">{a.icon}</span>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="truncate text-sm font-semibold text-slate-200">{a.title}</span>
                      <StatusBadge state={SEV_STYLE[a.severity]} label={a.severity} />
                    </div>
                    <div className="truncate text-xs text-slate-500">{a.detail}</div>
                  </div>
                  <div className="hidden shrink-0 text-right text-[11px] text-slate-500 sm:block">
                    <div className="uppercase tracking-wider">{a.system}</div>
                    <div>{a.stamp ? timeAgo(a.stamp) : a.zone ?? ''}</div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </Panel>

      <Panel title="Thresholds" subtitle="What triggers a row in the feed — tune in code, document in ops">
        <ColorDot color="#ef4444" label="congestion ≥ 80% · waterlogging severe · ≥3 outages" />
        <ColorDot color="#f87171" label="congestion ≥ 65% · waterlogging high · bin ≥ 95% · road < 30" />
        <ColorDot color="#fbbf24" label="bin ≥ 85% · road < 45 · feeder ≥ 95% of peak" />
        <ColorDot color="#34d399" label="congestion ≥ 50% (watch only)" />
      </Panel>
    </div>
  );
}

function SevCard({ sev, label, n }: { sev: Severity; label: string; n: number }) {
  const tint: Record<Severity, string> = {
    critical: 'from-red-500/10 to-transparent border-red-500/60',
    high: 'from-red-400/10 to-transparent border-red-400/50',
    medium: 'from-amber-400/10 to-transparent border-amber-400/50',
    watch: 'from-slate-400/10 to-transparent border-slate-400/40',
  };
  return (
    <div className={`rounded-2xl border bg-gradient-to-b p-4 ${tint[sev]}`}>
      <div className="text-xs font-medium uppercase tracking-wider text-slate-500">{label}</div>
      <div className="mt-1 flex items-baseline gap-2">
        <span className="text-2xl font-bold text-slate-50">{n}</span>
        <span className="text-xs text-slate-500">{n === 1 ? 'alert' : 'alerts'}</span>
      </div>
    </div>
  );
}
