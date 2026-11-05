import { NavLink } from 'react-router-dom';

const NAV = [
  { to: '/', label: 'Digital Twin', icon: '🌐' },
  { to: '/traffic', label: 'Traffic', icon: '🚦' },
  { to: '/roads', label: 'Roads', icon: '🛣️' },
  { to: '/drainage', label: 'Drainage', icon: '🌧️' },
  { to: '/energy', label: 'Energy', icon: '⚡' },
  { to: '/waste', label: 'Waste', icon: '🗑️' },
  { to: '/emergency', label: 'Emergency', icon: '🚑' },
  { to: '/simulation', label: 'Simulation', icon: '🌀' },
  { to: '/citizen', label: 'Citizen Reports', icon: '🗳️' },
];

export default function Sidebar() {
  return (
    <aside className="w-60 shrink-0 border-r border-slate-800 bg-slate-950/80 flex flex-col">
      <div className="px-5 py-5 border-b border-slate-800">
        <div className="flex items-center gap-2.5">
          <div className="h-9 w-9 rounded-xl bg-gradient-to-br from-emerald-400 to-sky-500 flex items-center justify-center text-slate-950 font-black text-lg">
            U
          </div>
          <div>
            <div className="text-white font-semibold tracking-tight leading-none">
              UrbanTwin
            </div>
            <div className="text-[11px] text-slate-500 mt-1">
              Bengaluru digital twin
            </div>
          </div>
        </div>
      </div>

      <nav className="flex-1 overflow-y-auto px-3 py-4 space-y-1">
        {NAV.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            className={({ isActive }) =>
              [
                'flex items-center gap-3 rounded-xl px-3 py-2 text-sm font-medium transition-colors',
                isActive
                  ? 'bg-slate-800 text-white shadow-card'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900',
              ].join(' ')
            }
          >
            <span className="text-base w-5 text-center">{item.icon}</span>
            {item.label}
          </NavLink>
        ))}
      </nav>

      <div className="px-5 py-4 border-t border-slate-800 text-[11px] text-slate-600">
        Contract v1.0 · API 8080 · ML 8000
      </div>
    </aside>
  );
}
