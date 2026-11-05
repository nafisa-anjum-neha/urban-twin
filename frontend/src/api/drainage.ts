import { api, intoList } from './client';
import type {
  DrainageNetwork,
  DrainageSensor,
  DrainageSensorStatus,
  LonLat,
  WaterLevelReading,
  WaterloggingPrediction,
} from '../types';

export async function listDrainageNetworks(params: { zoneId?: string } = {}): Promise<
  DrainageNetwork[]
> {
  const { data } = await api.get('/drainage/networks', { params });
  return intoList<DrainageNetwork>(data);
}

export async function listDrainageSensors(params: {
  networkId?: string;
  status?: DrainageSensorStatus;
} = {}): Promise<DrainageSensor[]> {
  const { data } = await api.get('/drainage/sensors', { params });
  return intoList<DrainageSensor>(data);
}

export async function listWaterLevelReadings(params: {
  sensorId?: string;
  from?: string;
  to?: string;
}): Promise<WaterLevelReading[]> {
  const { data } = await api.get('/drainage/readings', { params });
  return intoList<WaterLevelReading>(data);
}

export async function getWaterloggingPredictions(): Promise<WaterloggingPrediction[]> {
  const { data } = await api.get('/drainage/waterlogging/predictions');
  return intoList<WaterloggingPrediction>(data);
}

/** Convenience: derive [lon, lat] from a sensor if present. */
export function drainageSensorPoint(sensor: DrainageSensor): LonLat | null {
  return sensor.location;
}
