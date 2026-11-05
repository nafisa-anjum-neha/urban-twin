import { api, intoList } from './client';
import type { UUID, WasteBin, WasteRoute, WasteRouteStatus } from '../types';

export async function listWasteBins(params: {
  zoneId?: UUID;
  minFill?: number;
} = {}): Promise<WasteBin[]> {
  const { data } = await api.get('/waste/bins', { params });
  return intoList<WasteBin>(data);
}

export async function collectBin(id: UUID): Promise<WasteBin> {
  const { data } = await api.post(`/waste/bins/${id}/collect`);
  return data as WasteBin;
}

export async function optimizeRoute(body: {
  zoneId: UUID;
  vehicleId?: UUID;
}): Promise<WasteRoute> {
  const { data } = await api.post('/waste/routes/optimize', body);
  return data as WasteRoute;
}

export async function listWasteRoutes(params: { status?: WasteRouteStatus } = {}): Promise<
  WasteRoute[]
> {
  const { data } = await api.get('/waste/routes', { params });
  return intoList<WasteRoute>(data);
}
