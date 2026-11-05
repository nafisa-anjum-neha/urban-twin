import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { API_URL } from '../api/client';
import { getTwinState } from '../api/twin';
import type { TwinState } from '../types';

export type LiveMode = 'connecting' | 'sse' | 'polling' | 'offline';

interface TwinLiveValue {
  snapshot: TwinState | null;
  mode: LiveMode;
  /** milliseconds of the last successful snapshot */
  lastUpdateMs: number | null;
  error: string | null;
}

const TwinLiveContext = createContext<TwinLiveValue>({
  snapshot: null,
  mode: 'connecting',
  lastUpdateMs: null,
  error: null,
});

/**
 * Live twin feed: starts fetch + SSE (§7.11 `GET /twin/live`); if the SSE
 * connection fails or goes stale, automatically falls back to polling
 * `GET /twin/state` every 10 s. WebSocket `/ws` is intentionally skipped.
 */
export function TwinLiveProvider({ children }: { children: ReactNode }) {
  const [snapshot, setSnapshot] = useState<TwinState | null>(null);
  const [mode, setMode] = useState<LiveMode>('connecting');
  const [lastUpdateMs, setLastUpdateMs] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const modeRef = useRef(mode);
  modeRef.current = mode;
  const lastMsgRef = useRef(0);

  const applySnapshot = useCallback((state: TwinState) => {
    setSnapshot(state);
    setLastUpdateMs(Date.now());
    if (state?.totals?.dataFreshness !== undefined) lastMsgRef.current = Date.now();
  }, []);

  useEffect(() => {
    let cancelled = false;
    let pollTimer: ReturnType<typeof setInterval> | null = null;

    const startPolling = () => {
      if (pollTimer) return;
      setMode((m) => (m === 'sse' ? m : 'polling'));
      pollTimer = setInterval(async () => {
        try {
          const s = await getTwinState();
          if (cancelled) return;
          applySnapshot(s);
          setMode('polling');
        } catch (e) {
          if (cancelled) return;
          setError(e instanceof Error ? e.message : 'twin state unavailable');
        }
      }, 10_000);
    };

    const stopPolling = () => {
      if (pollTimer) {
        clearInterval(pollTimer);
        pollTimer = null;
      }
    };

    // Seed with an initial poll-fetch so the UI has data before first SSE tick.
    getTwinState()
      .then((s) => {
        if (cancelled) return;
        applySnapshot(s);
      })
      .catch((e) => {
        if (cancelled) return;
        setError(e instanceof Error ? e.message : 'twin state unavailable');
        startPolling();
      });

    let es: EventSource | null = null;
    try {
      es = new EventSource(`${new URL(API_URL, window.location.href).toString().replace(/\/$/, '')}/twin/live`);
    } catch {
      es = null;
      startPolling();
    }

    const handleMessage = (msg: MessageEvent) => {
      if (cancelled) return;
      lastMsgRef.current = Date.now();
      setMode('sse');
      stopPolling();
      if (!msg.data) return; // ping / keepalive
      try {
        const parsed = JSON.parse(msg.data) as Partial<TwinState> & {
          type?: unknown;
          payload?: unknown;
        };
        if (
          parsed &&
          parsed.city &&
          parsed.zones &&
          parsed.totals
        ) {
          applySnapshot(parsed as TwinState);
        } else {
          // delta event → refresh full snapshot (cheap, 2s SSE tick)
          getTwinState()
            .then((s) => {
              if (cancelled) return;
              applySnapshot(s);
            })
            .catch(() => undefined);
        }
      } catch {
        // non-JSON heartbeat; ignore
      }
    };

    const handleError = () => {
      if (cancelled) return;
      // SSE dropped (backend down or no stream endpoint): fall back to polling.
      setTimeout(() => {
        if (cancelled) return;
        if (Date.now() - lastMsgRef.current > 12_000) startPolling();
      }, 12_000);
      if (Date.now() - lastMsgRef.current > 12_000) startPolling();
    };

    if (es) {
      es.onmessage = handleMessage;
      es.onerror = handleError;
    } else {
      startPolling();
    }

    return () => {
      cancelled = true;
      stopPolling();
      es?.close();
    };
  }, [applySnapshot]);

  return (
    <TwinLiveContext.Provider value={{ snapshot, mode, lastUpdateMs, error }}>
      {children}
    </TwinLiveContext.Provider>
  );
}

export function useTwinLive(): TwinLiveValue {
  return useContext(TwinLiveContext);
}
