import type {
  GeoFeature,
  GeoFeatureCollection,
  GeoGeometry,
} from '../types';

/** [lat, lng] positions used by Leaflet */
export type LatLngTuple = [number, number];

/**
 * Geometry fields arrive either as GeoJSON strings (`geojson` column) or as
 * parsed objects. Normalise to an object.
 */
export function parseGeometry(raw: unknown): GeoGeometry | null {
  if (!raw) return null;
  if (typeof raw === 'string') {
    try {
      const parsed = JSON.parse(raw);
      if (parsed && typeof parsed.type === 'string' && 'coordinates' in parsed) {
        return parsed as GeoGeometry;
      }
      return null;
    } catch {
      return null;
    }
  }
  if (typeof raw === 'object' && 'type' in (raw as object) && 'coordinates' in (raw as object)) {
    return raw as GeoGeometry;
  }
  return null;
}

/** Convert a [lon, lat] pair to [lat, lng] for Leaflet. */
export function lonLatToLatLng(lonLat: readonly [number, number] | number[]): LatLngTuple {
  return [lonLat[1], lonLat[0]];
}

/** Convert GeoJSON position lists ([lon, lat]...) to Leaflet [lat, lng] rings. */
export function geometryToLatLng(geometry: GeoGeometry | null): LatLngTuple[] {
  if (!geometry) return [];
  const c = geometry.coordinates;
  if (geometry.type === 'Point') {
    const p = c as number[];
    return [[p[1], p[0]]];
  }
  if (geometry.type === 'LineString') {
    return (c as number[][]).map((p) => [p[1], p[0]] as LatLngTuple);
  }
  if (geometry.type === 'Polygon') {
    // take the outer ring
    const rings = c as number[][][];
    return (rings[0] ?? []).map((p) => [p[1], p[0]] as LatLngTuple);
  }
  // MultiPolygon: first polygon outer ring
  const polys = c as unknown as number[][][][];
  const outer = polys[0]?.[0] ?? [];
  return outer.map((p) => [p[1], p[0]] as LatLngTuple);
}

/** Rough centroid of any geometry (used for zone dots / labels). */
export function featureCenter(feature: GeoFeature): LatLngTuple {
  const pts = geometryToLatLng(parseGeometry(feature.geometry));
  if (pts.length === 0) return [12.9716, 77.5946]; // Bengaluru fallback
  const lat = pts.reduce((s, p) => s + p[0], 0) / pts.length;
  const lng = pts.reduce((s, p) => s + p[1], 0) / pts.length;
  return [lat, lng];
}

/** Bounding box [[minLat,minLng],[maxLat,maxLng]] of a FeatureCollection. */
export function fcBounds(fc: GeoFeatureCollection | undefined | null): LatLngTuple[] | null {
  if (!fc || fc.features.length === 0) return null;
  let minLat = Infinity;
  let minLng = Infinity;
  let maxLat = -Infinity;
  let maxLng = -Infinity;
  for (const f of fc.features) {
    for (const [lat, lng] of geometryToLatLng(parseGeometry(f.geometry))) {
      if (lat < minLat) minLat = lat;
      if (lng < minLng) minLng = lng;
      if (lat > maxLat) maxLat = lat;
      if (lng > maxLng) maxLng = lng;
    }
  }
  if (!Number.isFinite(minLat)) return null;
  return [
    [minLat, minLng],
    [maxLat, maxLng],
  ];
}

export const CITY_FALLBACK_CENTER: LatLngTuple = [12.9716, 77.5946];

/** Diverging congestion/risk scale: emerald → amber → red (0..1). */
export function riskColor(v: number | null | undefined): string {
  const x = Math.min(1, Math.max(0, v ?? 0));
  if (x >= 0.66) return '#f87171'; // red-400
  if (x >= 0.33) return '#fbbf24'; // amber-400
  return '#34d399'; // emerald-400
}

export function riskLabel(v: number | null | undefined): string {
  const x = Math.min(1, Math.max(0, v ?? 0));
  if (x >= 0.66) return 'High';
  if (x >= 0.33) return 'Moderate';
  return 'Low';
}

/** Water-logging risk level → color */
export function waterRiskColor(risk: string | null | undefined): string {
  switch (risk) {
    case 'severe':
      return '#f87171';
    case 'high':
      return '#fb923c';
    case 'moderate':
      return '#fbbf24';
    case 'low':
      return '#34d399';
    default:
      return '#64748b';
  }
}
