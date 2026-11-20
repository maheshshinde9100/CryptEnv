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
  logout: (everywhere = false) => api.post('/auth/logout', null, { params: { everywhere } }),
  getCurrentUser: () => api.get('/auth/me'),
  regenerateApiKey: () => api.post('/auth/api-key/regenerate'),
  mfaSetup: () => api.post('/auth/mfa/setup'),
  mfaEnable: (code) => api.post('/auth/mfa/enable', { code }),
  mfaDisable: (data) => api.post('/auth/mfa/disable', data),
  stepUp: () => api.post('/auth/mfa/step-up'),
  verifyStepUp: (stepUpToken, totpCode) => api.post('/auth/mfa/step-up/verify', { stepUpToken, totpCode }),
}

export const secretsAPI = {
  list: () => api.get('/secrets'),
  listByEnvironment: (environmentId) => api.get(`/secrets/environment/${environmentId}`),
  get: (key) => api.get(`/secrets/${encodeURIComponent(key)}`),
  getByEnvironment: (environmentId, key) =>
    api.get(`/secrets/environment/${environmentId}/${encodeURIComponent(key)}`),
  reveal: (environmentId, key, data = {}) =>
    api.post(`/secrets/environment/${environmentId}/${encodeURIComponent(key)}/reveal`, data),
  create: (data) => api.post('/secrets', data),
  update: (environmentId, key, data) =>
    api.put(`/secrets/environment/${environmentId}/${encodeURIComponent(key)}`, data),
  rotate: (environmentId, key) =>
    api.post(`/secrets/environment/${environmentId}/${encodeURIComponent(key)}/rotate`),
  delete: (key) => api.delete(`/secrets/${encodeURIComponent(key)}`),
  deleteFromEnvironment: (environmentId, key) =>
    api.delete(`/secrets/environment/${environmentId}/${encodeURIComponent(key)}`),
  import: (environmentId, format, content) => api.post('/secrets/import', { environmentId, format, content }),
  export: (environmentId, format = 'ENV') =>
    api.get('/secrets/export', { params: { environmentId, format }, responseType: 'text', transformResponse: (data) => data }),
  diff: (environmentId, key, fromVersion, toVersion) =>
    api.get(`/secrets/environment/${environmentId}/${encodeURIComponent(key)}/versions/diff`, {
      params: { fromVersion, toVersion },
    }),
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
  setExpiration: (key, expiresAt) =>
    api.post(`/secrets/${encodeURIComponent(key)}/lifecycle/expiration`, null, { params: { expiresAt } }),
  removeExpiration: (key) => api.delete(`/secrets/${encodeURIComponent(key)}/lifecycle/expiration`),
}

export const secretVersionAPI = {
  list: (key) => api.get(`/secrets/${encodeURIComponent(key)}/versions`),
  get: (key, version) => api.get(`/secrets/${encodeURIComponent(key)}/versions/${version}`),
  rollback: (key, version) =>
    api.post(`/secrets/${encodeURIComponent(key)}/versions/rollback/${version}`),
  active: (key) => api.get(`/secrets/${encodeURIComponent(key)}/versions/active`),
  diff: (environmentId, key, fromVersion, toVersion) =>
    api.get(`/secrets/environment/${environmentId}/${encodeURIComponent(key)}/versions/diff`, {
      params: { fromVersion, toVersion },
    }),
}

export const workspaceAPI = {
  list: () => api.get('/workspaces'),
  get: (id) => api.get(`/workspaces/${id}`),
  create: (data) => api.post('/workspaces', data),
  update: (id, data) => api.put(`/workspaces/${id}`, data),
  setEncryptionKey: (id, workspaceEncryptionKey) =>
    api.put(`/workspaces/${id}/encryption-key`, { workspaceEncryptionKey }),
  delete: (id) => api.delete(`/workspaces/${id}`),
  inviteMember: (id, email, role = 'DEVELOPER') =>
    api.post(`/workspaces/${id}/members`, null, { params: { email, role } }),
  changeMemberRole: (id, userId, role) =>
    api.put(`/workspaces/${id}/members/${userId}/role`, null, { params: { role } }),
  removeMember: (id, userId) => api.delete(`/workspaces/${id}/members/${userId}`),
  leave: (id) => api.post(`/workspaces/${id}/leave`),
  transferOwnership: (id, newOwnerUserId) =>
    api.post(`/workspaces/${id}/transfer-ownership`, { newOwnerUserId }),
}

