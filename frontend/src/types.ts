/**
 * TypeScript types for the UrbanTwin REST contract (docs/CONTRACT.md §7).
 * Shapes mirror the backend DTOs exactly; list envelopes are generic.
 */

export type UUID = string;

/** Pageable list envelope: `{ content, total, page, size }` */
export interface Page<T> {
  content: T[];
  total: number;
  page: number;
  size: number;
}

/** `[lon, lat]` as exposed by backend DTOs for point geometries */
export type LonLat = [number, number];

// ---------------------------------------------------------------------------
// Shared enums
// ---------------------------------------------------------------------------

export type ZoneType = 'residential' | 'commercial' | 'industrial' | 'mixed';
export type RoadClass = 'hw' | 'ar' | 'co' | 'lo';
export type RoadClassName = 'hw' | 'ar' | 'co' | 'lo';
export type MaintenanceStatus =
  | 'open'
  | 'assigned'
  | 'in_progress'
  | 'resolved'
  | 'closed';
export type MaintenancePriority = 'p1' | 'p2' | 'p3' | 'p4';
export type DrainageNetworkClass = 'primary' | 'secondary';
export type DrainageSensorStatus = 'normal' | 'warning' | 'critical' | 'faulty';
export type SensorStatus = 'active' | 'inactive' | 'faulty';
export type WaterRisk = 'low' | 'moderate' | 'high' | 'severe';
export type AssetType = 'streetlight' | 'water_main' | 'pump' | 'transformer' | 'signal';
export type AssetStatus = 'operational' | 'degraded' | 'failed';
export type IncidentType = 'medical' | 'fire' | 'crime' | 'hazard' | 'flood';
export type IncidentStatus = 'active' | 'dispatched' | 'resolved';
export type VehicleType = 'ambulance' | 'fire_engine' | 'police' | 'rescue';
export type VehicleStatus = 'available' | 'dispatched' | 'offline';
export type DispatchStatus = 'dispatched' | 'en_route' | 'arrived' | 'cancelled';
export type ReportCategory = 'pothole' | 'leak' | 'lighting' | 'garbage' | 'flooding' | 'other';
export type ReportStatus = 'submitted' | 'acknowledged' | 'in_progress' | 'resolved';
export type WasteRouteStatus = 'planned' | 'active' | 'completed';
export type PowerState = 'ok' | 'watch' | 'outage';
export type NetworkStatus = 'ok' | 'degraded' | 'critical';
export type TrendDirection = 'up' | 'down' | 'stable';
export type Scenario = 'rainfall' | 'substation_outage' | 'water_main_burst' | 'sensor_failure';
export type SimulationStatus = 'running' | 'completed' | 'failed';
export type CascadeNodeType =
  | 'TRIGGER'
  | 'DRAINAGE'
  | 'ROAD'
  | 'TRAFFIC'
  | 'ENERGY'
  | 'INCIDENT'
  | 'REPORT'
  | 'MITIGATION';

// ---------------------------------------------------------------------------
// GeoJSON (GIS layers + embedded geometry)
// ---------------------------------------------------------------------------

export type GisLayer =
  | 'zones'
  | 'roads'
  | 'traffic-sensors'
  | 'drainage-sensors'
  | 'bins'
  | 'assets'
  | 'incidents'
  | 'emergency-vehicles'
  | 'energy-grids';

export interface GeoGeometry {
  type: 'Point' | 'LineString' | 'Polygon' | 'MultiPolygon';
  coordinates: number[] | number[][] | number[][][]; // GeoJSON positions are [lon, lat]
}

export interface GeoFeatureProps {
  id?: UUID;
  name?: string;
  status?: string;
  [key: string]: unknown;
}

export interface GeoFeature {
  type: 'Feature';
  geometry: GeoGeometry;
  properties: GeoFeatureProps;
}

export interface GeoFeatureCollection {
  type: 'FeatureCollection';
  features: GeoFeature[];
}

export interface HeatmapPoint {
  lat: number;
  lon: number;
  weight: number;
}

// ---------------------------------------------------------------------------
// §7.1 City / Zone
// ---------------------------------------------------------------------------

export interface City {
  id: UUID;
  name: string;
  slug: string;
  country?: string;
  /** Regional-indicator emoji rendered anywhere the city is named */
  flag?: string;
  population?: number;
  zoneCount?: number;
}

