import { api, intoList } from './client';
import type {
  Dispatch,
  EmergencyIncident,
  EmergencyVehicle,
  IncidentStatus,
  IncidentType,
  LonLat,
  UUID,
  VehicleStatus,
} from '../types';

export async function listIncidents(params: {
  status?: IncidentStatus;
  type?: IncidentType;
  minSeverity?: number;
} = {}): Promise<EmergencyIncident[]> {
  const { data } = await api.get('/emergency/incidents', { params });
  return intoList<EmergencyIncident>(data);
}

export async function createIncident(body: {
  name: string;
  type: IncidentType;
  severity: number;
  lat: number;
  lon: number;
  description?: string;
  zoneId?: UUID;
}): Promise<EmergencyIncident> {
  const { data } = await api.post('/emergency/incidents', body);
  return data as EmergencyIncident;
}

export async function dispatchIncident(
  id: UUID,
  body: { vehicleId?: UUID | null } = {},
): Promise<Dispatch> {
  const { data } = await api.post(`/emergency/incidents/${id}/dispatch`, body);
  return data as Dispatch;
}

export async function resolveDispatch(id: UUID): Promise<Dispatch> {
  const { data } = await api.post(`/emergency/dispatch/${id}/resolve`);
  return data as Dispatch;
}

export async function listVehicles(params: { status?: VehicleStatus } = {}): Promise<
  EmergencyVehicle[]
> {
  const { data } = await api.get('/emergency/vehicles', { params });
  return intoList<EmergencyVehicle>(data);
}

export async function getSafestRoute(incidentId: UUID): Promise<{
  features: {
    geometry: { type: string; coordinates: number[][] | number[][][] };
    properties: Record<string, unknown>;
  }[];
}> {
  const { data } = await api.get('/emergency/route/safest', {
    params: { incidentId },
  });
  return data as {
    features: {
      geometry: { type: string; coordinates: number[][] | number[][][] };
      properties: Record<string, unknown>;
    }[];
  };
}

export function incidentPoint(i: EmergencyIncident): LonLat | null {
  return i.location;
}
