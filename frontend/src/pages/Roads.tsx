import { useMemo, useState } from 'react';
import { Polyline, Tooltip, Popup } from 'react-leaflet';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Panel from '../components/Panel';
import CityMap from '../components/CityMap';
import MapCard, { ColorDot } from '../components/MapCard';
import { LoadingState, ErrorState, EmptyState } from '../components/States';
import StatusBadge from '../components/StatusBadge';
import { parseGeometry, geometryToLatLng } from '../hooks/geo';
import { listRoads, listMaintenanceRequests, createMaintenanceRequest, patchMaintenanceStatus, getMaintenanceSummary } from '../api/roads';
import { timeAgo } from '../lib/format';
import type { MaintenancePriority, MaintenanceStatus, MaintenanceRequest, Road } from '../types';

function conditionColor(score: number): string {
  if (score >= 70) return '#34d399';
  if (score >= 45) return '#fbbf24';
  return '#f87171';
}

const NEXT: Record<MaintenanceStatus, MaintenanceStatus[]> = {
  open: ['assigned', 'closed'],
  assigned: ['in_progress', 'closed'],
  in_progress: ['resolved', 'closed'],
  resolved: ['closed'],
  closed: [],
};

export default function Roads() {
  const qc = useQueryClient();
  const roadsQ = useQuery({ queryKey: ['roads'], queryFn: () => listRoads({}) });
  const reqsQ = useQuery({ queryKey: ['maintenance'], queryFn: () => getMaintenanceSummary() });
  const listQ = useQuery({ queryKey: ['maintenance-list'], queryFn: () => listMaintenanceRequests({}) });

  const [form, setForm] = useState({ title: '', description: '', priority: 'p3' as MaintenancePriority, roadId: '', reporterName: 'operations@city.gov' });

  const createM = useMutation({
    mutationFn: () =>
      createMaintenanceRequest({
        title: form.title,
        description: form.description || undefined,
        priority: form.priority,
        roadId: form.roadId || undefined,
        reportedBy: form.reporterName,
      }),
    onSuccess: () => {
      setForm({ ...form, title: '', description: '' });
      void qc.invalidateQueries({ queryKey: ['maintenance-list'] });
      void qc.invalidateQueries({ queryKey: ['maintenance'] });
      void qc.invalidateQueries({ queryKey: ['roads'] });
    },
  });

  const patchM = useMutation({
    mutationFn: ({ id, status }: { id: string; status: MaintenanceStatus }) => patchMaintenanceStatus(id, status),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['maintenance-list'] });
      void qc.invalidateQueries({ queryKey: ['maintenance'] });
    },
  });

  const roadById = useMemo(() => new Map((roadsQ.data ?? []).map((r) => [r.id, r])), [roadsQ.data]);

  if (roadsQ.isLoading) return <LoadingState label="Surveying the tarmac…" />;
  if (roadsQ.isError)
    return <ErrorState message={(roadsQ.error as Error)?.message} onRetry={() => roadsQ.refetch()} />;

  const roads = roadsQ.data ?? [];
  const worst = [...roads].sort((a, b) => a.conditionScore - b.conditionScore).slice(0, 5);
  const open = (listQ.data ?? []).filter((r) => r.status !== 'resolved' && r.status !== 'closed');

  return (
    <div className="space-y-5">
      <div className="grid gap-5 xl:grid-cols-3">
        <div className="xl:col-span-2">
          <MapCard
            title="Road network condition"
            subtitle="Colored by condition score (0 degraded – 100 excellent)"
            legend={
              <>
                <ColorDot color="#34d399" label="good (70+)" />
                <ColorDot color="#fbbf24" label="fair (45–69)" />
                <ColorDot color="#f87171" label="poor (<45)" />
              </>
            }
          >
            <CityMap bounds={null} height={430}>
              {roads.map((r) => {
                const ring = roadPath(r);
                if (!ring) return null;
                return (
                  <Polyline key={r.id} pathOptions={{ color: conditionColor(r.conditionScore), weight: 4, opacity: 0.85 }} positions={ring}>
                    <Popup>
                      <b>{r.name}</b>
                      <div>class {r.roadClass} · score {Math.round(r.conditionScore)}</div>
                      <div>{r.openMaintenanceRequests ?? 0} open requests</div>
                    </Popup>
                    <Tooltip sticky>{r.name}</Tooltip>
                  </Polyline>
                );
              })}
            </CityMap>
          </MapCard>
        </div>

        <Panel title="Wear analysis" subtitle="Lowest condition scores first">
          <div className="space-y-3">
            {worst.map((r) => (
              <div key={r.id} className="rounded-xl border border-slate-800 bg-slate-950/40 px-4 py-3">
                <div className="flex items-center justify-between gap-2">
                  <span className="truncate text-sm font-medium text-slate-200">{r.name}</span>
                  <span className="text-sm font-semibold" style={{ color: conditionColor(r.conditionScore) }}>
                    {Math.round(r.conditionScore)}
                  </span>
                </div>
                <div className="relative mt-2 h-1.5 rounded-full bg-slate-800">
                  <div className="absolute h-full rounded-full" style={{ width: `${r.conditionScore}%`, backgroundColor: conditionColor(r.conditionScore) }} />
                </div>
              </div>
            ))}
          </div>

          <div className="mt-4 border-t border-slate-800 pt-4">
            <h3 className="mb-2 text-xs font-semibold uppercase tracking-wider text-slate-500">Open requests</h3>
            <div className="flex flex-wrap gap-2">
              {open.map((r) => (
                <StatusBadge key={r.id} status={r.priority} label={`${r.priority} · ${r.title}`} />
              ))}
              {open.length === 0 && <span className="text-xs text-slate-500">Nothing outstanding.</span>}
            </div>
          </div>
        </Panel>
      </div>

      <div className="grid gap-5 xl:grid-cols-3">
        <Panel title="Report an issue" subtitle="Creates a maintenance request">
          <form
            className="space-y-2"
            onSubmit={(e) => {
              e.preventDefault();
              createM.mutate();
            }}
          >
            <input
              className="input"
              placeholder="e.g. Pothole cluster near Aarathi bridge"
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
              required
            />
            <textarea
              className="input h-16"
              placeholder="Description (optional)"
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
            />
            <div className="flex gap-2">
              <input
                id="priority"
                className="input"
                placeholder="priority (p1–p4)"
                value={form.priority}
                onChange={(e) => setForm({ ...form, priority: (e.target.value || 'p3') as MaintenancePriority })}
              />
              <select
                className="input"
                value={form.roadId}
                onChange={(e) => setForm({ ...form, roadId: e.target.value })}
              >
                <option value="">No road linked</option>
                {roads.slice(0, 60).map((r) => (
                  <option key={r.id} value={r.id}>{r.name}</option>
                ))}
              </select>
            </div>
            {createM.isError && <p className="text-xs text-red-400">{(createM.error as Error).message}</p>}
            <button type="submit" className="button" disabled={createM.isPending || !form.title}>
              {createM.isPending ? 'Submitting…' : 'Submit request'}
            </button>
          </form>
          {reqsQ.data && (
            <p className="mt-3 text-[11px] text-slate-500">
              {reqsQ.data.reduce((s, r) => s + r.count, 0)} historical requests · summary by group archived below
            </p>
          )}
        </Panel>

        <Panel title="Maintenance workflow" subtitle="Move tickets through the pipeline" className="xl:col-span-2">
          {listQ.isLoading ? (
            <LoadingState />
          ) : (listQ.data ?? []).length === 0 ? (
            <EmptyState message="No maintenance requests yet." hint="Create one on the left." />
          ) : (
            <div className="max-h-[420px] overflow-y-auto">
              <table className="table">
                <thead>
                  <tr>
                    <th>Title</th>
                    <th>Priority</th>
                    <th>Reported</th>
                    <th>Status</th>
                    <th>Transition</th>
                  </tr>
                </thead>
                <tbody>
                  {(listQ.data ?? []).map((r) => (
                    <tr key={r.id}>
                      <td>
                        <div className="font-medium text-slate-200">{r.title}</div>
                        <div className="text-[11px] text-slate-500">{r.roadName ?? singleRoadName(roadById, r.roadId)}</div>
                      </td>
                      <td><StatusBadge status={r.priority} /></td>
                      <td className="text-[11px] text-slate-500">{timeAgo(r.createdAt)}</td>
                      <td><StatusBadge status={r.status} label={fixStatus(r.status)} /></td>
                      <td>
                        <div className="flex gap-1">
                          {NEXT[r.status]?.map((next) => (
                            <button
                              key={next}
                              type="button"
                              className="ticket"
                              onClick={() => patchM.mutate({ id: r.id, status: next })}
                            >
                              {fixStatus(next)}
                            </button>
                          ))}
                          {NEXT[r.status].length === 0 && <span className="text-[11px] text-slate-600">—</span>}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Panel>
      </div>
    </div>
  );
}

function fixStatus(s: string): string {
  return s.replace('_', ' ');
}

function singleRoadName(m: Map<string, Road>, roadId: string | null | undefined): string {
  return roadId ? (m.get(roadId)?.name ?? '') : '';
}

function roadPath(r: Road): [number, number][] | null {
  const ring = geometryToLatLng(parseGeometry(r.geojson));
  return ring.length >= 2 ? ring : null;
}

export interface PublicTypes {
  req: MaintenanceRequest;
}
