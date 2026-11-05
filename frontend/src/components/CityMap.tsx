import { useEffect } from 'react';
import { MapContainer, TileLayer, useMap } from 'react-leaflet';
import type { LatLngBoundsExpression } from 'leaflet';
import { CITY_FALLBACK_CENTER, type LatLngTuple } from '../hooks/geo';
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

/** Leaflet map with OpenStreetMap raster tiles (contract §9) on the dark theme. */
export default function CityMap({
  height = 420,
  center = CITY_FALLBACK_CENTER,
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
  return (
    <MapContainer
      center={center}
      zoom={zoom}
      style={{ height, width: '100%', background: '#0b1220' }}
      scrollWheelZoom
      className="rounded-xl border border-slate-800 z-0"
    >
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      {bounds && <FitBounds bounds={bounds} />}
      {children}
    </MapContainer>
  );
}
