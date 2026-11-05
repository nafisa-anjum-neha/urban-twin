import { useMemo } from 'react';
import { useQueries } from '@tanstack/react-query';
import { getLayer } from '../api/gis';
import type { GeoFeatureCollection, GisLayer } from '../types';

export interface MapDataResult {
  /** loaded FeatureCollection per requested layer */
  fc: Partial<Record<GisLayer, GeoFeatureCollection>>;
  isLoading: boolean;
  isError: boolean;
  /** layers that failed to load */
  failed: GisLayer[];
}

/**
 * Single shared hook for map data: loads one or more GIS FeatureCollections and
 * keeps the map code lean (contract §9: "single shared useMapData(layers) hook").
 */
export function useMapData(layers: GisLayer[]): MapDataResult {
  const layersKey = layers.join(',');
  const results = useQueries({
    queries: useMemo(
      () =>
        layersKey.split(',').map((layer) => ({
          queryKey: ['gis-layer', layer],
          queryFn: () => getLayer(layer as GisLayer),
          staleTime: 30_000,
          refetchInterval: 60_000,
          retry: 1,
        })),
      [layersKey],
    ),
  });

  const fc = useMemo(() => {
    const out: Partial<Record<GisLayer, GeoFeatureCollection>> = {};
    layersKey.split(',').forEach((layer, i) => {
      const r = results[i];
      if (r?.data) out[layer as GisLayer] = r.data;
    });
    return out;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [results.map((r) => r.dataUpdatedAt).join(','), layersKey]);

  const failed = useMemo(() => {
    const out: GisLayer[] = [];
    layersKey.split(',').forEach((layer, i) => {
      if (results[i]?.isError) out.push(layer as GisLayer);
    });
    return out;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [results.map((r) => r.isError).join(','), layersKey]);

  return {
    fc,
    isLoading: results.some((r) => r.isLoading),
    isError: failed.length === layersKey.split(',').length && layersKey.length > 0,
    failed,
  };
}
