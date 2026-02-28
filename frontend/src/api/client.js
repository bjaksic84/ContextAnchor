// ─── API Client with JWT handling ────────────────────────────────────────────

const BASE = '/api/v1';

function getToken() {
  const raw = localStorage.getItem('auth');
  if (!raw) return null;
  try { return JSON.parse(raw).accessToken; } catch { return null; }
}

function getRefreshToken() {
  const raw = localStorage.getItem('auth');
  if (!raw) return null;
  try { return JSON.parse(raw).refreshToken; } catch { return null; }
}

async function refreshAccessToken() {
  const refreshToken = getRefreshToken();
  if (!refreshToken) throw new Error('No refresh token');

  const res = await fetch(`${BASE}/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  });
  if (!res.ok) throw new Error('Refresh failed');

  const data = await res.json();
  localStorage.setItem('auth', JSON.stringify(data));
  return data.accessToken;
}

async function request(url, options = {}) {
  let token = getToken();
  const headers = { ...options.headers };

  if (token && !headers['Authorization']) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  let res = await fetch(`${BASE}${url}`, { ...options, headers });

  // Auto-refresh on 401
  if (res.status === 401 && getRefreshToken()) {
    try {
      token = await refreshAccessToken();
      headers['Authorization'] = `Bearer ${token}`;
      res = await fetch(`${BASE}${url}`, { ...options, headers });
    } catch {
      localStorage.removeItem('auth');
      window.location.href = '/login';
      throw new Error('Session expired');
    }
  }

  return res;
}

async function json(url, options) {
  const res = await request(url, options);
  if (res.status === 204) return null;
  const data = await res.json();
  if (!res.ok) throw new ApiError(data.message || res.statusText, res.status, data);
  return data;
}

export class ApiError extends Error {
  constructor(message, status, body) {
    super(message);
    this.status = status;
    this.body = body;
  }
}

// ─── Auth ─────────────────────────────────────────────────────────────────────

export const auth = {
  login: (email, password) =>
    json('/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    }),

  register: (fullName, email, password, organizationName) =>
    json('/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ fullName, email, password, organizationName }),
    }),

  logout: () =>
    json('/auth/logout', { method: 'POST' }).catch(() => {}),
};

// ─── Documents ────────────────────────────────────────────────────────────────

export const documents = {
  list: () => json('/documents'),

  get: (id) => json(`/documents/${id}`),

  upload: async (file, onProgress) => {
    const formData = new FormData();
    formData.append('file', file);

    // Use XMLHttpRequest for progress tracking
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open('POST', `${BASE}/documents`);

      const token = getToken();
      if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`);

      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable && onProgress) {
          onProgress(Math.round((e.loaded / e.total) * 100));
        }
      };

      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          resolve(JSON.parse(xhr.responseText));
        } else {
          try {
            const err = JSON.parse(xhr.responseText);
            reject(new ApiError(err.message || 'Upload failed', xhr.status, err));
          } catch {
            reject(new ApiError('Upload failed', xhr.status));
          }
        }
      };

      xhr.onerror = () => reject(new ApiError('Network error', 0));
      xhr.send(formData);
    });
  },

  delete: (id) => json(`/documents/${id}`, { method: 'DELETE' }),
};

// ─── Chat ─────────────────────────────────────────────────────────────────────

export const chat = {
  send: (question, documentIds, conversationId = null) =>
    json('/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question, documentIds, conversationId }),
    }),

  listConversations: () => json('/chat/conversations'),

  getConversation: (id) => json(`/chat/conversations/${id}`),

  deleteConversation: (id) =>
    json(`/chat/conversations/${id}`, { method: 'DELETE' }),
};

// ─── API Keys ─────────────────────────────────────────────────────────────────

export const apiKeys = {
  list: () => json('/api-keys'),

  create: (name, expiresAt = null) => {
    const params = new URLSearchParams({ name });
    if (expiresAt) params.append('expiresAt', expiresAt);
    return json(`/api-keys?${params}`, { method: 'POST' });
  },

  revoke: (id) => json(`/api-keys/${id}`, { method: 'DELETE' }),
};

// ─── Audit Logs ───────────────────────────────────────────────────────────────

export const audit = {
  list: (page = 0, size = 20, action = null) => {
    const params = new URLSearchParams({ page, size });
    if (action) params.append('action', action);
    return json(`/audit?${params}`);
  },

  listByUser: (userId, page = 0, size = 20) =>
    json(`/audit/user/${userId}?page=${page}&size=${size}`),
};

// ─── Health ───────────────────────────────────────────────────────────────────

export const health = {
  check: () => json('/health'),
};
