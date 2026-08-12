import { Routes, Route, Navigate } from 'react-router-dom'
import { ErrorBoundary } from './components/ErrorBoundary'
import { Layout } from './components/Layout'
import { Login } from './pages/Login'
import { Register } from './pages/Register'
import { Dashboard } from './pages/Dashboard'
import { Secrets } from './pages/Secrets'
import { SecretEditor } from './pages/SecretEditor'
import { Workspace } from './pages/Workspace'
import { Settings } from './pages/Settings'
import { Members } from './pages/Members'
import { AuditLogs } from './pages/AuditLogs'
import { SecretRotation } from './pages/SecretRotation'
import { About } from './pages/About'
import { DocsLayout } from './pages/docs/DocsLayout'

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
        <Route path="/register" element={<Register />} />

        <Route
          path="/docs"
          element={
            <ProtectedRoute>
              <DocsLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<Navigate to="getting-started" replace />} />
          <Route path=":section" element={null} />
        </Route>

        <Route
          path="/"
          element={
            <ProtectedRoute>
              <Layout />
            </ProtectedRoute>
          }
        >
          <Route index element={<Dashboard />} />
          <Route path="workspace" element={<Workspace />} />
          <Route path="members" element={<Members />} />
          <Route path="secrets" element={<Secrets />} />
          <Route path="secrets/new" element={<SecretEditor />} />
          <Route path="secrets/:key/edit" element={<SecretEditor />} />
          <Route path="rotation" element={<SecretRotation />} />
          <Route path="audit" element={<AuditLogs />} />
          <Route path="audit-logs" element={<AuditLogs />} />
          <Route path="settings" element={<Settings />} />
          <Route path="about" element={<About />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </ErrorBoundary>
  )
}

export default App
