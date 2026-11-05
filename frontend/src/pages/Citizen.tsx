import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Panel from '../components/Panel';
import KpiCard from '../components/KpiCard';
import MapCard from '../components/MapCard';
import { LoadingState, ErrorState, EmptyState } from '../components/States';
import StatusBadge from '../components/StatusBadge';
import { CITY_FALLBACK_CENTER } from '../hooks/geo';
import { listReports, createReport, upvoteReport, patchReportStatus } from '../api/gis';
import { listZones } from '../api/cities';
import { timeAgo } from '../lib/format';
import type { ReportCategory, ReportStatus } from '../types';

const CATEGORIES: ReportCategory[] = ['pothole', 'leak', 'lighting', 'garbage', 'flooding', 'other'];
const STATUS_FLOW: Record<ReportStatus, ReportStatus[]> = {
  submitted: ['acknowledged'],
  acknowledged: ['in_progress'],
  in_progress: ['resolved'],
  resolved: [],
};

export default function Citizen() {
  const qc = useQueryClient();
  const reportsQ = useQuery({ queryKey: ['reports'], queryFn: () => listReports({}), refetchInterval: 30_000 });
  const zonesQ = useQuery({ queryKey: ['zones'], queryFn: () => listZones() });

  const [form, setForm] = useState({
    category: 'pothole' as ReportCategory,
    description: '',
    lat: String(CITY_FALLBACK_CENTER[0]),
    lon: String(CITY_FALLBACK_CENTER[1]),
    zoneId: '',
    reporterName: 'citizen',
  });

  const createM = useMutation({
    mutationFn: () =>
      createReport({
        category: form.category,
        description: form.description,
        lat: Number(form.lat),
        lon: Number(form.lon),
        reporterName: form.reporterName || 'citizen',
        zoneId: form.zoneId || undefined,
      }),
    onSuccess: () => {
      setForm({ ...form, description: '' });
      void qc.invalidateQueries({ queryKey: ['reports'] });
    },
  });
  const upvoteM = useMutation({
    mutationFn: (id: string) => upvoteReport(id),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['reports'] }),
  });
  const statusM = useMutation({
    mutationFn: ({ id, status }: { id: string; status: ReportStatus }) => patchReportStatus(id, status),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['reports'] }),
  });

  const reports = reportsQ.data ?? [];

  if (reportsQ.isLoading) return <LoadingState label="Gathering citizen reports…" />;
  if (reportsQ.isError)
    return <ErrorState message={(reportsQ.error as Error)?.message} onRetry={() => reportsQ.refetch()} />;

  const byStatus = (s: ReportStatus) => reports.filter((r) => r.status === s).length;

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-2 gap-4 xl:grid-cols-4">
        <KpiCard label="Submitted" value={byStatus('submitted')} icon="🆕" accent="slate" />
        <KpiCard label="Acknowledged" value={byStatus('acknowledged')} icon="👁️" accent="amber" />
        <KpiCard label="In progress" value={byStatus('in_progress')} icon="⚙️" accent="sky" />
        <KpiCard label="Resolved" value={byStatus('resolved')} icon="✅" accent="emerald" />
      </div>

      <div className="grid gap-5 xl:grid-cols-3">
        <Panel title="Report an issue" subtitle="Crowd-sourced infrastructure problems">
          <form
            className="space-y-2"
            onSubmit={(e) => {
              e.preventDefault();
              createM.mutate();
            }}
          >
            <select className="select w-full" value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value as ReportCategory })}>
              {CATEGORIES.map((c) => (
                <option key={c} value={c}>{c}</option>
              ))}
            </select>
            <textarea
              className="input h-20"
              placeholder="What did you see? e.g. flooded underpass on ORR after 7 pm…"
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              required
            />
            <div className="flex gap-2">
              <input className="input" placeholder="lat" value={form.lat} onChange={(e) => setForm({ ...form, lat: e.target.value })} required />
              <input className="input" placeholder="lon" value={form.lon} onChange={(e) => setForm({ ...form, lon: e.target.value })} required />
            </div>
            <div className="flex gap-2">
              <input className="input" placeholder="your name" value={form.reporterName} onChange={(e) => setForm({ ...form, reporterName: e.target.value })} />
              <select className="select" value={form.zoneId} onChange={(e) => setForm({ ...form, zoneId: e.target.value })}>
                <option value="">auto-detect zone</option>
                {(zonesQ.data ?? []).map((z) => (
                  <option key={z.id} value={z.id}>{z.name}</option>
                ))}
              </select>
            </div>
            {createM.isError && <p className="text-xs text-red-400">{(createM.error as Error).message}</p>}
            <button type="submit" className="button" disabled={createM.isPending || !form.description}>
              {createM.isPending ? 'Sending…' : 'Submit report'}
            </button>
          </form>
        </Panel>

        <div className="xl:col-span-2">
          <MapCard title="Report locations" subtitle="Unresolved issues plotted on the map">
            <div className="rounded-xl border border-dashed border-slate-800 bg-slate-950/40 px-4 py-6 text-center text-xs text-slate-500">
              {reports.filter((r) => r && r.location).length} reports · locations are ingested with each submission. Open the{' '}
              <b className="text-slate-300">Digital Twin</b> page for the city-wide map.
            </div>
          </MapCard>
        </div>
      </div>

      <Panel title="All reports" subtitle="Upvote hot issues · advance status through the pipeline">
        {reports.length === 0 ? (
          <EmptyState message="No reports yet." hint="Be the first citizen to report something." />
        ) : (
          <div className="max-h-[480px] overflow-y-auto">
            <table className="table">
              <thead>
                <tr><th>Category</th><th>Description</th><th>Zone</th><th>Reporter</th><th>Votes</th><th>Status</th><th>Next</th></tr>
              </thead>
              <tbody>
                {reports.map((r) => (
                  <tr key={r.id}>
                    <td><StatusBadge status="" label={r.category} state={r.status === 'resolved' ? 'emerald' : 'violet'} /></td>
                    <td className="max-w-[260px] truncate text-slate-300">{r.description}</td>
                    <td className="text-[11px] text-slate-500">{r.zoneName ?? '—'}</td>
                    <td className="text-[11px] text-slate-500">{r.reporterName} · {timeAgo(r.createdAt)}</td>
                    <td>
                      <button type="button" className="ticket" onClick={() => upvoteM.mutate(r.id)} disabled={upvoteM.isPending}>
                        ▲ {r.upvotes}
                      </button>
                    </td>
                    <td><StatusBadge status={r.status} label={r.status.replace('_', ' ')} /></td>
                    <td>
                      <div className="flex gap-1">
                        {(STATUS_FLOW[r.status] ?? []).map((next) => (
                          <button key={next} type="button" className="ticket" onClick={() => statusM.mutate({ id: r.id, status: next })}>
                            {next.replace('_', ' ')}
                          </button>
                        ))}
                        {(STATUS_FLOW[r.status] ?? []).length === 0 && <span className="text-[11px] text-slate-600">done</span>}
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
  );
}
