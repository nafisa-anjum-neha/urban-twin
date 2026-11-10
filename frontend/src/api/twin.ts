import { api } from './client';
import type { TwinState } from '../types';

export async function getTwinState(): Promise<TwinState> {
  const { data } = await api.get('/twin/state');
  return data as TwinState;
}

/** KPI snapshot for every city (multi-city comparison table). */
export async function getCityCompare(): Promise<import('../types').CityCompareRow[]> {
  const { data } = await api.get('/twin/compare');
  return data as import('../types').CityCompareRow[];
}

/** SSE stream URL for the live twin feed (contract §7.11). */
export function twinLiveUrl(cityId?: string | null): string {
  const base = `${api.defaults.baseURL ?? ''}/twin/live`;
  return cityId ? `${base}?cityId=${encodeURIComponent(cityId)}` : base;
}
