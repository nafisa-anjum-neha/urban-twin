import { api, intoList } from './client';
import type { Scenario, SimulationResult, SimulationRunRequest, SimulationRunSummary } from '../types';

export async function runSimulation(body: SimulationRunRequest): Promise<SimulationResult> {
  const { data } = await api.post('/simulation/run', body);
  return data as SimulationResult;
}

export async function listSimulationRuns(
  params: { scenario?: Scenario; limit?: number } = {},
): Promise<SimulationRunSummary[]> {
  const { data } = await api.get('/simulation/runs', { params });
  return intoList<SimulationRunSummary>(data);
}

export async function getSimulationRun(id: string): Promise<SimulationResult> {
  const { data } = await api.get(`/simulation/runs/${id}`);
  return data as SimulationResult;
}