export const environmentAPI = {
  list: (workspaceId) => api.get(`/environments/workspace/${workspaceId}`),
  create: (data) => api.post('/environments', data),
  get: (id) => api.get(`/environments/${id}`),
  update: (id, data) => api.patch(`/environments/${id}`, data),
  toggle: (id) => api.patch(`/environments/${id}/toggle`),
  delete: (id) => api.delete(`/environments/${id}`),
}

export const jitAPI = {
  request: (data) => api.post('/jit/request', data),
  approve: (id) => api.post(`/jit/${id}/approve`),
  deny: (id) => api.post(`/jit/${id}/deny`),
  list: (workspaceId) => api.get(`/jit/workspace/${workspaceId}`),
}

export const approvalsAPI = {
  supported: () => api.get('/approvals/supported'),
  mine: () => api.get('/approvals'),
  pending: () => api.get('/approvals/pending'),
  request: (data) => api.post('/approvals', data),
  approve: (id, note) => api.post(`/approvals/${id}/approve`, { note }),
  reject: (id, note) => api.post(`/approvals/${id}/reject`, { note }),
  cancel: (id) => api.post(`/approvals/${id}/cancel`),
}

export const accessReviewsAPI = {
  list: (workspaceId) => api.get(`/access-reviews/workspace/${workspaceId}`),
  create: (data) => api.post('/access-reviews', data),
  items: (reviewId) => api.get(`/access-reviews/${reviewId}/items`),
  reviewItem: (itemId, status, comment) =>
    api.post(`/access-reviews/items/${itemId}/review`, { status, comment }),
  complete: (reviewId) => api.post(`/access-reviews/${reviewId}/complete`),
}

export const auditLogAPI = {
  list: (params) => api.get('/audit-logs', { params }),
  getUserLogs: (userId, params) => api.get(`/audit-logs/user/${userId}`, { params }),
  getActionLogs: (action, params) => api.get(`/audit-logs/action/${action}`, { params }),
  getResourceLogs: (resourceType, resourceId, params) =>
    api.get(`/audit-logs/resource/${resourceType}/${resourceId}`, { params }),
  export: (workspaceId, format = 'json') => api.get('/audit-logs/export', { params: { workspaceId, format }, responseType: 'text' }),
}

export const memberAPI = {
  invite: (workspaceId, email, role = 'DEVELOPER') =>
    api.post(`/workspaces/${workspaceId}/members`, null, { params: { email, role } }),
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

export const incidentsAPI = {
  list: () => api.get('/incidents'),
  get: (id) => api.get(`/incidents/${id}`),
  create: (data) => api.post('/incidents', data),
  status: (id, status, note) => api.post(`/incidents/${id}/status`, { status, note }),
  quarantine: (id) => api.post(`/incidents/${id}/quarantine`),
  postMortem: (id, content) => api.post(`/incidents/${id}/post-mortem`, { content }),
}

export const leaksAPI = {
  list: () => api.get('/leaks'),
  scan: (data) => api.post('/leaks/scan', data),
  update: (id, status) => api.patch(`/leaks/${id}`, { status }),
}

export default api

export const cloudIntegrationsAPI = {
  getTypes: () => api.get('/integrations/types'),
  getWorkspaceIntegrations: (workspaceId) => api.get(`/integrations/workspace/${workspaceId}`),
  getWorkspaceIntegrationsByCategory: (workspaceId, category) => api.get(`/integrations/workspace/${workspaceId}/category/${category}`),
  connect: (data) => api.post('/integrations/connect', data),
  disconnect: (data) => api.post('/integrations/disconnect', data),
  sync: (integrationId, data) => api.post(`/integrations/${integrationId}/sync`, data),
  getSyncLogs: (integrationId) => api.get(`/integrations/${integrationId}/sync-logs`),
}
