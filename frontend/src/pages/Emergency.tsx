import { useState } from 'react';
import { CircleMarker, Polyline, Tooltip } from 'react-leaflet';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Panel from '../components/Panel';
import CityMap from '../components/CityMap';
import KpiCard from '../components/KpiCard';
import MapCard, { ColorDot } from '../components/MapCard';
import { LoadingState, ErrorState, EmptyState } from '../components/States';
import StatusBadge from '../components/StatusBadge';
import { listIncidents, listVehicles, dispatchIncident, resolveDispatch, getSafestRoute } from '../api/emergency';
import { timeAgo } from '../lib/format';
import type { Dispatch } from '../types';

export default function Emergency() {
  const qc = useQueryClient();
  const [sessionDispatches, setSessionDispatches] = useState<Dispatch[]>([]);

  const incidentsQ = useQuery({
    queryKey: ['incidents'],
    queryFn: () => listIncidents({}),
    refetchInterval: 20_000,
  });
  const vehiclesQ = useQuery({ queryKey: ['vehicles'], queryFn: () => listVehicles({}) });

  const dispatchM = useMutation({
    mutationFn: (incidentId: string) => dispatchIncident(incidentId, {}),
    onSuccess: (d) => {
      setSessionDispatches((prev) => [...prev.filter((x) => x.id !== d.id), d]);
      void qc.invalidateQueries({ queryKey: ['incidents'] });
      void qc.invalidateQueries({ queryKey: ['vehicles'] });
    },
  });
  const resolveM = useMutation({
    mutationFn: (dispatchId: string) => resolveDispatch(dispatchId),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['incidents'] });
      void qc.invalidateQueries({ queryKey: ['vehicles'] });
    },
  });
  const routesQ = useQuery({
    queryKey: ['safe-route', sessionDispatches[sessionDispatches.length - 1]?.incidentId],
    queryFn: () => getSafestRoute(sessionDispatches[sessionDispatches.length - 1].incidentId),
    enabled: sessionDispatches.length > 0,
  });

  if (incidentsQ.isLoading || vehiclesQ.isLoading) return <LoadingState label="Scanning for incidents…" />;
  if (incidentsQ.isError)
    return <ErrorState message={(incidentsQ.error as Error)?.message} onRetry={() => incidentsQ.refetch()} />;

  const incidents = incidentsQ.data ?? [];
  const vehicles = vehiclesQ.data ?? [];
  const active = incidents.filter((i) => i.status !== 'resolved');
  const availableVehicles = vehicles.filter((v) => v.status === 'available');
  const lastDispatch = sessionDispatches[sessionDispatches.length - 1];

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-2 gap-4 xl:grid-cols-4">
        <KpiCard label="Active incidents" value={active.length} icon="🚨" accent="red" />
        <KpiCard label="Vehicles available" value={availableVehicles.length} sub={`of ${vehicles.length} stationed`} icon="🚑" accent="emerald" />
        <KpiCard label="Dispatched now" value={vehicles.filter((v) => v.status === 'dispatched').length} icon="🚨" accent="amber" />
        <KpiCard label="Resolved all-time" value={incidents.filter((i) => i.status === 'resolved').length} icon="✅" accent="sky" />
      </div>

      <div className="grid gap-5 xl:grid-cols-3">
        <div className="xl:col-span-2">
          <MapCard
            title="Incident & vehicle map"
            subtitle="Marker size reflects severity · red = active, ringed = dispatched"
            legend={
              <>
                <ColorDot color="#ef4444" label="active incident" />
                <ColorDot color="#fbbf24" label="dispatched incident" />
                <ColorDot color="#34d399" label="available vehicle" />
                <ColorDot color="#64748b" label="unavailable vehicle" />
              </>
            }
          >
            <CityMap height={430}>
              {incidents.map((i) =>
                i.location && (i.location[0] !== 0 || i.location[1] !== 0) ? (
                  <CircleMarker
                    key={i.id}
                    center={[i.location[1], i.location[0]]}
                    radius={6 + i.severity * 2}
                    pathOptions={{
                      color: i.status === 'resolved' ? '#334155' : i.status === 'dispatched' ? '#fbbf24' : '#ef4444',
                      fillColor: i.status === 'resolved' ? '#475569' : i.status === 'dispatched' ? '#f59e0b' : '#ef4444',
                      fillOpacity: i.status === 'resolved' ? 0.4 : 0.85,
                      weight: 2,
                    }}
                  >
                    <Tooltip sticky>
                      <b>{i.name}</b>
                      <div>{i.incidentType} · severity {i.severity}/5</div>
                      <div>{i.status} · {timeAgo(i.reportedAt)}</div>
                      {i.zoneName && <div>zone: {i.zoneName}</div>}
                    </Tooltip>
                  </CircleMarker>
                ) : null,
              )}
              {vehicles.map((v) =>
                v.station && (v.station[0] !== 0 || v.station[1] !== 0) ? (
                  <CircleMarker
                    key={v.id}
                    center={[v.station[1], v.station[0]]}
                    radius={5}
                    pathOptions={{
                      color: v.status === 'available' ? '#34d399' : '#64748b',
                      fillColor: '#0f172a',
                      fillOpacity: 0.9,
                      weight: 2,
                    }}
                  >
                    <Tooltip sticky>
                      <b>{v.name}</b>
                      <div>{v.vehicleType} · {v.status}</div>
                    </Tooltip>
                  </CircleMarker>
                ) : null,
              )}
              {lastDispatch?.routeGeojson && (
                <Polyline
                  pathOptions={{ color: '#38bdf8', weight: 3.5, dashArray: '4 7', opacity: 0.9 }}
                  positions={
                    (JSON.parse(lastDispatch.routeGeojson as string) as { coordinates: [number, number][] }).coordinates.map(
                      ([lon, lat]) => [lat, lon] as [number, number],
                    )
                  }
                />
              )}
              {routesQ.data &&
                routesQ.data.features.map((f, i) => (
                  <Polyline
                    key={i}
                    pathOptions={{ color: '#a78bfa', weight: 2, opacity: 0.5 }}
                    positions={
                      (f.geometry.coordinates as number[][]).map(([lon, lat]) => [lat, lon] as [number, number])
                    }
                  />
                ))}
            </CityMap>
          </MapCard>
        </div>

        <Panel title="Dispatch queue" subtitle="Send the nearest available unit">
          <div className="space-y-3">
            {active.length === 0 && <EmptyState message="No active incidents. The city is quiet." />}
            {active.map((i) => {
              const d = sessionDispatches.find((x) => x.incidentId === i.id);
              return (
                <div key={i.id} className="rounded-xl border border-slate-800 bg-slate-950/40 px-4 py-3">
                  <div className="flex items-center justify-between gap-2">
                    <span className="truncate text-sm font-medium text-slate-200">{i.name}</span>
                    <StatusBadge status={i.status} />
                  </div>
                  <div className="mt-1 text-[11px] text-slate-500">
                    {i.incidentType} · severity {i.severity}/5 · {timeAgo(i.reportedAt)}
                  </div>
                  <div className="mt-2 flex items-center gap-2">
                    {i.status === 'active' && (
                      <button
                        type="button"
                        className="ticket"
                        onClick={() => dispatchM.mutate(i.id)}
                        disabled={dispatchM.isPending || availableVehicles.length === 0}
                      >
                        {availableVehicles.length === 0 ? 'No units free' : 'Dispatch nearest'}
                      </button>
                    )}
                    {d && d.status !== 'arrived' && d.status !== 'cancelled' && (
                      <>
                        <span className="text-[11px] text-slate-400">
                          ETA {Math.round(d.etaMin)} min · {d.distanceKm.toFixed(1)} km
                        </span>
                        <button
                          type="button"
                          className="ticket"
                          onClick={() => resolveM.mutate(d.id)}
                          disabled={resolveM.isPending}
                        >
                          Resolve
                        </button>
                      </>
                    )}
                  </div>
                  {dispatchM.isError && dispatchM.variables === i.id && (
                    <p className="mt-1 text-[11px] text-red-400">{(dispatchM.error as Error).message}</p>
                  )}
                </div>
              );
            })}
          </div>
        </Panel>
      </div>

      <Panel title="Fleet" subtitle="Emergency vehicles and stations">
        <table className="table">
          <thead>
            <tr><th>Vehicle</th><th>Type</th><th>Status</th><th>Station</th></tr>
          </thead>
          <tbody>
            {vehicles.map((v) => (
              <tr key={v.id}>
                <td className="font-medium text-slate-200">{v.name}</td>
                <td>{v.vehicleType.replace('_', ' ')}</td>
                <td><StatusBadge status={v.status} /></td>
                <td className="text-[11px] text-slate-500">{v.station ? `${v.station[1].toFixed(4)}, ${v.station[0].toFixed(4)}` : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Panel>
    </div>
  );
}
