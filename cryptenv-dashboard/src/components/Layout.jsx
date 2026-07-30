import { Outlet, Link, useLocation, useNavigate } from 'react-router-dom'
import { useState, useEffect } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { authAPI, workspaceAPI } from '../lib/api'
import { toast } from 'sonner'
import { Button } from './ui/button'
import {
  LayoutDashboard,
  FolderKanban,
  Users,
  ShieldCheck,
  BarChart3,
  RefreshCw,
  KeyRound,
  ClipboardCheck,
  FileText,
  Boxes,
  Bell,
  Settings as SettingsIcon,
  BookOpen,
  LogOut,
  Menu,
  Moon,
  Sun,
  Shield,
  ChevronDown,
  UserCircle,
  Key,
  Activity,
  CreditCard,
} from 'lucide-react'

export function Layout() {
  const location = useLocation()
  const navigate = useNavigate()
  const [isDark, setIsDark] = useState(false)
  const [isSidebarOpen, setIsSidebarOpen] = useState(true)

  const { data: user } = useQuery({
    queryKey: ['currentUser'],
    queryFn: () => authAPI.getCurrentUser().then((res) => res.data),
  })

  const { data: workspaces } = useQuery({
    queryKey: ['workspaces'],
    queryFn: () => workspaceAPI.list().then((res) => res.data),
  })

  const logoutMutation = useMutation({
    mutationFn: () => authAPI.logout(),
    onSuccess: () => {
      localStorage.removeItem('token')
      navigate('/login')
      toast.success('Logged out successfully')
    },
    onError: () => {
      localStorage.removeItem('token')
      navigate('/login')
    },
  })

  useEffect(() => {
    const isDarkMode = localStorage.getItem('darkMode') === 'true'
    setIsDark(isDarkMode)
    if (isDarkMode) {
      document.documentElement.classList.add('dark')
    }
  }, [])

  const toggleDarkMode = () => {
    const newMode = !isDark
    setIsDark(newMode)
    localStorage.setItem('darkMode', newMode)
    document.documentElement.classList.toggle('dark')
  }

  const handleLogout = () => {
    logoutMutation.mutate()
  }

  const activeWorkspace = workspaces && workspaces.length > 0 ? workspaces[0].name : "My Workspace"
  const userDisplayName = user ? `${user.firstName || ''} ${user.lastName || ''}`.trim() || user.username : 'User'
  const userEmail = user ? user.email : ''

  const navSections = [
    {
      title: 'Platform',
      items: [
        { path: '/', icon: LayoutDashboard, label: 'Dashboard' },
        { path: '/workspace', icon: FolderKanban, label: 'Projects' },
        { path: '/members', icon: Users, label: 'Teams' },
      ],
    },
    {
      title: 'Security',
      items: [
        { path: '/health', icon: ShieldCheck, label: 'Security Health' },
        { path: '/analytics/usage', icon: BarChart3, label: 'Usage Analytics' },
        { path: '/rotation', icon: RefreshCw, label: 'Secret Rotation' },
        { path: '/settings', icon: KeyRound, label: 'Access Keys / API Keys' },
        { path: '/admin/reviews', icon: ClipboardCheck, label: 'Access Reviews' },
        { path: '/audit', icon: FileText, label: 'Audit Logs' },
      ],
    },
    {
      title: 'Configuration',
      items: [
        { path: '/integrations', icon: Boxes, label: 'Integrations' },
        { path: '/notifications', icon: Bell, label: 'Notifications' },
        { path: '/settings', icon: SettingsIcon, label: 'Settings' },
        { path: '/subscription', icon: CreditCard, label: 'Subscription & Usages' },
        { path: '/docs', icon: BookOpen, label: 'Documentation' },
      ],
    },
  ]

  const isActive = (path) => {
    if (path === '/') return location.pathname === '/'
    return location.pathname.startsWith(path)
  }

  return (
    <div className="min-h-screen bg-background flex flex-col">
      {/* Top Navbar */}
      <header className="border-b bg-card sticky top-0 z-20">
        <div className="flex h-16 items-center px-4 justify-between">
          <div className="flex items-center gap-3">
            <Button
              variant="ghost"
              size="icon"
              onClick={() => setIsSidebarOpen(!isSidebarOpen)}
            >
              <Menu className="h-5 w-5" />
            </Button>
            <Link to="/" className="flex items-center gap-2 font-bold text-xl tracking-tight text-primary">
              <Shield className="h-6 w-6 text-primary" />
              <span>CryptEnv</span>
            </Link>
          </div>

          <div className="flex items-center gap-3">
            <Button variant="ghost" size="icon" onClick={toggleDarkMode} title={isDark ? 'Light mode' : 'Dark mode'}>
              {isDark ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}
            </Button>
            <div className="hidden sm:flex flex-col text-right">
              <span className="text-sm font-semibold">{userDisplayName}</span>
              <span className="text-xs text-muted-foreground">{userEmail}</span>
            </div>
            <Button variant="ghost" size="icon" onClick={handleLogout} disabled={logoutMutation.isPending} title="Logout">
              <LogOut className="h-5 w-5 text-destructive" />
            </Button>
          </div>
        </div>
      </header>

      <div className="flex flex-1">
        {/* Sidebar */}
        <aside
          className={`fixed left-0 top-16 z-10 h-[calc(100vh-4rem)] w-64 border-r bg-card transition-transform duration-200 overflow-y-auto flex flex-col justify-between ${
            isSidebarOpen ? 'translate-x-0' : '-translate-x-full'
          }`}
        >
          <div className="p-4 space-y-6">
            {/* Workspace Selector */}
            <div className="p-2.5 rounded-lg border bg-accent/40 flex items-center justify-between cursor-pointer hover:bg-accent transition-colors">
              <div className="flex items-center gap-2.5 overflow-hidden">
                <div className="h-7 w-7 rounded bg-primary text-primary-foreground font-bold flex items-center justify-center text-xs flex-shrink-0">
                  {activeWorkspace.charAt(0).toUpperCase()}
                </div>
                <span className="text-sm font-semibold truncate">{activeWorkspace}</span>
              </div>
              <ChevronDown className="h-4 w-4 text-muted-foreground flex-shrink-0" />
            </div>

            {/* Navigation Groups */}
            {navSections.map((section) => (
              <div key={section.title} className="space-y-1">
                <h2 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider px-3 mb-2">
                  {section.title}
                </h2>
                {section.items.map((item) => {
                  const Icon = item.icon
                  const active = isActive(item.path)
                  return (
                    <Link
                      key={`${item.label}-${item.path}`}
                      to={item.path}
                      className={`flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                        active
                          ? 'bg-primary text-primary-foreground'
                          : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground'
                      }`}
                    >
                      <Icon className="h-4 w-4 flex-shrink-0" />
                      <span className="truncate">{item.label}</span>
                    </Link>
                  )
                })}
              </div>
            ))}
          </div>

          {/* User Profile Badge at bottom */}
          <div className="p-4 border-t bg-muted/20">
            <Link to="/settings" className="flex items-center gap-3 hover:opacity-80 transition-opacity">
              <UserCircle className="h-8 w-8 text-muted-foreground flex-shrink-0" />
              <div className="flex flex-col truncate">
                <span className="text-sm font-semibold truncate">{userDisplayName}</span>
                <span className="text-xs text-muted-foreground truncate">{userEmail}</span>
              </div>
            </Link>
          </div>
        </aside>

        {/* Main Content — shifts right when sidebar is open */}
        <main className={`flex-1 p-6 overflow-y-auto transition-all duration-200 ${isSidebarOpen ? 'ml-64' : 'ml-0'}`}>
          <Outlet />
        </main>
      </div>
    </div>
  )
}
