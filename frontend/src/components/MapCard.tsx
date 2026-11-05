import type { ReactNode } from 'react';
import Panel from './Panel';

export default function MapCard({
  title,
  subtitle,
  legend,
  children,
  right,
}: {
  title: string;
  subtitle?: string;
  legend?: ReactNode;
  children: ReactNode;
  right?: ReactNode;
}) {
  return (
    <Panel
      title={title}
      subtitle={subtitle}
      right={
        right ?? (legend ? <div className="flex flex-wrap items-center gap-3 pt-1">{legend}</div> : undefined)
      }
      bodyClassName="!px-0 !pb-0"
    >
      <div className="px-5 pb-5">{children}</div>
    </Panel>
  );
}

export function ColorDot({ color, label }: { color: string; label: string }) {
  return (
    <span className="inline-flex items-center gap-1.5 text-[11px] text-slate-400">
      <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: color }} />
      {label}
    </span>
  );
}
