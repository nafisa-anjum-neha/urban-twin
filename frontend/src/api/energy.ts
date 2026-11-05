import { api, intoList } from './client';
import type {
  AssetStatus,
  AssetType,
  ElectricityGrid,
  EnergyZoneSummary,
  GridReading,
  InfrastructureAsset,
  UUID,
} from '../types';

export async function listGrids(params: { zoneId?: UUID } = {}): Promise<ElectricityGrid[]> {
  const { data } = await api.get('/energy/grids', { params });
  return intoList<ElectricityGrid>(data);
}

export async function listGridReadings(params: {
  gridId?: UUID;
  from?: string;
  to?: string;
}): Promise<GridReading[]> {
  const { data } = await api.get('/energy/readings', { params });
  return intoList<GridReading>(data);
}

export async function getEnergySummary(): Promise<EnergyZoneSummary[]> {
  const { data } = await api.get('/energy/summary');
  return intoList<EnergyZoneSummary>(data);
}

export async function listAssets(params: {
  type?: AssetType;
  status?: AssetStatus;
} = {}): Promise<InfrastructureAsset[]> {
  const { data } = await api.get('/energy/assets', { params });
  return intoList<InfrastructureAsset>(data);
}
