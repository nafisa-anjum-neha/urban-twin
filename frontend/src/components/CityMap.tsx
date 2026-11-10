import { useEffect, useMemo } from 'react';
import { MapContainer, TileLayer, useMap } from 'react-leaflet';
import type { LatLngBoundsExpression } from 'leaflet';
import { CITY_FALLBACK_CENTER, fcBounds, type LatLngTuple } from '../hooks/geo';
import { useMapData } from '../hooks/useMapData';
import 'leaflet/dist/leaflet.css';

function FitBounds({ bounds }: { bounds: LatLngBoundsExpression | null }) {
  const map = useMap();
  useEffect(() => {
    if (bounds) {
      // slight delay lets geometry render first
      const t = setTimeout(() => map.fitBounds(bounds, { padding: [28, 28] }), 80);
      return () => clearTimeout(t);
    }
  }, [bounds, map]);
  return null;
}

/** Leaflet map with OpenStreetMap raster tiles (contract §9).
 *  Styling rides on theme CSS variables; dark mode additionally dims tiles.
 *
 *  Multi-city: when the caller doesn't pass `bounds` (undefined, i.e. most
 *  pages), the map auto-fits the current city's zone layer so every city
 *  centres correctly. Callers may pass `null` to opt out of auto-fit. */
export default function CityMap({
  height = 420,
  center: initialCenter,
  zoom = 11,
  bounds,
  children,
}: {
  height?: number;
  center?: LatLngTuple;
  zoom?: number;
  bounds?: LatLngBoundsExpression | null;
  children?: React.ReactNode;
}) {
  const autoFit = bounds === undefined;
  const { fc } = useMapData(autoFit ? ['zones'] : []);
  const autoBounds = useMemo(
    () => (autoFit ? fcBounds(fc['zones']) : null),
    [autoFit, fc['zones']],
  );
  const effective = autoFit && autoBounds ? autoBounds : (bounds ?? null);

  return (
    <MapContainer
      center={initialCenter ?? CITY_FALLBACK_CENTER}
      zoom={zoom}
      style={{ height, width: '100%', background: 'var(--map-bg)' }}
      scrollWheelZoom
      className="rounded-xl border border-slate-800 z-0"
    >
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <FitBounds bounds={effective} />
      {children}
    </MapContainer>
  );
}
