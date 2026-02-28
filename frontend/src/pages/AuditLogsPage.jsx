import { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../context/AuthContext';
import { audit as auditApi } from '../api/client';
import {
  ScrollText,
  ChevronLeft,
  ChevronRight,
  Filter,
  User,
  CheckCircle2,
  XCircle,
  Clock,
} from 'lucide-react';

const ACTION_COLORS = {
  USER_LOGIN: 'bg-blue-100 text-blue-700',
  USER_REGISTER: 'bg-indigo-100 text-indigo-700',
  DOCUMENT_UPLOAD: 'bg-emerald-100 text-emerald-700',
  DOCUMENT_DELETE: 'bg-red-100 text-red-700',
  CHAT_QUERY: 'bg-purple-100 text-purple-700',
  API_KEY_CREATE: 'bg-amber-100 text-amber-700',
  API_KEY_REVOKE: 'bg-orange-100 text-orange-700',
};

const ACTION_OPTIONS = [
  'USER_LOGIN',
  'USER_REGISTER',
  'DOCUMENT_UPLOAD',
  'DOCUMENT_DELETE',
  'CHAT_QUERY',
  'API_KEY_CREATE',
  'API_KEY_REVOKE',
];

export default function AuditLogsPage() {
  const { user } = useAuth();
  const [logs, setLogs] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [actionFilter, setActionFilter] = useState('');
  const [userFilter, setUserFilter] = useState(false); // filter to current user only
  const pageSize = 15;

  const fetchLogs = useCallback(async () => {
    setLoading(true);
    try {
      let data;
      if (userFilter && user?.id) {
        data = await auditApi.listByUser(user.id, page, pageSize);
      } else {
        data = await auditApi.list(page, pageSize, actionFilter || null);
      }
      setLogs(data.content || []);
      setTotalPages(data.totalPages || 0);
      setTotalElements(data.totalElements || 0);
    } catch { /* ignore */ }
    setLoading(false);
  }, [page, actionFilter, userFilter, user?.id]);

  useEffect(() => {
    fetchLogs();
  }, [fetchLogs]);

  const handleFilterChange = (action) => {
    setActionFilter(action);
    setPage(0);
  };

  const toggleUserFilter = () => {
    setUserFilter(!userFilter);
    setActionFilter('');
    setPage(0);
  };

  return (
    <div className="flex h-full flex-col">
      {/* Header */}
      <div className="border-b px-6 py-4">
        <h1 className="text-xl font-bold text-gray-900">Audit Logs</h1>
        <p className="text-sm text-gray-500 mt-0.5">
          Track all activity across your organization
        </p>
      </div>

      <div className="flex-1 overflow-auto p-6">
        <div className="max-w-5xl space-y-4">
          {/* Filters */}
          <div className="flex flex-wrap items-center gap-3">
            <div className="flex items-center gap-2 text-sm text-gray-500">
              <Filter className="h-4 w-4" />
              <span>Filter:</span>
            </div>
            <select
              value={actionFilter}
              onChange={(e) => handleFilterChange(e.target.value)}
              className="input w-48 text-sm py-1.5"
              disabled={userFilter}
            >
              <option value="">All actions</option>
              {ACTION_OPTIONS.map((a) => (
                <option key={a} value={a}>
                  {a.replace(/_/g, ' ')}
                </option>
              ))}
            </select>
            <button
              onClick={toggleUserFilter}
              className={`inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                userFilter
                  ? 'bg-brand-100 text-brand-700 ring-1 ring-brand-300'
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              <User className="h-3.5 w-3.5" />
              My activity
            </button>
            <span className="ml-auto text-xs text-gray-400">
              {totalElements} total entries
            </span>
          </div>

          {/* Table */}
          <div className="card overflow-hidden">
            {loading ? (
              <div className="p-8 text-center text-sm text-gray-400">Loading…</div>
            ) : logs.length === 0 ? (
              <div className="p-12 text-center">
                <ScrollText className="h-12 w-12 text-gray-300 mx-auto mb-3" />
                <h3 className="text-base font-medium text-gray-900">No audit logs</h3>
                <p className="text-sm text-gray-500 mt-1">
                  Activity will appear here once users interact with the platform.
                </p>
              </div>
            ) : (
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b bg-gray-50/50 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                    <th className="px-5 py-3">Status</th>
                    <th className="px-5 py-3">Action</th>
                    <th className="px-5 py-3">User</th>
                    <th className="px-5 py-3">Resource</th>
                    <th className="px-5 py-3">Details</th>
                    <th className="px-5 py-3">Time</th>
                    <th className="px-5 py-3">Duration</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {logs.map((log) => (
                    <tr key={log.id} className="hover:bg-gray-50">
                      <td className="px-5 py-3">
                        {log.success ? (
                          <CheckCircle2 className="h-4 w-4 text-emerald-500" />
                        ) : (
                          <XCircle className="h-4 w-4 text-red-500" />
                        )}
                      </td>
                      <td className="px-5 py-3">
                        <span
                          className={`badge text-xs ${
                            ACTION_COLORS[log.action] || 'bg-gray-100 text-gray-700'
                          }`}
                        >
                          {log.action?.replace(/_/g, ' ')}
                        </span>
                      </td>
                      <td className="px-5 py-3 text-gray-600">
                        {log.userEmail || '—'}
                      </td>
                      <td className="px-5 py-3">
                        {log.resourceType ? (
                          <span className="text-gray-600">
                            {log.resourceType}
                            {log.resourceId && (
                              <span className="text-gray-400 font-mono text-xs ml-1">
                                {log.resourceId.substring(0, 8)}…
                              </span>
                            )}
                          </span>
                        ) : (
                          <span className="text-gray-400">—</span>
                        )}
                      </td>
                      <td className="px-5 py-3 max-w-[200px] truncate text-gray-500 text-xs">
                        {log.details || '—'}
                      </td>
                      <td className="px-5 py-3 text-gray-500 whitespace-nowrap">
                        <span className="flex items-center gap-1">
                          <Clock className="h-3 w-3" />
                          {formatDateTime(log.createdAt)}
                        </span>
                      </td>
                      <td className="px-5 py-3 text-gray-400 text-xs">
                        {log.durationMs != null ? `${log.durationMs}ms` : '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between px-1">
              <span className="text-sm text-gray-500">
                Page {page + 1} of {totalPages}
              </span>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setPage(Math.max(0, page - 1))}
                  disabled={page === 0}
                  className="btn-secondary px-3 py-1.5 text-sm disabled:opacity-40"
                >
                  <ChevronLeft className="h-4 w-4" />
                  Previous
                </button>
                <button
                  onClick={() => setPage(Math.min(totalPages - 1, page + 1))}
                  disabled={page >= totalPages - 1}
                  className="btn-secondary px-3 py-1.5 text-sm disabled:opacity-40"
                >
                  Next
                  <ChevronRight className="h-4 w-4" />
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function formatDateTime(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}
