import { api } from './client';
import type { TwinState } from '../types';

export async function getTwinState(): Promise<TwinState> {
  const { data } = await api.get('/twin/state');
  return data as TwinState;
}

/** SSE stream URL for the live twin feed (contract §7.11). */
export function twinLiveUrl(): string {
  return `${api.defaults.baseURL ?? ''}/twin/live`;
}
