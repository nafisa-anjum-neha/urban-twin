import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Panel from '../components/Panel';
import KpiCard from '../components/KpiCard';
import CascadeGraph from '../components/CascadeGraph';
import { LoadingState, ErrorState, EmptyState } from '../components/States';
import StatusBadge from '../components/StatusBadge';
import { runSimulation, listSimulationRuns, getSimulationRun } from '../api/simulation';
import { listZones } from '../api/cities';
import { fmtSec, pct, timeAgo } from '../lib/format';
import type { Scenario, SimulationResult } from '../types';

const SCENARIOS: { id: Scenario; label: string; icon: string; blurb: string; color: string }[] = [
  { id: 'rainfall', label: 'Heavy rainfall', icon: '🌧️', blurb: 'Drainage overload → waterlogging → traffic detours → emergency reroutes', color: 'sky' },
  { id: 'substation_outage', label: 'Substation outage', icon: '⚡', blurb: 'Traffic signals dark → congestion spike → emergency response slowdown', color: 'amber' },
  { id: 'water_main_burst', label: 'Water main burst', icon: '💧', blurb: 'Localized waterlogging → road closures → field service rerouting', color: 'violet' },
  { id: 'sensor_failure', label: 'Sensor failure', icon: '📡', blurb: 'Stale telemetry → twin staleness alarms → degraded decisions', color: 'red' },
];

const PLAY_MS = 15_000;