/** One row of `GET /twin/compare` — cross-city KPI snapshot. */
export interface CityCompareRow {
  cityId: UUID;
  name: string;
  slug?: string;
  flag?: string;
  country?: string;
  zones: number;
  avgCongestion: number;
  activeIncidents: number;
  openMaintenance: number;
  binsOverfull: number;
}

export interface Zone {
  id: UUID;
  name: string;
  zoneType: ZoneType;
  cityId: UUID;
  /** GeoJSON string of the zone polygon — parse with JSON.parse when present */
  geojson: string | null;
}

// ---------------------------------------------------------------------------
// §7.2 Traffic
// ---------------------------------------------------------------------------

export interface TrafficReading {
  id?: UUID;
  sensorId: UUID;
  vehicleCount?: number;
  avgSpeedKph?: number;
  occupancyPct?: number;
  congestionLevel?: number;
  recordedAt: string;
}

export interface TrafficSensor {
  id: UUID;
  roadId?: UUID;
  name: string;
  location: LonLat | null;
  status: SensorStatus;
  latestReading?: TrafficReading | null;
}

export interface ZoneCongestion {
  zoneId: UUID;
  name: string;
  congestionLevel: number; // 0..1
  avgSpeedKph: number;
  trend: TrendDirection;
  updatedAt: string;
}

export interface CongestionPrediction {
  zoneId: UUID;
  horizonMinute: number;
  predictedCongestion: number; // 0..1
  confidence: number; // 0..1
}

// ---------------------------------------------------------------------------
// §7.3 Roads & maintenance
// ---------------------------------------------------------------------------

export interface Road {
  id: UUID;
  zoneId?: UUID;
  name: string;
  roadClass: RoadClass;
  conditionScore: number; // 0..100
  /** GeoJSON string of the road line geometry — parse with JSON.parse when present */
  geojson: string | null;
  openMaintenanceRequests?: number;
}

export interface MaintenanceRequest {
  id: UUID;
  title: string;
  description?: string | null;
  priority: MaintenancePriority;
  status: MaintenanceStatus;
  reportedBy?: string;
  scheduledDate?: string | null;
  resolvedAt?: string | null;
  roadId?: UUID | null;
  assetId?: UUID | null;
  roadName?: string | null;
  createdAt?: string;
}

export interface MaintenanceSummaryRow {
  /** dimension present per grouped row */
  roadClass?: RoadClass;
  priority?: MaintenancePriority;
  status: MaintenanceStatus;
  count: number;
}

// ---------------------------------------------------------------------------
// §7.4 Drainage
// ---------------------------------------------------------------------------

export interface DrainageNetwork {
  id: UUID;
  zoneId?: UUID;
  name: string;
  capacityM3PerHour: number;
  networkClass: DrainageNetworkClass;
}

export interface WaterLevelReading {
  id?: UUID;
  sensorId: UUID;
  waterLevelCm: number;
  flowRateLps?: number;
  rainfallMm?: number;
  riskLevel?: WaterRisk | null;
  recordedAt: string;
}

export interface DrainageSensor {
  id: UUID;
  networkId?: UUID;
  name: string;
  location: LonLat | null;
  status: DrainageSensorStatus;
  latestReading?: WaterLevelReading | null;
}

export interface WaterloggingPrediction {
  zoneId: UUID;
  risk: WaterRisk;
  maxLevelCm: number;
  affectedRoads: UUID[];
  confidence: number;
}

// ---------------------------------------------------------------------------
// §7.5 Energy
// ---------------------------------------------------------------------------

export interface ElectricityGrid {
  id: UUID;
  zoneId?: UUID;
  name: string;
  capacityKw: number;
  lossPct: number;
  zoneName?: string;
  latestReading?: GridReading | null;
}

export interface GridReading {
  id?: UUID;
  gridId: UUID;
  consumptionKw: number;
  voltage?: number;
  outageReported?: boolean;
  recordedAt: string;
}

export interface EnergyZoneSummary {
  zoneId: UUID;
  name: string;
  currentKw: number;
  peakKw24h: number;
  outages24h: number;
  trend: TrendDirection;
}

export interface InfrastructureAsset {
  id: UUID;
  zoneId?: UUID;
  assetType: AssetType;
  name: string;
  location: LonLat | null;
  conditionScore: number;
  installedYear?: number;
  status: AssetStatus;
}

