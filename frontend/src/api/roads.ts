import { api, intoList } from './client';
import type {
  MaintenancePriority,
  MaintenanceRequest,
  MaintenanceStatus,
  MaintenanceSummaryRow,
  Page,
  Road,
  UUID,
} from '../types';

export async function listRoads(params: {
  zoneId?: UUID;
  maxCondition?: number;
}): Promise<Road[]> {
  const { data } = await api.get('/roads', { params });
  return intoList<Road>(data);
}

export async function getRoad(id: UUID): Promise<Road> {
  const { data } = await api.get(`/roads/${id}`);
  return data as Road;
}

export async function listMaintenanceRequests(params: {
  status?: MaintenanceStatus;
  priority?: MaintenancePriority;
  roadId?: UUID;
}): Promise<MaintenanceRequest[]> {
  const { data } = await api.get('/maintenance/requests', { params });
  return intoList<MaintenanceRequest>(data);
}

export async function createMaintenanceRequest(body: {
  title: string;
  description?: string;
  priority: MaintenancePriority;
  roadId?: UUID;
  assetId?: UUID;
  reportedBy: string;
}): Promise<MaintenanceRequest> {
  const { data } = await api.post('/maintenance/requests', body);
  return data as MaintenanceRequest;
}

export async function patchMaintenanceStatus(
  id: UUID,
  status: MaintenanceStatus,
): Promise<MaintenanceRequest> {
  const { data } = await api.patch(`/maintenance/requests/${id}/status`, { status });
  return data as MaintenanceRequest;
}

export async function getMaintenanceSummary(): Promise<MaintenanceSummaryRow[]> {
  const { data } = await api.get('/maintenance/summary');
  return intoList<MaintenanceSummaryRow>(data);
}

/** Re-exported type guard: readings pages accept both envelopes and plain arrays. */
export type ReadingPage<T> = Page<T>;
