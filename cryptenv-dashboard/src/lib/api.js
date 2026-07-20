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
  list: (workspaceId) => api.get(`/workspaces/${workspaceId}/environments`),
  create: (workspaceId, data) => api.post(`/workspaces/${workspaceId}/environments`, data),
  update: (id, data) => api.put(`/environments/${id}`, data),
  delete: (id) => api.delete(`/environments/${id}`),
}

export default api
