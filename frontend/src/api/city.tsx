import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import { listCities } from './cities';
import type { City } from '../types';

const STORAGE_KEY = 'urbantwin.cityId';
const DEFAULT_CITY_SLUG = 'dhaka';

interface CityContextValue {
  cities: City[];
  /** currently selected city (null until loaded) */
  city: City | null;
  /** selected cityId — slug if possible, else uuid, else null */
  cityId: string | null;
  /** switch the active city: refetches every query + the live feed */
  setCity: (c: City) => void;
  switchById: (id: string, fallbackSlug?: string) => void;
}

const CityContext = createContext<CityContextValue>({
  cities: [],
  city: null,
  cityId: null,
  setCity: () => undefined,
  switchById: () => undefined,
});

/** Synchronous read used by the axios interceptor below. */
let activeCityId: string | null = bootCityId();

/**
 * Appends `?cityId=<selected>` to every outgoing API request automatically,
 * so domain modules stay unchanged. An explicit cityId param always wins
 * (callers that already scope by city keep their behaviour).
 */
api.interceptors.request.use((config) => {
  if (activeCityId && config.params && 'cityId' in config.params) return config;
  if (activeCityId) config.params = { ...config.params, cityId: activeCityId };
  return config;
});

/** `?city=` in the URL wins, then localStorage, then the default city. */
function bootCityId(): string {
  const q = new URLSearchParams(window.location.search).get('city');
  if (q) return q;
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved) return saved;
  } catch {
    /* private mode */
  }
  return DEFAULT_CITY_SLUG;
}

export function CityProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [cities, setCities] = useState<City[]>([]);
  const [rawId, setRawId] = useState<string>(() => bootCityId());

  // Load the city catalogue once; attempts to resolve the stored id against
  // it (a stale slug/uuid falls back to the default city).
  useEffect(() => {
    let cancelled = false;
    listCities()
      .then((all) => {
        if (cancelled || all.length === 0) return;
        setCities(all);
        const id = bootCityId();
        const match = all.find((c) => c.slug === id || c.id === id);
        activeCityId = (match?.slug ?? all[0].slug);
        setRawId(match?.slug ?? all[0].slug);
      })
      .catch(() => {
        /* offline: interceptor keeps whatever boot resolution chose */
        activeCityId = bootCityId();
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    activeCityId = rawId;
  }, [rawId]);

  const setCity = useCallback(
    (c: City) => {
      // 1. the interceptor must see the new city BEFORE any refetch fires
      activeCityId = c.slug;
      setRawId(c.slug);
      try {
        localStorage.setItem(STORAGE_KEY, c.slug);
      } catch {
        /* ignore */
      }
      // 2. drop in-flight fetches of the old city so they can't land in the
      //    cache after the switch, then wipe cached data of all query keys
      queryClient.cancelQueries().catch(() => undefined);
      queryClient.clear();
    },
    [queryClient],
  );

  const setActive = useCallback((slug: string) => {
    activeCityId = slug;
    setRawId(slug);
  }, []);

  const switchById = useCallback(
    (id: string, fallbackSlug?: string) => {
      const c = cities.find((x) => x.slug === id || x.id === id);
      if (c) setCity(c);
      else {
        const slug = fallbackSlug ?? id;
        activeCityId = slug;
        try {
          localStorage.setItem(STORAGE_KEY, slug);
        } catch {
          /* ignore */
        }
        setRawId(slug);
        queryClient.cancelQueries().catch(() => undefined);
        queryClient.clear();
      }
    },
    [cities, setCity, setActive, queryClient],
  );

  const city = useMemo(
    () => cities.find((c) => c.slug === rawId || c.id === rawId) ?? null,
    [cities, rawId],
  );

  return (
    <CityContext.Provider
      value={{ cities, city, cityId: rawId ?? null, setCity, switchById }}
    >
      {children}
    </CityContext.Provider>
  );
}

export function useCity(): CityContextValue {
  return useContext(CityContext);
}
