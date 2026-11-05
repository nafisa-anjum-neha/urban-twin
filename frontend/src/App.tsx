import { Route, Routes, useLocation } from 'react-router-dom';
import Sidebar from './components/Sidebar';
import Topbar from './components/Topbar';
import Overview from './pages/Overview';
import Traffic from './pages/Traffic';
import Roads from './pages/Roads';
import Drainage from './pages/Drainage';
import Energy from './pages/Energy';
import Waste from './pages/Waste';
import Emergency from './pages/Emergency';
import Simulation from './pages/Simulation';
import Citizen from './pages/Citizen';

const TITLES: Record<string, { title: string; sub: string }> = {
  '/': { title: 'Digital Twin Overview', sub: 'Live cross-system state of the city' },
  '/traffic': { title: 'Traffic', sub: 'Congestion now & 30-minute prediction' },
  '/roads': { title: 'Roads & Maintenance', sub: 'Condition scoring and repair workflow' },
  '/drainage': { title: 'Drainage', sub: 'Water levels and waterlogging risk' },
  '/energy': { title: 'Electricity', sub: 'Consumption, grids and outages' },
  '/waste': { title: 'Waste Collection', sub: 'Bin fill levels and route optimization' },
  '/emergency': { title: 'Emergency', sub: 'Incidents, vehicles and dispatch' },
  '/simulation': { title: 'Cascade Simulator', sub: 'Cross-infrastructure failure propagation' },
  '/citizen': { title: 'Citizen Reports', sub: 'Crowd-sourced infrastructure issues' },
};

export default function App() {
  const { pathname } = useLocation();
  const head = TITLES[pathname] ?? { title: 'UrbanTwin', sub: '' };

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <Topbar />
        <div className="border-b border-slate-800 bg-slate-950/40 px-6 py-3">
          <h1 className="text-base font-semibold tracking-tight text-white">{head.title}</h1>
          <p className="text-xs text-slate-500">{head.sub}</p>
        </div>
        <main className="flex-1 overflow-y-auto px-6 py-5">
          <Routes>
            <Route path="/" element={<Overview />} />
            <Route path="/traffic" element={<Traffic />} />
            <Route path="/roads" element={<Roads />} />
            <Route path="/drainage" element={<Drainage />} />
            <Route path="/energy" element={<Energy />} />
            <Route path="/waste" element={<Waste />} />
            <Route path="/emergency" element={<Emergency />} />
            <Route path="/simulation" element={<Simulation />} />
            <Route path="/citizen" element={<Citizen />} />
          </Routes>
        </main>
      </div>
    </div>
  );
}
