import type { ReactNode } from 'react';

export default function Panel({
  title,
  subtitle,
  right,
  children,
  className = '',
  bodyClassName = '',
}: {
  title?: string;
  subtitle?: string;
  right?: ReactNode;
  children: ReactNode;
  className?: string;
  bodyClassName?: string;
}) {
  return (
    <section
      className={[
        'rounded-2xl border border-slate-800 bg-slate-900/50 shadow-card overflow-hidden flex flex-col min-w-0',
        className,
      ].join(' ')}
    >
      {(title || right) && (
        <div className="flex items-start justify-between gap-4 px-5 pt-4 pb-3">
          <div className="min-w-0">
            {title && (
              <h2 className="text-sm font-semibold text-slate-50 tracking-tight">{title}</h2>
            )}
            {subtitle && <p className="text-[11px] text-slate-500 mt-0.5">{subtitle}</p>}
          </div>
          {right && <div className="shrink-0">{right}</div>}
        </div>
      )}
      <div className={['px-5 pb-5 flex-1 min-w-0', bodyClassName].join(' ')}>{children}</div>
    </section>
  );
}
