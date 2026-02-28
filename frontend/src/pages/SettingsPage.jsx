import { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { health as healthApi } from '../api/client';
import {
  User,
  Building2,
  Shield,
  Server,
  Cpu,
  Database,
  HardDrive,
  Activity,
  RefreshCw,
} from 'lucide-react';

export default function SettingsPage() {
  const { user } = useAuth();
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(true);

  const fetchStatus = async () => {
    setLoading(true);
    try {
      const data = await healthApi.check();
      setStatus(data);
    } catch { /* ignore */ }
    setLoading(false);
  };

  useEffect(() => {
    fetchStatus();
  }, []);

  return (
    <div className="flex h-full flex-col">
      {/* Header */}
      <div className="border-b px-6 py-4">
        <h1 className="text-xl font-bold text-gray-900">Settings</h1>
        <p className="text-sm text-gray-500 mt-0.5">Your account and system information</p>
      </div>

      <div className="flex-1 overflow-auto p-6">
        <div className="max-w-3xl space-y-6">
          {/* Profile card */}
          <div className="card p-6">
            <h2 className="text-base font-semibold text-gray-900 mb-4 flex items-center gap-2">
              <User className="h-5 w-5 text-brand-600" />
              Profile
            </h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <InfoRow label="Full Name" value={user?.fullName} />
              <InfoRow label="Email" value={user?.email} />
              <InfoRow
                label="Role"
                value={
                  <span className="badge bg-brand-100 text-brand-700 gap-1">
                    <Shield className="h-3 w-3" />
                    {user?.role}
                  </span>
                }
              />
              <InfoRow label="User ID" value={<span className="font-mono text-xs">{user?.id}</span>} />
            </div>
          </div>

          {/* Organization card */}
          <div className="card p-6">
            <h2 className="text-base font-semibold text-gray-900 mb-4 flex items-center gap-2">
              <Building2 className="h-5 w-5 text-brand-600" />
              Organization
            </h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <InfoRow label="Organization" value={user?.tenantName} />
              <InfoRow label="Tenant ID" value={<span className="font-mono text-xs">{user?.tenantId}</span>} />
            </div>
          </div>

          {/* System Status card */}
          <div className="card p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-base font-semibold text-gray-900 flex items-center gap-2">
                <Activity className="h-5 w-5 text-brand-600" />
                System Status
              </h2>
              <button onClick={fetchStatus} className="btn-secondary text-xs px-3 py-1.5" disabled={loading}>
                <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
                Refresh
              </button>
            </div>

            {!status ? (
              <p className="text-sm text-gray-400">Loading…</p>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <InfoRow
                  label="Status"
                  value={
                    <span className={`badge gap-1 ${status.status === 'UP' ? 'bg-emerald-100 text-emerald-700' : 'bg-red-100 text-red-700'}`}>
                      <span className={`h-2 w-2 rounded-full ${status.status === 'UP' ? 'bg-emerald-500' : 'bg-red-500'}`} />
                      {status.status}
                    </span>
                  }
                />
                <InfoRow
                  label="AI Provider"
                  value={
                    <span className="badge bg-purple-100 text-purple-700 gap-1">
                      <Cpu className="h-3 w-3" />
                      {status.ai?.provider} — {status.ai?.mode}
                    </span>
                  }
                />
                <InfoRow
                  label="Database"
                  value={
                    <span className="flex items-center gap-1.5 text-sm">
                      <Database className="h-3.5 w-3.5 text-gray-400" />
                      {status.database?.database} {status.database?.version?.split(' ')[0]}
                    </span>
                  }
                />
                <InfoRow
                  label="Uptime"
                  value={status.uptime}
                />
                <InfoRow
                  label="Java Runtime"
                  value={`${status.runtime?.javaVendor} Java ${status.runtime?.javaVersion}`}
                />
                <InfoRow
                  label="Memory"
                  value={
                    <span className="flex items-center gap-1.5 text-sm">
                      <HardDrive className="h-3.5 w-3.5 text-gray-400" />
                      {status.runtime?.freeMemoryMB} MB free / {status.runtime?.maxMemoryMB} MB max
                    </span>
                  }
                />
              </div>
            )}
          </div>

          {/* API Info */}
          <div className="card p-6">
            <h2 className="text-base font-semibold text-gray-900 mb-4 flex items-center gap-2">
              <Server className="h-5 w-5 text-brand-600" />
              API Reference
            </h2>
            <div className="space-y-2 text-sm">
              <div className="flex items-center gap-3 rounded-lg bg-gray-50 px-3 py-2">
                <span className="badge bg-emerald-100 text-emerald-700 font-mono">GET</span>
                <code className="text-gray-600 flex-1">/api/v1/health</code>
                <span className="text-gray-400">Health check</span>
              </div>
              <div className="flex items-center gap-3 rounded-lg bg-gray-50 px-3 py-2">
                <span className="badge bg-blue-100 text-blue-700 font-mono">POST</span>
                <code className="text-gray-600 flex-1">/api/v1/documents</code>
                <span className="text-gray-400">Upload document</span>
              </div>
              <div className="flex items-center gap-3 rounded-lg bg-gray-50 px-3 py-2">
                <span className="badge bg-blue-100 text-blue-700 font-mono">POST</span>
                <code className="text-gray-600 flex-1">/api/v1/chat</code>
                <span className="text-gray-400">RAG chat</span>
              </div>
              <a
                href="/swagger-ui.html"
                target="_blank"
                rel="noopener"
                className="inline-block mt-2 text-sm font-medium text-brand-600 hover:text-brand-500"
              >
                Open full Swagger UI →
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function InfoRow({ label, value }) {
  return (
    <div>
      <dt className="text-xs font-medium text-gray-500 uppercase tracking-wider mb-1">{label}</dt>
      <dd className="text-sm text-gray-900">{value || '—'}</dd>
    </div>
  );
}
