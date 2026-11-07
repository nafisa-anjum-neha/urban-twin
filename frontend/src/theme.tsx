import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';

/**
 * White/Black theme switch. Defaults to WHITE (light); the choice persists in
 * localStorage and is applied as a `dark` class on <html> which drives every
 * token in index.css (Tailwind slate ramp + chart/leaflet variables).
 */

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'urbantwin.theme';

function initial(): Theme {
  // 1. explicit URL override(?theme=dark / ?theme=light) — useful for demos
  try {
    const q = new URLSearchParams(window.location.search).get('theme');
    if (q === 'dark' || q === 'light') return q;
    // 2. persisted choice
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'dark' || saved === 'light') return saved;
  } catch {
    /* storage unavailable — stay default */
  }
  return 'light';
}

interface ThemeCtx {
  theme: Theme;
  toggle: () => void;
  /** Chart/SVG palette for the active theme (recharts accepts style/props colors). */
  palette: Palette;
}

export interface Palette {
  grid: string;
  axis: string;
  tick: string;
  tipBg: string;
  tipBorder: string;
  /** fill behind geo "slots" (route dots, gauge arrows) punched out of colored areas */
  slot: string;
  ink: string;
  inkMuted: string;
}

const PALETTES: Record<Theme, Palette> = {
  light: {
    grid: '#e2e8f0',
    axis: '#cbd5e1',
    tick: '#64748b',
    tipBg: '#ffffff',
    tipBorder: '#cbd5e1',
    slot: '#ffffff',
    ink: '#0f172a',
    inkMuted: '#94a3b8',
  },
  dark: {
    grid: '#1e293b',
    axis: '#334155',
    tick: '#94a3b8',
    tipBg: '#0f172a',
    tipBorder: '#334155',
    slot: '#0f172a',
    ink: '#e2e8f0',
    inkMuted: '#64748b',
  },
};

const Ctx = createContext<ThemeCtx | null>(null);

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [theme, setTheme] = useState<Theme>(initial);

  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark');
    try {
      localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      /* ignore */
    }
  }, [theme]);

  const toggle = useCallback(() => {
    setTheme((t) => (t === 'light' ? 'dark' : 'light'));
  }, []);

  const value = useMemo<ThemeCtx>(
    () => ({ theme, toggle, palette: PALETTES[theme] }),
    [theme, toggle],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useTheme(): ThemeCtx {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useTheme must be used within <ThemeProvider>');
  return ctx;
}
