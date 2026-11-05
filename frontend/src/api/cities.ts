import { api, intoList } from './client';
import type { City, UUID, Zone } from '../types';

export async function listCities(): Promise<City[]> {
  const { data } = await api.get('/cities');
  return intoList<City>(data);
}

export async function listZones(cityId?: UUID): Promise<Zone[]> {
  const { data } = await api.get('/zones', { params: cityId ? { cityId } : undefined });
  return intoList<Zone>(data);
}
