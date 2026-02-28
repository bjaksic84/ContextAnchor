import { useState, useEffect } from 'react';
import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { health as healthApi } from '../api/client';
import {
  MessageSquare,
  FileText,
  Key,
  ScrollText,
  Settings,
  LogOut,
  Anchor,
  ChevronLeft,
  ChevronRight,
  Activity,
} from 'lucide-react';

const navItems = [
  { to: '/chat', icon: MessageSquare, label: 'Chat' },
  { to: '/documents', icon: FileText, label: 'Documents' },
  { to: '/api-keys', icon: Key, label: 'API Keys' },
  { to: '/audit', icon: ScrollText, label: 'Audit Logs' },
  { to: '/settings', icon: Settings, label: 'Settings' },
];

export default function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);
  const [status, setStatus] = useState(null);

  useEffect(() => {
    healthApi.check().then(setStatus).catch(() => {});
  }, []);

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className="flex h-screen overflow-hidden">
      {/* Sidebar */}
      <aside
        className={`flex flex-col bg-sidebar text-white transition-all duration-200 ${
          collapsed ? 'w-16' : 'w-64'
        }`}
      >
        {/* Logo */}
        <div className="flex h-16 items-center gap-2 px-4 border-b border-white/10">
          <Anchor className="h-7 w-7 flex-shrink-0 text-brand-400" />
          {!collapsed && (
            <span className="text-lg font-bold tracking-tight truncate">ContextAnchor</span>
          )}
        </div>

        {/* Navigation */}
        <nav className="flex-1 py-4 space-y-1 px-2">
          {navItems.map(({ to, icon: Icon, label }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-sidebar-active text-white'
                    : 'text-gray-400 hover:bg-sidebar-hover hover:text-white'
                }`
              }
            >
              <Icon className="h-5 w-5 flex-shrink-0" />
              {!collapsed && <span>{label}</span>}
            </NavLink>
          ))}
        </nav>

        {/* Status indicator */}
        {status && !collapsed && (
          <div className="mx-3 mb-3 rounded-lg bg-sidebar-hover px-3 py-2.5 text-xs">
            <div className="flex items-center gap-2 text-gray-400 mb-1">
              <Activity className="h-3.5 w-3.5" />
              <span>System Status</span>
            </div>
            <div className="flex items-center gap-1.5">
              <span className={`h-2 w-2 rounded-full ${status.status === 'UP' ? 'bg-emerald-400' : 'bg-red-400'}`} />
              <span className="text-gray-300">{status.ai?.provider || 'unknown'}</span>
              <span className="text-gray-500">•</span>
              <span className="text-gray-400">{status.ai?.mode || ''}</span>
            </div>
          </div>
        )}

        {/* User & logout */}
        <div className="border-t border-white/10 p-3">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full bg-brand-600 text-sm font-semibold">
              {user?.fullName?.charAt(0)?.toUpperCase() || '?'}
            </div>
            {!collapsed && (
              <div className="flex-1 min-w-0">
                <div className="text-sm font-medium truncate">{user?.fullName}</div>
                <div className="text-xs text-gray-400 truncate">{user?.tenantName}</div>
              </div>
            )}
            <button
              onClick={handleLogout}
              className="p-1.5 rounded-lg text-gray-400 hover:text-white hover:bg-sidebar-hover transition-colors"
              title="Logout"
            >
              <LogOut className="h-4 w-4" />
            </button>
          </div>
        </div>

        {/* Collapse toggle */}
        <button
          onClick={() => setCollapsed(!collapsed)}
          className="flex h-10 items-center justify-center border-t border-white/10 text-gray-500 hover:text-gray-300 transition-colors"
        >
          {collapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronLeft className="h-4 w-4" />}
        </button>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-hidden">
        <Outlet />
      </main>
    </div>
  );
}
