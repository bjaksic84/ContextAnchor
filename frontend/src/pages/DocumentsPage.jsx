import { useState, useEffect, useCallback } from 'react';
import { useDropzone } from 'react-dropzone';
import { documents as docsApi } from '../api/client';
import {
  Upload,
  FileText,
  Trash2,
  RefreshCw,
  CheckCircle2,
  Clock,
  AlertCircle,
  Loader2,
  File,
  HardDrive,
} from 'lucide-react';

const STATUS_CONFIG = {
  UPLOADED:    { color: 'bg-blue-100 text-blue-700',   icon: Clock,        label: 'Uploaded' },
  PROCESSING:  { color: 'bg-yellow-100 text-yellow-700', icon: Loader2,    label: 'Processing' },
  CHUNKING:    { color: 'bg-yellow-100 text-yellow-700', icon: Loader2,    label: 'Chunking' },
  EMBEDDING:   { color: 'bg-purple-100 text-purple-700', icon: Loader2,    label: 'Embedding' },
  READY:       { color: 'bg-emerald-100 text-emerald-700', icon: CheckCircle2, label: 'Ready' },
  ERROR:       { color: 'bg-red-100 text-red-700',     icon: AlertCircle,  label: 'Error' },
};

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function formatDate(dateStr) {
  return new Date(dateStr).toLocaleDateString('en-US', {
    month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

export default function DocumentsPage() {
  const [docs, setDocs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [error, setError] = useState('');
  const [deleting, setDeleting] = useState(null);

  const fetchDocs = useCallback(async () => {
    try {
      const data = await docsApi.list();
      setDocs(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchDocs();
  }, [fetchDocs]);

  // Auto-refresh when docs are processing
  useEffect(() => {
    const processing = docs.some((d) =>
      ['UPLOADED', 'PROCESSING', 'CHUNKING', 'EMBEDDING'].includes(d.status)
    );
    if (!processing) return;
    const interval = setInterval(fetchDocs, 3000);
    return () => clearInterval(interval);
  }, [docs, fetchDocs]);

  const onDrop = useCallback(async (acceptedFiles) => {
    if (acceptedFiles.length === 0) return;
    setUploading(true);
    setUploadProgress(0);
    setError('');
    try {
      for (const file of acceptedFiles) {
        await docsApi.upload(file, setUploadProgress);
      }
      await fetchDocs();
    } catch (err) {
      setError(err.message || 'Upload failed');
    } finally {
      setUploading(false);
      setUploadProgress(0);
    }
  }, [fetchDocs]);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: {
      'application/pdf': ['.pdf'],
      'text/plain': ['.txt'],
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document': ['.docx'],
    },
    disabled: uploading,
  });

  const handleDelete = async (id) => {
    if (!confirm('Delete this document and all its embeddings?')) return;
    setDeleting(id);
    try {
      await docsApi.delete(id);
      setDocs((prev) => prev.filter((d) => d.id !== id));
    } catch (err) {
      setError(err.message);
    } finally {
      setDeleting(null);
    }
  };

  return (
    <div className="flex h-full flex-col">
      {/* Header */}
      <div className="flex items-center justify-between border-b px-6 py-4">
        <div>
          <h1 className="text-xl font-bold text-gray-900">Documents</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            Upload and manage your knowledge base documents
          </p>
        </div>
        <button onClick={fetchDocs} className="btn-secondary" disabled={loading}>
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </div>

      <div className="flex-1 overflow-auto p-6 space-y-6">
        {/* Upload zone */}
        <div
          {...getRootProps()}
          className={`card cursor-pointer border-2 border-dashed p-8 text-center transition-colors ${
            isDragActive
              ? 'border-brand-500 bg-brand-50'
              : uploading
              ? 'border-gray-300 bg-gray-50 cursor-not-allowed'
              : 'border-gray-300 hover:border-brand-400 hover:bg-gray-50'
          }`}
        >
          <input {...getInputProps()} />
          {uploading ? (
            <div className="space-y-3">
              <Loader2 className="h-10 w-10 text-brand-500 mx-auto animate-spin" />
              <p className="text-sm font-medium text-gray-700">Uploading… {uploadProgress}%</p>
              <div className="mx-auto h-2 w-64 overflow-hidden rounded-full bg-gray-200">
                <div
                  className="h-full rounded-full bg-brand-500 transition-all"
                  style={{ width: `${uploadProgress}%` }}
                />
              </div>
            </div>
          ) : (
            <div className="space-y-2">
              <Upload className="h-10 w-10 text-gray-400 mx-auto" />
              <p className="text-sm font-medium text-gray-700">
                {isDragActive ? 'Drop files here…' : 'Drag & drop files, or click to browse'}
              </p>
              <p className="text-xs text-gray-400">Supports PDF, DOCX, and TXT (max 50 MB)</p>
            </div>
          )}
        </div>

        {/* Error banner */}
        {error && (
          <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700 flex items-center gap-2">
            <AlertCircle className="h-4 w-4 flex-shrink-0" />
            {error}
            <button onClick={() => setError('')} className="ml-auto text-red-500 hover:text-red-700 text-xs font-medium">
              Dismiss
            </button>
          </div>
        )}

        {/* Documents list */}
        {loading ? (
          <div className="flex justify-center py-12">
            <Loader2 className="h-8 w-8 animate-spin text-brand-500" />
          </div>
        ) : docs.length === 0 ? (
          <div className="text-center py-16 text-gray-400">
            <HardDrive className="h-12 w-12 mx-auto mb-3 opacity-50" />
            <p className="font-medium">No documents yet</p>
            <p className="text-sm mt-1">Upload your first document to get started</p>
          </div>
        ) : (
          <div className="card overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                  <th className="px-4 py-3">Document</th>
                  <th className="px-4 py-3">Size</th>
                  <th className="px-4 py-3">Chunks</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Uploaded</th>
                  <th className="px-4 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {docs.map((doc) => {
                  const st = STATUS_CONFIG[doc.status] || STATUS_CONFIG.ERROR;
                  const StIcon = st.icon;
                  return (
                    <tr key={doc.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-3">
                          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-brand-50">
                            <FileText className="h-4 w-4 text-brand-600" />
                          </div>
                          <div>
                            <div className="font-medium text-gray-900 truncate max-w-xs">
                              {doc.originalName}
                            </div>
                            <div className="text-xs text-gray-400 font-mono">{doc.id.slice(0, 8)}…</div>
                          </div>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-gray-600">{formatSize(doc.fileSize)}</td>
                      <td className="px-4 py-3 text-gray-600">{doc.chunkCount ?? '—'}</td>
                      <td className="px-4 py-3">
                        <span className={`badge gap-1 ${st.color}`}>
                          <StIcon className={`h-3 w-3 ${['PROCESSING','CHUNKING','EMBEDDING'].includes(doc.status) ? 'animate-spin' : ''}`} />
                          {st.label}
                        </span>
                        {doc.errorMessage && (
                          <p className="text-xs text-red-500 mt-1 max-w-xs truncate">{doc.errorMessage}</p>
                        )}
                      </td>
                      <td className="px-4 py-3 text-gray-500 text-xs">{formatDate(doc.createdAt)}</td>
                      <td className="px-4 py-3 text-right">
                        <button
                          onClick={() => handleDelete(doc.id)}
                          disabled={deleting === doc.id}
                          className="inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-medium text-red-600 hover:bg-red-50 transition-colors disabled:opacity-50"
                        >
                          {deleting === doc.id ? (
                            <Loader2 className="h-3.5 w-3.5 animate-spin" />
                          ) : (
                            <Trash2 className="h-3.5 w-3.5" />
                          )}
                          Delete
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
