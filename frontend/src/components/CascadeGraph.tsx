import { useMemo } from 'react';
import type { CascadeEdge, CascadeGraphData, CascadeNode } from '../types';

/**
 * Layered cascade graph rendered with plain SVG (no graph library):
 * triggers at the top, downstream systems on lower rows (longest-path layering),
 * edges annotated with propagation probability. Nodes/edges fade in as the
 * animated timeline playback (simulated seconds) reaches them.
 */

const TYPE_COLORS: Record<string, string> = {
  TRIGGER: '#f87171',
  DRAINAGE: '#38bdf8',
  ROAD: '#fbbf24',
  TRAFFIC: '#fb923c',
  ENERGY: '#facc15',
  INCIDENT: '#ef4444',
  REPORT: '#a78bfa',
  MITIGATION: '#34d399',
};

const ROW_H = 108;
const COL_W = 150;
const NODE_R = 26;
const PAD_X = 60;
const PAD_Y = 46;

interface PlacedNode extends CascadeNode {
  depth: number;
  x: number;
  y: number;
}

function layout(nodes: CascadeNode[], edges: CascadeEdge[]): { placed: PlacedNode[]; width: number; height: number } {
  const byId = new Map(nodes.map((n) => [n.id, n]));
  const incoming = new Map<string, number>();
  for (const e of edges) incoming.set(e.to, (incoming.get(e.to) ?? 0) + 1);

  // longest-path layering (safe against cycles via visit guard)
  const depth = new Map<string, number>();
  const visit = (id: string, guard: Set<string>): number => {
    if (depth.has(id)) return depth.get(id)!;
    if (guard.has(id)) return 0;
    guard.add(id);
    const parents = edges.filter((e) => e.to === id).map((e) => e.from);
    let maxParent = -1;
    for (const p of parents) {
      if (byId.has(p)) maxParent = Math.max(maxParent, visit(p, guard));
    }
    guard.delete(id);
    const d = maxParent < 0 ? 0 : maxParent + 1;
    depth.set(id, d);
    return d;
  };
  for (const n of nodes) visit(n.id, new Set());

  const rows = new Map<number, CascadeNode[]>();
  for (const n of nodes) {
    const d = depth.get(n.id) ?? 0;
    if (!rows.has(d)) rows.set(d, []);
    rows.get(d)!.push(n);
  }

  const maxDepth = Math.max(0, ...rows.keys());
  const height = rowY(maxDepth) + NODE_R + 34;
  let widest = 0;

  const placed: PlacedNode[] = [];
  for (const [d, rowNodes] of [...rows.entries()].sort((a, b) => a[0] - b[0])) {
    rowNodes.sort((a, b) => a.startOffsetSec - b.startOffsetSec);
    const span = (rowNodes.length - 1) * COL_W;
    const startX = PAD_X + (Math.max(0, maxSpan(rows)) - span) / 2;
    rowNodes.forEach((n, i) => {
      placed.push({ ...n, depth: d, x: startX + i * COL_W, y: rowY(d) });
    });
    widest = Math.max(widest, startX + span + PAD_X + NODE_R + 30);
  }

  function maxSpan(m: Map<number, CascadeNode[]>): number {
    let max = 0;
    for (const arr of m.values()) max = Math.max(max, (arr.length - 1) * COL_W);
    return max;
  }
  function rowY(d: number): number {
    return PAD_Y + d * ROW_H;
  }

  const width = Math.max(560, widest);
  return { placed, width, height: Math.max(height, rowY(maxDepth) + PAD_Y) };
}

