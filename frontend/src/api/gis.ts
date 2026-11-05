import { api, intoList } from './client';
import type {
  GeoFeatureCollection,
  GisLayer,
  HeatmapPoint,
  ReportCategory,
  CitizenReport,
  ReportStatus,
} from '../types';

export async function getLayer(layer: GisLayer): Promise<GeoFeatureCollection> {
  const { data } = await api.get(`/gis/layers/${layer}`);
  return data as GeoFeatureCollection;
}

export async function getHeatmap(layer: 'traffic' | 'drainage' | 'energy'): Promise<
  HeatmapPoint[]
> {
  const { data } = await api.get('/gis/heatmap', { params: { layer } });
  return intoList<HeatmapPoint>(data);
}

export async function listReports(params: {
  status?: ReportStatus;
  category?: ReportCategory;
} = {}): Promise<CitizenReport[]> {
  const { data } = await api.get('/reports', { params });
  return intoList<CitizenReport>(data);
}

export async function createReport(body: {
  category: ReportCategory;
  description: string;
  lat: number;
  lon: number;
  reporterName: string;
  zoneId?: string;
}): Promise<CitizenReport> {
  const { data } = await api.post('/reports', body);
  return data as CitizenReport;
}

export async function upvoteReport(id: string): Promise<CitizenReport> {
  const { data } = await api.post(`/reports/${id}/upvote`);
  return data as CitizenReport;
}

export async function patchReportStatus(
  id: string,
  status: ReportStatus,
): Promise<CitizenReport> {
  const { data } = await api.patch(`/reports/${id}/status`, { status });
  return data as CitizenReport;
}
