import type { ReactNode } from 'react';

const ACCENTS: Record<string, { text: string; ring: string }> = {
  emerald: { text: 'text-emerald-300', ring: 'border-emerald-800/60' },
  amber: { text: 'text-amber-300', ring: 'border-amber-800/60' },
  red: { text: 'text-red-300', ring: 'border-red-800/60' },
  sky: { text: 'text-sky-300', ring: 'border-sky-800/60' },
  violet: { text: 'text-violet-300', ring: 'border-violet-800/60' },
  slate: { text: 'text-slate-300', ring: 'border-slate-700' },
};

export default function KpiCard({
  label,
  value,
  sub,
  icon,
  accent = 'slate',
  href,
  onClick,
}: {
  label: string;
  value: ReactNode;
  sub?: ReactNode;
  icon?: string;
  accent?: keyof typeof ACCENTS | string;
  href?: string;
  onClick?: () => void;
}) {
  const a = ACCENTS[accent] ?? ACCENTS.slate;
  const inner = (
    <div
      className={[
        'rounded-2xl border bg-slate-900/50 shadow-card px-5 py-4 h-full transition-colors',
        href || onClick ? 'hover:bg-slate-900 cursor-pointer' : '',
        a.ring,
      ].join(' ')}
    >
      <div className="flex items-center justify-between gap-3">
        <span className="text-[11px] uppercase tracking-wider text-slate-500 font-medium">
          {label}
        </span>
        {icon && <span className="text-lg leading-none">{icon}</span>}
      </div>
      <div className={`mt-2 text-2xl font-bold ${a.text} tracking-tight`}>{value}</div>
      {sub && <div className="mt-1 text-[11px] text-slate-500">{sub}</div>}
    </div>
  );

  if (href) {
    return (
      <a href={href} className="block focus:outline-none">
        {inner}
      </a>
    );
  }
  return onClick ? <button type="button" onClick={onClick} className="w-full text-left">{inner}</button> : inner;
}
