import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'sonner'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Secrets from './pages/Secrets'
import SecretEditor from './pages/SecretEditor'
import Workspace from './pages/Workspace'
import Settings from './pages/Settings'
import Members from './pages/Members'
import Layout from './components/Layout'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
})

function App() {
  const isAuthenticated = !!localStorage.getItem('token')

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={!isAuthenticated ? <Login /> : <Navigate to="/dashboard" />} />
          <Route path="/" element={<Layout />}>
            <Route index element={<Navigate to="/dashboard" />} />
            <Route path="dashboard" element={isAuthenticated ? <Dashboard /> : <Navigate to="/login" />} />
            <Route path="secrets" element={isAuthenticated ? <Secrets /> : <Navigate to="/login" />} />
            <Route path="secrets/:id" element={isAuthenticated ? <SecretEditor /> : <Navigate to="/login" />} />
            <Route path="workspace" element={isAuthenticated ? <Workspace /> : <Navigate to="/login" />} />
            <Route path="settings" element={isAuthenticated ? <Settings /> : <Navigate to="/login" />} />
            <Route path="members" element={isAuthenticated ? <Members /> : <Navigate to="/login" />} />
          </Route>
        </Routes>
        <Toaster position="top-right" />
      </BrowserRouter>
    </QueryClientProvider>
  )
}

export default App
