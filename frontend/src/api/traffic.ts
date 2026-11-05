import { api, intoList } from './client';
import type {
  CongestionPrediction,
  LonLat,
  Page,
  SensorStatus,
  TrafficReading,
  TrafficSensor,
  UUID,
  ZoneCongestion,
} from '../types';

export async function listTrafficSensors(params: {
  zoneId?: UUID;
  status?: SensorStatus;
}): Promise<TrafficSensor[]> {
  const { data } = await api.get('/traffic/sensors', { params });
  return intoList<TrafficSensor>(data);
}

export async function listTrafficReadings(params: {
  sensorId?: UUID;
  from?: string;
  to?: string;
}): Promise<Page<TrafficReading> | TrafficReading[]> {
  // PostGIS readings list; may be paged or plain — pass through, callers normalise.
  const { data } = await api.get('/traffic/readings', { params });
  return data as Page<TrafficReading> | TrafficReading[];
}

export async function getCongestionFirehose(): Promise<ZoneCongestion[]> {
  const { data } = await api.get('/traffic/congestion/firehose');
  return intoList<ZoneCongestion>(data);
}

export async function getCongestionPrediction(params: {
  zoneId?: UUID;
  horizonMinutes?: number;
}): Promise<CongestionPrediction[]> {
  const { data } = await api.get('/traffic/congestion/prediction', { params });
  return intoList<CongestionPrediction>(data);
}

export async function sensorLonLat(sensor: TrafficSensor): Promise<LonLat | null> {
  return sensor.location;
}
