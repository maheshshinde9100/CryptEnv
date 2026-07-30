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
import { SecurityHealth } from './pages/SecurityHealth'
import { UsageAnalytics } from './pages/UsageAnalytics'
import { SecretRotation } from './pages/SecretRotation'
import { Integrations } from './pages/Integrations'
import { Notifications } from './pages/Notifications'
import { Docs } from './pages/Docs'
import { Subscription } from './pages/Subscription'
import { AccessRequests } from './pages/AccessRequests'
import { AccessReviews } from './pages/AccessReviews'

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
        {/* Public routes */}
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />

        {/* Protected routes */}
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <Layout />
            </ProtectedRoute>
          }
        >
          {/* Platform */}
          <Route index element={<Dashboard />} />
          <Route path="workspace" element={<Workspace />} />
          <Route path="members" element={<Members />} />

          {/* Secrets */}
          <Route path="secrets" element={<Secrets />} />
          <Route path="secrets/new" element={<SecretEditor />} />
          <Route path="secrets/:key/edit" element={<SecretEditor />} />

          {/* Security */}
          <Route path="health" element={<SecurityHealth />} />
          <Route path="analytics/usage" element={<UsageAnalytics />} />
          <Route path="rotation" element={<SecretRotation />} />
          <Route path="access-requests" element={<AccessRequests />} />
          <Route path="admin/reviews" element={<AccessReviews />} />
          <Route path="audit" element={<AuditLogs />} />
          <Route path="audit-logs" element={<AuditLogs />} />

          {/* Configuration */}
          <Route path="integrations" element={<Integrations />} />
          <Route path="notifications" element={<Notifications />} />
          <Route path="settings" element={<Settings />} />
          <Route path="subscription" element={<Subscription />} />
          <Route path="docs" element={<Docs />} />

          {/* Catch-all redirect */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </ErrorBoundary>
  )
}

export default App