export default function CascadeGraph({
  cascade,
  visibleSec = Infinity,
}: {
  cascade: CascadeGraphData;
  /** simulated seconds reached by playback; nodes appear as their startOffsetSec elapses */
  visibleSec?: number;
}) {
  const { placed, width, height } = useMemo(() => layout(cascade.nodes, cascade.edges), [cascade]);
  const posById = useMemo(() => new Map(placed.map((p) => [p.id, p])), [placed]);

  const visibleCount = placed.filter((p) => p.startOffsetSec <= visibleSec).length;
  const visibleIds = useMemo(
    () => new Set(placed.filter((p) => p.startOffsetSec <= visibleSec).map((p) => p.id)),
    [placed, visibleSec],
  );

  return (
    <div className="overflow-x-auto rounded-xl border border-slate-800 bg-[repeating-linear-gradient(135deg,rgba(15,23,42,0.4)_0px,rgba(15,23,42,0.4)_12px,rgba(2,6,23,0.4)_12px,rgba(2,6,23,0.4)_24px)]">
      <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Cascade graph">
        {/* edges */}
        <svg />
        <defs>
          <marker id="cascade-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
            <path d="M 0 0 L 10 5 L 0 10 z" fill="#475569" />
          </marker>
        </defs>

        {cascade.edges.map((e) => {
          const a = posById.get(e.from);
          const b = posById.get(e.to);
          if (!a || !b) return null;
          const edgeVisible = visibleIds.has(e.to); // edge lights up when the target fires
          const opacity = edgeVisible ? Math.min(0.95, 0.35 + 0.65 * (e.probability ?? 1)) : 0.07;
          const dx = b.x - a.x;
          const dy = b.y - a.y;
          const len = Math.hypot(dx, dy) || 1;
          const ux = dx / len;
          const uy = dy / len;
          const x1 = a.x + ux * (NODE_R + 4);
          const y1 = a.y + uy * (NODE_R + 4);
          const x2 = b.x - ux * (NODE_R + 6);
          const y2 = b.y - uy * (NODE_R + 6);
          const mx = (x1 + x2) / 2;
          const my = (y1 + y2) / 2;
          return (
            <g key={`${e.from}->${e.to}`} className={edgeVisible ? 'animate-fade-in' : undefined}>
              <line
                x1={x1}
                y1={y1}
                x2={x2}
                y2={y2}
                stroke={edgeVisible ? '#64748b' : '#334155'}
                strokeWidth={edgeVisible ? 1.6 : 1}
                strokeDasharray="5 4"
                markerEnd="url(#cascade-arrow)"
                opacity={opacity}
              />
              <text
                x={mx}
                y={my - 5}
                textAnchor="middle"
                fontSize={10}
                fill={edgeVisible ? '#cbd5e1' : '#475569'}
                opacity={opacity}
              >
                {`${Math.round((e.probability ?? 0) * 100)}% ${e.relation ?? ''}`}
              </text>
            </g>
          );
        })}

        {/* nodes */}
        {placed.map((n) => {
          const color = TYPE_COLORS[n.type] ?? '#94a3b8';
          const visible = n.startOffsetSec <= visibleSec;
          const r = NODE_R - (1 - Math.min(1, n.severity ?? 0.6)) * 6;
          return (
            <g key={n.id} transform={`translate(${n.x}, ${n.y})`} opacity={visible ? 1 : 0} className={visible ? 'animate-fade-in' : undefined}>
              {visible && n.severity !== undefined && (
                <circle r={r + 7} fill="none" stroke={color} strokeOpacity={0.25} strokeWidth={2} />
              )}
              <circle r={r} fill="#0f172a" stroke={color} strokeWidth={2.5} />
              <text y="5" textAnchor="middle" fontSize={11} fontWeight={700} fill={color}>
                {shortLabel(n)}
              </text>
              <text
                y={r + 16}
                textAnchor="middle"
                fontSize={10.5}
                fill="#e2e8f0"
              >
                {clip(n.label, 22)}
              </text>
              <text y={r + 29} textAnchor="middle" fontSize={9.5} fill="#64748b">
                {n.type.toLowerCase()} · +{fmtOffset(n.startOffsetSec)}
              </text>
            </g>
          );
        })}

        <text x={16} y={20} fontSize={11} fill="#475569">
          {visibleCount}/{placed.length} cascade nodes triggered
        </text>
      </svg>
    </div>
  );
}

function shortLabel(n: CascadeNode): string {
  const words = n.type.split('');
  return words.slice(0, 2).join('');
}

function clip(s: string, max: number): string {
  return s && s.length > max ? `${s.slice(0, max - 1)}…` : s ?? '';
}

function fmtOffset(sec: number): string {
  if (sec < 60) return `${sec}s`;
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return s === 0 ? `${m}m` : `${m}m${String(s).padStart(2, '0')}s`;
}