export default function Simulation() {
  const qc = useQueryClient();
  const [scenario, setScenario] = useState<Scenario>('rainfall');
  const [name, setName] = useState('Evening monsoon surge');
  const [zoneId, setZoneId] = useState('');
  const [intensity, setIntensity] = useState(90);
  const [duration, setDuration] = useState(120);
  const [result, setResult] = useState<SimulationResult | null>(null);
  const [visibleSec, setVisibleSec] = useState(0);
  const [playing, setPlaying] = useState(false);

  const zonesQ = useQuery({ queryKey: ['zones'], queryFn: () => listZones() });
  const historyQ = useQuery({
    queryKey: ['sim-runs'],
    queryFn: () => listSimulationRuns({ limit: 10 }),
    refetchInterval: 30_000,
  });

  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const stopTimer = () => {
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }
    setPlaying(false);
  };

  const startPlayback = (total: number) => {
    stopTimer();
    setVisibleSec(0);
    setPlaying(true);
    const step = total / (PLAY_MS / 120);
    timerRef.current = setInterval(() => {
      setVisibleSec((s) => {
        const next = s + step;
        if (next >= total) {
          stopTimer();
          return total;
        }
        return next;
      });
    }, 120);
  };

  const runM = useMutation({
    mutationFn: () =>
      runSimulation({
        scenario,
        name: name || `${scenario} run`,
        params: {
          intensityMm: scenario === 'rainfall' || scenario === 'water_main_burst' ? intensity : undefined,
          durationMinutes: duration,
          zoneId: zoneId || undefined,
          criticality: 'high',
        },
      }),
    onSuccess: (r) => {
      setResult(r);
      startPlayback(r.timelineSeconds || 3600);
      void qc.invalidateQueries({ queryKey: ['sim-runs'] });
    },
  });

  useEffect(() => () => stopTimer(), []);

  const timeline = result?.timelineSeconds ?? 3600;
  const activated = (result?.cascade.nodes ?? []).filter((n) => n.startOffsetSec <= visibleSec + 0.001).length;

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-2 gap-4 xl:grid-cols-4">
        {SCENARIOS.map((s) => (
          <button
            key={s.id}
            type="button"
            onClick={() => setScenario(s.id)}
            className={[
              'rounded-2xl border px-4 py-3 text-left transition-colors',
              scenario === s.id ? 'border-emerald-700 bg-emerald-950/30' : 'border-slate-800 bg-slate-900/50 hover:bg-slate-900',
            ].join(' ')}
          >
            <div className="flex items-center justify-between">
              <span className="text-sm font-semibold text-white">{s.icon} {s.label}</span>
              {scenario === s.id && <span className="h-2 w-2 rounded-full bg-emerald-400 animate-pulse-dot" />}
            </div>
            <p className="mt-1 text-[11px] leading-relaxed text-slate-500">{s.blurb}</p>
          </button>
        ))}
      </div>

      <Panel title="Run a cascade simulation" subtitle="The engine queries live system state and propagates the failure between infrastructure domains">
        <div className="grid gap-3 md:grid-cols-5">
          <label className="col-span-1 text-xs text-slate-400 md:col-span-2">
            Run name
            <input className="input mt-1" value={name} onChange={(e) => setName(e.target.value)} placeholder="name this scenario" />
          </label>
          <label className="text-xs text-slate-400">
            Target zone (optional)
            <select className="select mt-1" value={zoneId} onChange={(e) => setZoneId(e.target.value)}>
              <option value="">Whole city</option>
              {(zonesQ.data ?? []).map((z) => (
                <option key={z.id} value={z.id}>{z.name}</option>
              ))}
            </select>
          </label>
          <label className="text-xs text-slate-400">
            Rainfall intensity (mm/h)
            <input
              type="number"
              className="input mt-1"
              value={intensity}
              min={10}
              max={220}
              onChange={(e) => setIntensity(Number(e.target.value))}
              disabled={scenario !== 'rainfall' && scenario !== 'water_main_burst'}
            />
          </label>
          <label className="text-xs text-slate-400">
            Event duration (min)
            <input type="number" className="input mt-1" value={duration} min={10} max={480} onChange={(e) => setDuration(Number(e.target.value))} />
          </label>
        </div>
        <div className="mt-4 flex items-center gap-3">
          <button type="button" className="button" onClick={() => runM.mutate()} disabled={runM.isPending}>
            {runM.isPending ? 'Simulating…' : '▶ Run cascade'}
          </button>
          {result && (
            <div className="flex items-center gap-2 text-xs">
              <button type="button" className="ticket" onClick={() => result && startPlayback(timeline)}>
                ↻ Replay
              </button>
              <button type="button" className={playing ? 'ticket' : 'ticket'} onClick={() => (playing ? stopTimer() : startPlayback(timeline))}>
                {playing ? '⏸ Pause' : '⏵ Play'}
              </button>
              <button type="button" className="ticket" onClick={() => { stopTimer(); setVisibleSec(Infinity); }}>
                ⏭ Skip to end
              </button>
              <span className="ml-2 text-slate-500">
                simulated {fmtSec(visibleSec === Infinity ? timeline : Math.min(visibleSec, timeline))} / {fmtSec(timeline)}
              </span>
            </div>
          )}
          {runM.isError && <p className="text-xs text-red-400">{(runM.error as Error).message}</p>}
        </div>
      </Panel>

      {result && (
        <>
          <div className="grid gap-4 md:grid-cols-3 xl:grid-cols-6">
            <KpiCard label="Zones affected" value={result.impactSummary.zonesAffected} icon="🏙️" accent="sky" />
            <KpiCard label="Roads impacted" value={result.impactSummary.roadsImpacted} icon="🛣️" accent="amber" />
            <KpiCard label="Roads waterlogged" value={result.impactSummary.roadsWaterlogged ?? '—'} icon="🌊" accent="red" />
            <KpiCard label="Incidents rerouted" value={result.impactSummary.incidentsRerouted ?? '—'} icon="🚑" accent="red" />
            <KpiCard label="Citizen reports est." value={result.impactSummary.citizenReportsEstimate ?? '—'} icon="🗳️" accent="violet" />
            <KpiCard
              label="Cascade progress"
              value={`${activated}/${result.cascade.nodes.length}`}
              icon="🌀"
              accent="emerald"
              sub={`${pct(activated / result.cascade.nodes.length, 1)} of the chain fired`}
            />
          </div>

          <Panel
            title="Cascade graph"
            subtitle="Nodes appear as the failure propagates across systems — edge labels show propagation probability"
            right={<StatusBadge status={result.status} label={result.scenario.replace('_', ' ')} />}
          >
            <CascadeGraph cascade={result.cascade} visibleSec={visibleSec === Infinity ? Infinity : visibleSec} />
          </Panel>

          <Panel title="Event log" subtitle="Chronological propagation record">
            <div className="max-h-64 space-y-1 overflow-y-auto text-xs">
              {result.cascade.nodes
                .slice()
                .sort((a, b) => a.startOffsetSec - b.startOffsetSec)
                .map((n, i) => {
                  const fired = n.startOffsetSec <= visibleSec + 0.001;
                  return (
                    <div
                      key={n.id}
                      className={[
                        'flex items-center gap-3 rounded-lg px-3 py-1.5 transition-colors',
                        fired ? 'bg-slate-900/70 text-slate-200' : 'text-slate-600',
                      ].join(' ')}
                    >
                      <span className="w-16 shrink-0 font-mono text-[10px] text-slate-500">+{fmtSec(n.startOffsetSec)}</span>
                      <span className="w-24 shrink-0 text-[10px] uppercase tracking-wider text-slate-500">{n.type}</span>
                      <span className="flex-1">{n.label}</span>
                      {i === 0 && <span className="text-[10px] text-emerald-400">source</span>}
                      {fired && i > 0 && <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />}
                    </div>
                  );
                })}
            </div>
          </Panel>
        </>
      )}

      {!result && !runM.isPending && (
        <Panel>
          {runM.isError ? (
            <ErrorState message={(runM.error as Error)?.message} onRetry={() => runM.reset()} />
          ) : (
            <EmptyState
              message="Pick a scenario above and hit ▶ Run cascade."
              hint="The simulator reads real sensor state from the platform and models how one failure cascades into the others."
            />
          )}
        </Panel>
      )}

      <Panel title="Recent runs" subtitle="Persisted cascade reproductions">
        {historyQ.isLoading ? (
          <LoadingState />
        ) : (historyQ.data ?? []).length === 0 ? (
          <EmptyState message="No runs yet." />
        ) : (
          <table className="table">
            <thead>
              <tr><th>Name</th><th>Scenario</th><th>Status</th><th>Started</th><th></th></tr>
            </thead>
            <tbody>
              {(historyQ.data ?? []).map((r) => (
                <tr key={r.simulationId ?? r.id ?? r.name}>
                  <td className="font-medium text-slate-200">{r.name ?? '—'}</td>
                  <td><StatusBadge status="" label={r.scenario.replace('_', ' ')} /></td>
                  <td><StatusBadge status={r.status} /></td>
                  <td className="text-[11px] text-slate-500">{timeAgo(r.startedAt)}</td>
                  <td>
                    {r.simulationId && (
                      <button
                        type="button"
                        className="ticket"
                        onClick={async () => {
                          const full = await getSimulationRun(r.simulationId!);
                          setResult(full);
                          startPlayback(full.timelineSeconds || 3600);
                        }}
                      >
                        Load
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>
    </div>
  );
}
