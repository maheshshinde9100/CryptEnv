import axios from 'axios'

const apiBase =
  import.meta.env.VITE_API_URL?.replace(/\/$/, '') ||
  ''

const api = axios.create({
  baseURL: apiBase ? `${apiBase}/api` : '/api',
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      localStorage.removeItem('activeWorkspaceId')
      if (!window.location.pathname.startsWith('/login') && !window.location.pathname.startsWith('/register') && window.location.pathname !== '/') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

export const authAPI = {
  login: (email, password) => api.post('/auth/login', { email, password }),
  register: (data) => api.post('/auth/register', data),
  verifyEmail: (email, code) => api.post('/auth/email-verification/verify', { email, code }),
  resendVerification: (email) => api.post('/auth/email-verification/resend', { email }),
  logout: () => api.post('/auth/logout'),
  getCurrentUser: () => api.get('/auth/me'),
  regenerateApiKey: () => api.post('/auth/api-key/regenerate'),
}

export const secretsAPI = {
  list: () => api.get('/secrets'),
  listByEnvironment: (environmentId) => api.get(`/secrets/environment/${environmentId}`),
  get: (key) => api.get(`/secrets/${encodeURIComponent(key)}`),
  getByEnvironment: (environmentId, key) =>
    api.get(`/secrets/environment/${environmentId}/${encodeURIComponent(key)}`),
  create: (data) => api.post('/secrets', data),
  update: (environmentId, key, data) =>
    api.put(`/secrets/environment/${environmentId}/${encodeURIComponent(key)}`, data),
  delete: (key) => api.delete(`/secrets/${encodeURIComponent(key)}`),
}

export const secretLifecycleAPI = {
  softDelete: (key) => api.post(`/secrets/${encodeURIComponent(key)}/lifecycle/soft-delete`),
  restore: (key) => api.post(`/secrets/${encodeURIComponent(key)}/lifecycle/restore`),
  activate: (key) => api.post(`/secrets/${encodeURIComponent(key)}/lifecycle/activate`),
  deactivate: (key) => api.post(`/secrets/${encodeURIComponent(key)}/lifecycle/deactivate`),
  setRotationInterval: (key, intervalDays) =>
    api.post(`/secrets/${encodeURIComponent(key)}/lifecycle/rotation-interval`, null, {
      params: { intervalDays },
    }),
  enableAutoRotate: (key) =>
    api.post(`/secrets/${encodeURIComponent(key)}/lifecycle/auto-rotate/enable`),
  disableAutoRotate: (key) =>
    api.post(`/secrets/${encodeURIComponent(key)}/lifecycle/auto-rotate/disable`),
}

export const secretVersionAPI = {
  list: (key) => api.get(`/secrets/${encodeURIComponent(key)}/versions`),
  get: (key, version) => api.get(`/secrets/${encodeURIComponent(key)}/versions/${version}`),
  rollback: (key, version) =>
    api.post(`/secrets/${encodeURIComponent(key)}/versions/rollback/${version}`),
  active: (key) => api.get(`/secrets/${encodeURIComponent(key)}/versions/active`),
}

export const workspaceAPI = {
  list: () => api.get('/workspaces'),
  get: (id) => api.get(`/workspaces/${id}`),
  create: (data) => api.post('/workspaces', data),
  update: (id, data) => api.put(`/workspaces/${id}`, data),
  setEncryptionKey: (id, workspaceEncryptionKey) =>
    api.put(`/workspaces/${id}/encryption-key`, { workspaceEncryptionKey }),
  delete: (id) => api.delete(`/workspaces/${id}`),
  inviteMember: (id, email) =>
    api.post(`/workspaces/${id}/members`, null, { params: { email } }),
}

export const environmentAPI = {
  list: (workspaceId) => api.get(`/environments/workspace/${workspaceId}`),
  create: (data) => api.post('/environments', data),
  get: (id) => api.get(`/environments/${id}`),
  toggle: (id) => api.patch(`/environments/${id}/toggle`),
  delete: (id) => api.delete(`/environments/${id}`),
}

export const auditLogAPI = {
  list: (params) => api.get('/audit-logs', { params }),
  getUserLogs: (userId, params) => api.get(`/audit-logs/user/${userId}`, { params }),
  getActionLogs: (action, params) => api.get(`/audit-logs/action/${action}`, { params }),
  getResourceLogs: (resourceType, resourceId, params) =>
    api.get(`/audit-logs/resource/${resourceType}/${resourceId}`, { params }),
}

export const memberAPI = {
  invite: (workspaceId, email) =>
    api.post(`/workspaces/${workspaceId}/members`, null, { params: { email } }),
}

export const integrationsAPI = {
  overview: () => api.get('/integrations'),
  targets: (provider) => api.get(`/integrations/${provider}/targets`),
  connect: (provider, data) => api.post(`/integrations/${provider}/connect`, data),
  test: (provider) => api.post(`/integrations/${provider}/test`),
  disconnect: (provider) => api.delete(`/integrations/${provider}`),
  sync: (provider, data) => api.post(`/integrations/${provider}/sync`, data),
  deleteSecret: (provider, data) => api.post(`/integrations/${provider}/delete-secret`, data),
  jobs: () => api.get('/integrations/jobs'),
  queue: () => api.get('/integrations/queue'),
  replay: (id) => api.post(`/integrations/jobs/${id}/replay`),
  export: (environmentId, format = 'env') =>
    api.get(`/integrations/export/${environmentId}`, {
      params: { format },
      responseType: 'text',
      transformResponse: (data) => data,
    }),
}

export const driftAPI = {
  scan: (environmentId) => api.post('/drift/scan', null, { params: { environmentId } }),
  scans: () => api.get('/drift/scans'),
  scanDetail: (id) => api.get(`/drift/scans/${id}`),
  findings: (environmentId) =>
    api.get('/drift/findings', { params: environmentId ? { environmentId } : undefined }),
  remediate: (id) => api.post(`/drift/scans/${id}/remediate`),
  report: (id, format = 'json') =>
    api.get(`/drift/scans/${id}/report`, {
      params: { format },
      responseType: 'text',
      transformResponse: (data) => data,
    }),
}

export const healthAPI = {
  check: () => api.get('/health'),
}

export const postureAPI = {
  current: () => api.get('/security-posture'),
}

export const notificationsAPI = {
  list: (params) => api.get('/notifications', { params }),
  preferences: () => api.get('/notifications/preferences'),
  updatePreferences: (data) => api.put('/notifications/preferences', data),
  markRead: (id) => api.post(`/notifications/${id}/read`),
  markAllRead: () => api.post('/notifications/read-all'),
}

export default api
