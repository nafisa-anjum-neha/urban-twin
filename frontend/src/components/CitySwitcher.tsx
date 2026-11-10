import { useEffect, useRef, useState } from 'react';
import { useCity } from '../api/city';

/**
 * Country/city selector for the topbar: flag + name dropdown over the full
 * catalogue (`GET /cities`). Selecting a city remounts every data query
 * (queryClient.clear) and re-opens the twin SSE for that city.
 */
export default function CitySwitcher() {
  const { cities, city, setCity, cityId } = useCity();
  const [open, setOpen] = useState(false);
  const boxRef = useRef<HTMLDivElement>(null);

  // Close on outside click / Esc
  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (!boxRef.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDoc);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const currentFlag = city?.flag ?? '🏙️';
  const currentName = city?.name ?? '…';

  return (
    <div ref={boxRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="listbox"
        aria-expanded={open}
        title="Choose the city this twin shows"
        className="inline-flex items-center gap-2 rounded-xl border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 transition-colors hover:border-slate-400 dark:border-slate-800 dark:bg-slate-950/60 dark:text-slate-100 dark:hover:border-slate-600"
      >
        <span className="text-base leading-none">{currentFlag}</span>
        <span className="max-w-[9rem] truncate">{currentName}</span>
        <span className="text-[10px] text-slate-400 dark:text-slate-500">▼</span>
      </button>

      {open && (
        <div
          role="listbox"
          aria-label="Choose city"
          className="absolute right-0 top-[calc(100%+6px)] z-50 max-h-80 w-60 overflow-y-auto rounded-xl border border-slate-200 bg-white p-1.5 shadow-xl dark:border-slate-800 dark:bg-slate-950"
        >
          <div className="px-2.5 pb-1.5 pt-1 text-[11px] font-semibold uppercase tracking-wide text-slate-400">
            Countries · Cities
          </div>
          {cities.length === 0 && (
            <div className="px-2.5 py-2 text-sm text-slate-500">Loading cities…</div>
          )}
          {cities.map((c) => (
            <button
              key={c.id}
              type="button"
              role="option"
              aria-selected={c.slug === cityId}
              onClick={() => {
                setCity(c);
                setOpen(false);
              }}
              className={[
                'flex w-full items-center gap-3 rounded-lg px-2.5 py-2 text-left text-sm transition-colors',
                c.slug === cityId
                  ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300'
                  : 'text-slate-700 hover:bg-slate-100 dark:text-slate-200 dark:hover:bg-slate-900',
              ].join(' ')}
            >
              <span className="w-6 text-center text-base leading-none">{c.flag ?? '🏙️'}</span>
              <span className="min-w-0 flex-1">
                <span className="block truncate font-medium">{c.name}</span>
                <span className="block truncate text-[11px] text-slate-400">
                  {c.country}
                  {c.zoneCount ? ` · ${c.zoneCount} zones` : ''}
                </span>
              </span>
              {c.slug === cityId && (
                <span className="text-xs text-emerald-500">●</span>
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
