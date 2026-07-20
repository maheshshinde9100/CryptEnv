import { Routes, Route, Navigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ErrorBoundary } from './components/ErrorBoundary'
import { Layout } from './components/Layout'
import { Login } from './pages/Login'
import { Dashboard } from './pages/Dashboard'
import { Secrets } from './pages/Secrets'
import { SecretEditor } from './pages/SecretEditor'
import { Workspace } from './pages/Workspace'
import { Settings } from './pages/Settings'
import { Members } from './pages/Members'
import { authAPI } from './lib/api'

function ProtectedRoute({ children }) {
  const token = localStorage.getItem('token')
  if (!token) {
    return <Navigate to="/login" replace />
  }
  return children
}

function App() {
  return (
    <ErrorBoundary>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <Layout />
            </ProtectedRoute>
          }
        >
          <Route index element={<Dashboard />} />
          <Route path="secrets" element={<Secrets />} />
          <Route path="secrets/new" element={<SecretEditor />} />
          <Route path="secrets/:key/edit" element={<SecretEditor />} />
          <Route path="workspace" element={<Workspace />} />
          <Route path="settings" element={<Settings />} />
          <Route path="members" element={<Members />} />
        </Route>
      </Routes>
    </ErrorBoundary>
  )
}

export default App
