const WATER_THRESHOLDS: { cm: number; risk: string; color: string }[] = [
  { cm: 0, risk: 'low', color: '#34d399' },
  { cm: 50, risk: 'moderate', color: '#fbbf24' },
  { cm: 100, risk: 'high', color: '#fb923c' },
  { cm: 150, risk: 'severe', color: '#f87171' },
];

export const MAX_GAUGE_CM = 200;

export function riskForCm(cm: number): { risk: string; color: string } {
  let out = WATER_THRESHOLDS[0];
  for (const t of WATER_THRESHOLDS) if (cm >= t.cm) out = t;
  return { risk: out.risk, color: out.color };
}

/** Compact horizontal water-level gauge against the service-depth scale (cm). */
export default function RiskGauge({
  valueCm,
  label,
  sub,
  confidence,
}: {
  valueCm: number;
  label: string;
  sub?: string;
  confidence?: number;
}) {
  const { risk, color } = riskForCm(valueCm);
  const pctW = Math.min(100, (valueCm / MAX_GAUGE_CM) * 100);

  return (
    <div className="rounded-xl border border-slate-800 bg-slate-950/50 px-4 py-3">
      <div className="flex items-baseline justify-between gap-2">
        <span className="text-xs font-medium text-slate-300 truncate">{label}</span>
        <span className="text-sm font-semibold" style={{ color }}>
          {Math.round(valueCm)} cm
        </span>
      </div>
      {sub && <div className="text-[11px] text-slate-500 mt-0.5">{sub}</div>}
      <div className="relative mt-2 h-2.5 rounded-full bg-slate-800 overflow-hidden">
        <div
          className="h-full rounded-full transition-all duration-500"
          style={{ width: `${pctW}%`, backgroundColor: color }}
        />
        {WATER_THRESHOLDS.slice(1).map((t) => (
          <div
            key={t.risk}
            title={`${t.risk} @ ${t.cm} cm`}
            className="absolute top-0 h-full w-px bg-slate-600"
            style={{ left: `${(t.cm / MAX_GAUGE_CM) * 100}%` }}
          />
        ))}
      </div>
      <div className="mt-1.5 flex items-center justify-between text-[10px] uppercase tracking-wide text-slate-500">
        <span style={{ color }}>{risk}</span>
        {confidence !== undefined && <span>conf {Math.round(confidence * 100)}%</span>}
      </div>
    </div>
  );
}
