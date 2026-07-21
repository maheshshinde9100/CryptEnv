import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
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
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export const authAPI = {
  login: (email, password) => api.post('/auth/login', { email, password }),
  register: (data) => api.post('/auth/register', data),
  logout: () => api.post('/auth/logout'),
  getCurrentUser: () => api.get('/auth/me'),
}

export const secretsAPI = {
  list: () => api.get('/secrets'),
  get: (key) => api.get(`/secrets/${key}`),
  create: (data) => api.post('/secrets', data),
  update: (key, data) => api.put(`/secrets/${key}`, data),
  delete: (key) => api.delete(`/secrets/${key}`),
}

export const workspaceAPI = {
  list: () => api.get('/workspaces'),
  get: (id) => api.get(`/workspaces/${id}`),
  create: (data) => api.post('/workspaces', data),
  update: (id, data) => api.put(`/workspaces/${id}`, data),
  delete: (id) => api.delete(`/workspaces/${id}`),
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
  getResourceLogs: (resourceType, resourceId, params) => api.get(`/audit-logs/resource/${resourceType}/${resourceId}`, { params }),
}

export const memberAPI = {
  invite: (workspaceId, email) => api.post(`/workspaces/${workspaceId}/members`, null, { params: { email } }),
}

export default api
