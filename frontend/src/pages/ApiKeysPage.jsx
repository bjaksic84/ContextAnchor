import { useState, useEffect, useCallback } from 'react';
import { apiKeys as apiKeysApi } from '../api/client';
import {
  Key,
  Plus,
  Trash2,
  Copy,
  Check,
  AlertTriangle,
  Clock,
  Shield,
} from 'lucide-react';

export default function ApiKeysPage() {
  const [keys, setKeys] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [name, setName] = useState('');
  const [expiresIn, setExpiresIn] = useState('');
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState('');
  const [newKey, setNewKey] = useState(null);
  const [copied, setCopied] = useState(false);

  const fetchKeys = useCallback(async () => {
    try {
      const data = await apiKeysApi.list();
      setKeys(data);
    } catch { /* ignore */ }
    setLoading(false);
  }, []);

  useEffect(() => {
    fetchKeys();
  }, [fetchKeys]);

  const handleCreate = async (e) => {
    e.preventDefault();
    setCreating(true);
    setError('');
    try {
      let expiresAt = null;
      if (expiresIn) {
        const d = new Date();
        d.setDate(d.getDate() + parseInt(expiresIn));
        expiresAt = d.toISOString();
      }
      const data = await apiKeysApi.create(name, expiresAt);
      setNewKey(data);
      setName('');
      setExpiresIn('');
      setShowCreate(false);
      fetchKeys();
    } catch (err) {
      setError(err.message || 'Failed to create API key');
    }
    setCreating(false);
  };

  const handleRevoke = async (id, keyName) => {
    if (!confirm(`Revoke API key "${keyName}"? This cannot be undone.`)) return;
    try {
      await apiKeysApi.revoke(id);
      fetchKeys();
    } catch { /* ignore */ }
  };

  const copyKey = () => {
    navigator.clipboard.writeText(newKey.rawKey || newKey.key);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="flex h-full flex-col">
      {/* Header */}
      <div className="flex items-center justify-between border-b px-6 py-4">
        <div>
          <h1 className="text-xl font-bold text-gray-900">API Keys</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            Manage API keys for programmatic access
          </p>
        </div>
        <button onClick={() => setShowCreate(true)} className="btn-primary">
          <Plus className="h-4 w-4" />
          Create Key
        </button>
      </div>

      <div className="flex-1 overflow-auto p-6">
        <div className="max-w-4xl space-y-6">
          {/* New key banner */}
          {newKey && (
            <div className="card border-amber-200 bg-amber-50 p-4">
              <div className="flex items-start gap-3">
                <AlertTriangle className="h-5 w-5 text-amber-600 flex-shrink-0 mt-0.5" />
                <div className="flex-1 min-w-0">
                  <h3 className="text-sm font-semibold text-amber-800">
                    Save your API key now
                  </h3>
                  <p className="text-xs text-amber-700 mt-0.5">
                    This key will only be shown once. Copy it and store it securely.
                  </p>
                  <div className="mt-3 flex items-center gap-2">
                    <code className="flex-1 rounded-lg bg-white px-3 py-2 text-sm font-mono text-gray-900 border border-amber-200 break-all">
                      {newKey.rawKey || newKey.key}
                    </code>
                    <button
                      onClick={copyKey}
                      className="btn-secondary flex-shrink-0 px-3 py-2"
                    >
                      {copied ? (
                        <Check className="h-4 w-4 text-emerald-600" />
                      ) : (
                        <Copy className="h-4 w-4" />
                      )}
                    </button>
                  </div>
                </div>
                <button
                  onClick={() => setNewKey(null)}
                  className="text-amber-400 hover:text-amber-600 text-lg leading-none"
                >
                  ×
                </button>
              </div>
            </div>
          )}

          {/* Create form */}
          {showCreate && (
            <div className="card p-6">
              <h2 className="text-base font-semibold text-gray-900 mb-4 flex items-center gap-2">
                <Key className="h-5 w-5 text-brand-600" />
                New API Key
              </h2>
              <form onSubmit={handleCreate} className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Key Name
                  </label>
                  <input
                    type="text"
                    className="input"
                    placeholder="e.g. Production Backend"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Expires In (days)
                  </label>
                  <input
                    type="number"
                    className="input"
                    placeholder="Leave empty for no expiration"
                    value={expiresIn}
                    onChange={(e) => setExpiresIn(e.target.value)}
                    min="1"
                  />
                </div>
                {error && (
                  <p className="text-sm text-red-600">{error}</p>
                )}
                <div className="flex items-center gap-3 pt-1">
                  <button type="submit" className="btn-primary" disabled={creating}>
                    {creating ? 'Creating...' : 'Create Key'}
                  </button>
                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={() => { setShowCreate(false); setError(''); }}
                  >
                    Cancel
                  </button>
                </div>
              </form>
            </div>
          )}

          {/* Keys table */}
          <div className="card overflow-hidden">
            {loading ? (
              <div className="p-8 text-center text-sm text-gray-400">Loading…</div>
            ) : keys.length === 0 ? (
              <div className="p-12 text-center">
                <Shield className="h-12 w-12 text-gray-300 mx-auto mb-3" />
                <h3 className="text-base font-medium text-gray-900">No API keys</h3>
                <p className="text-sm text-gray-500 mt-1">
                  Create an API key for programmatic access to the platform.
                </p>
              </div>
            ) : (
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b bg-gray-50/50 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                    <th className="px-6 py-3">Name</th>
                    <th className="px-6 py-3">Key Prefix</th>
                    <th className="px-6 py-3">Created</th>
                    <th className="px-6 py-3">Expires</th>
                    <th className="px-6 py-3">Last Used</th>
                    <th className="px-6 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {keys.map((k) => (
                    <tr key={k.id} className="hover:bg-gray-50">
                      <td className="px-6 py-3.5 font-medium text-gray-900">
                        <div className="flex items-center gap-2">
                          <Key className="h-4 w-4 text-gray-400" />
                          {k.name}
                        </div>
                      </td>
                      <td className="px-6 py-3.5">
                        <code className="text-xs bg-gray-100 px-2 py-0.5 rounded font-mono">
                          {k.keyPrefix}•••
                        </code>
                      </td>
                      <td className="px-6 py-3.5 text-gray-500">
                        {formatDate(k.createdAt)}
                      </td>
                      <td className="px-6 py-3.5">
                        {k.expiresAt ? (
                          <span className="flex items-center gap-1 text-gray-500">
                            <Clock className="h-3.5 w-3.5" />
                            {formatDate(k.expiresAt)}
                          </span>
                        ) : (
                          <span className="text-gray-400">Never</span>
                        )}
                      </td>
                      <td className="px-6 py-3.5 text-gray-500">
                        {k.lastUsedAt ? formatDate(k.lastUsedAt) : 'Never'}
                      </td>
                      <td className="px-6 py-3.5 text-right">
                        <button
                          onClick={() => handleRevoke(k.id, k.name)}
                          className="inline-flex items-center gap-1 text-red-600 hover:text-red-700 text-xs font-medium"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                          Revoke
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          {/* Usage hint */}
          <div className="card p-5">
            <h3 className="text-sm font-semibold text-gray-900 mb-2">Using API Keys</h3>
            <p className="text-sm text-gray-500 mb-3">
              Include your API key in the <code className="text-xs bg-gray-100 px-1.5 py-0.5 rounded">X-API-Key</code> header:
            </p>
            <pre className="rounded-lg bg-gray-900 text-gray-100 text-xs p-4 overflow-x-auto">
{`curl -H "X-API-Key: rak_your_key_here" \\
     http://localhost:8080/api/v1/documents`}
            </pre>
          </div>
        </div>
      </div>
    </div>
  );
}

function formatDate(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
}