// ---------------------------------------------------------------------------
// §7.6 Waste
// ---------------------------------------------------------------------------

export interface WasteBin {
  id: UUID;
  zoneId?: UUID;
  name: string;
  location: LonLat | null;
  fillLevelPct: number;
  lastCollectedAt?: string | null;
}

export interface WasteRouteStop {
  binId: UUID;
  name: string;
  lat: number;
  lon: number;
  fillLevel: number;
  order: number;
}

export interface WasteRoute {
  id: UUID;
  name: string;
  zoneId: UUID;
  vehicleId?: UUID | null;
  stops: WasteRouteStop[];
  totalDistanceKm: number;
  estimatedDurationMin: number;
  status: WasteRouteStatus;
}

// ---------------------------------------------------------------------------
// §7.7 Emergency
// ---------------------------------------------------------------------------

export interface EmergencyIncident {
  id: UUID;
  zoneId?: UUID;
  name: string;
  incidentType: IncidentType;
  severity: number; // 1..5
  location: LonLat | null;
  description?: string | null;
  status: IncidentStatus;
  reportedAt: string;
  resolvedAt?: string | null;
  zoneName?: string;
}

export interface EmergencyVehicle {
  id: UUID;
  name: string;
  vehicleType: VehicleType;
  status: VehicleStatus;
  station: LonLat | null;
  headingDeg?: number;
}

export interface Dispatch {
  id: UUID;
  incidentId: UUID;
  vehicleId: UUID;
  routeGeojson?: string | null;
  distanceKm: number;
  etaMin: number;
  status: DispatchStatus;
  dispatchedAt: string;
}

// ---------------------------------------------------------------------------
// §7.9 Citizen reports
// ---------------------------------------------------------------------------

export interface CitizenReport {
  id: UUID;
  zoneId?: UUID | null;
  category: ReportCategory;
  description: string;
  location: LonLat | null;
  status: ReportStatus;
  upvotes: number;
  reporterName?: string;
  createdAt?: string;
  zoneName?: string;
}

// ---------------------------------------------------------------------------
// §7.10 Simulation (flagship)
// ---------------------------------------------------------------------------

export interface SimulationRunRequest {
  scenario: Scenario;
  name: string;
  params: {
    intensityMm?: number;
    durationMinutes?: number;
    zoneId?: UUID;
    gridId?: UUID;
    criticality?: string;
  };
}

export interface CascadeNode {
  id: string;
  type: CascadeNodeType;
  label: string;
  zoneId?: UUID | null;
  payload?: Record<string, unknown>;
  startOffsetSec: number;
  severity?: number;
}

export interface CascadeEdge {
  from: string;
  to: string;
  relation: string;
  delaySec: number;
  probability: number;
}

export interface CascadeGraphData {
  nodes: CascadeNode[];
  edges: CascadeEdge[];
}

export interface ImpactSummary {
  zonesAffected: number;
  roadsImpacted: number;
  roadsWaterlogged?: number;
  incidentsRerouted?: number;
  citizenReportsEstimate?: number;
  totalNodes?: number;
}

export interface SimulationResult {
  simulationId: UUID;
  scenario: Scenario;
  status: SimulationStatus;
  /** aggregate duration of the cascade in seconds */
  timelineSeconds?: number;
  cascade: CascadeGraphData;
  impactSummary: ImpactSummary;
  name?: string;
  startedAt?: string;
  completedAt?: string;
}

export interface SimulationRunSummary {
  id?: UUID;
  simulationId?: UUID;
  name?: string;
  scenario: Scenario;
  status: SimulationStatus;
  startedAt?: string;
  completedAt?: string;
}

// ---------------------------------------------------------------------------
// §7.11 Digital twin
// ---------------------------------------------------------------------------

export interface TwinZoneState {
  zoneId: UUID;
  name: string;
  congestion: number; // 0..1
  floodRisk: number; // 0..1
  powerState: PowerState;
  binsOverfull: number;
  assetHealth?: number; // 0..100
}

export interface TwinTotals {
  activeIncidents: number;
  openMaintenance: number;
  binsOverfull: number;
  avgCongestion: number; // 0..1
  networkStatus: NetworkStatus;
  dataFreshness: string;
}

export interface TwinState {
  city: { id: UUID; name: string; flag?: string; country?: string };
  zones: TwinZoneState[];
  totals: TwinTotals;
  updatedAt: string;
}
