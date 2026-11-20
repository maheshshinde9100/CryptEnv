import { useState, useEffect, useMemo } from 'react'
import { Outlet, Link, useLocation, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { authAPI, workspaceAPI } from '../lib/api'
import { toast } from 'sonner'
import { Button } from './ui/button'
import { BrandWordmark } from './BrandLogo'
import {
  LayoutDashboard,
  FolderKanban,
  Users,
  FileText,
  Settings as SettingsIcon,
  BookOpen,
  LogOut,
  Menu,
  ChevronDown,
  Lock,
  Activity,
  X,
  Plug,
  Radar,
  Bell,
  ClipboardCheck,
  Siren,
  ScanSearch,
  Shield,
  KeyRound,
} from 'lucide-react'

const navSections = [
  {
    title: 'Workspace',
    items: [
      { path: '/overview', icon: LayoutDashboard, label: 'Overview' },
      { path: '/workspace', icon: FolderKanban, label: 'Workspaces' },
      { path: '/secrets', icon: Lock, label: 'Secrets' },
      { path: '/members', icon: Users, label: 'Members' },
      { path: '/integrations', icon: Plug, label: 'Integrations' },
    ],
  },
  {
    title: 'Security',
    items: [
      { path: '/rotation', icon: Activity, label: 'Rotation' },
      { path: '/drift', icon: Radar, label: 'Drift detection' },
      { path: '/security-health', icon: Shield, label: 'Security health' },
      { path: '/access-requests', icon: KeyRound, label: 'Access requests' },
      { path: '/access-reviews', icon: ClipboardCheck, label: 'Access reviews' },
      { path: '/incidents', icon: Siren, label: 'Incidents' },
      { path: '/leaks', icon: ScanSearch, label: 'Leak findings' },
      { path: '/audit', icon: FileText, label: 'Audit log' },
    ],
  },
  {
    title: 'Account',
    items: [
      { path: '/notifications', icon: Bell, label: 'Notifications' },
      { path: '/settings', icon: SettingsIcon, label: 'Settings' },
      { path: '/docs/getting-started', icon: BookOpen, label: 'Documentation' },
    ],
  },
]

export function Layout() {
  const location = useLocation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [isSidebarOpen, setIsSidebarOpen] = useState(true)
  const [wsMenuOpen, setWsMenuOpen] = useState(false)

  useEffect(() => {
    document.documentElement.classList.add('dark')
    localStorage.removeItem('darkMode')
  }, [])

  const { data: user } = useQuery({
    queryKey: ['currentUser'],
    queryFn: () => authAPI.getCurrentUser().then((res) => res.data),
  })

  const { data: workspaces = [] } = useQuery({
    queryKey: ['workspaces'],
    queryFn: () => workspaceAPI.list().then((res) => res.data),
  })

  const activeWorkspaceId = Number(localStorage.getItem('activeWorkspaceId') || 0) || workspaces[0]?.id
  const activeWorkspace = useMemo(
    () => workspaces.find((w) => w.id === activeWorkspaceId) || workspaces[0],
    [workspaces, activeWorkspaceId]
  )

  useEffect(() => {
    if (activeWorkspace?.id) {
      localStorage.setItem('activeWorkspaceId', String(activeWorkspace.id))
    }
  }, [activeWorkspace?.id])

  const logoutMutation = useMutation({
    mutationFn: () => authAPI.logout(),
    onSettled: () => {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      localStorage.removeItem('activeWorkspaceId')
      queryClient.clear()
      navigate('/login')
      toast.success('Signed out')
    },
  })

  const selectWorkspace = (id) => {
    localStorage.setItem('activeWorkspaceId', String(id))
    setWsMenuOpen(false)
    queryClient.invalidateQueries({ queryKey: ['secrets'] })
    queryClient.invalidateQueries({ queryKey: ['environments'] })
  }

  const isActive = (path) => {
    if (path === '/overview') return location.pathname === '/overview'
    if (path.startsWith('/docs')) return location.pathname.startsWith('/docs')
    if (path === '/settings') return location.pathname === '/settings'
    return location.pathname.startsWith(path)
  }

  const userDisplayName =
    `${user?.firstName || ''} ${user?.lastName || ''}`.trim() || user?.username || 'User'

  return (
    <div className="min-h-screen bg-background">
      <header className="sticky top-0 z-30 border-b border-border bg-background">
        <div className="flex h-16 items-center justify-between gap-3 px-4 sm:px-6">
          <div className="flex items-center gap-3">
            <Button
              variant="ghost"
              size="icon"
              onClick={() => setIsSidebarOpen((v) => !v)}
              aria-label="Toggle navigation"
            >
              {isSidebarOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
            </Button>
            <Link to="/overview" className="flex items-center">
              <BrandWordmark size="sm" />
            </Link>
          </div>

          <div className="flex items-center gap-3">
            <div className="hidden text-right leading-tight sm:block">
              <p className="text-sm text-foreground">{userDisplayName}</p>
              <p className="text-xs text-muted-foreground">
                {user?.role ? user.role.charAt(0) + user.role.slice(1).toLowerCase() : user?.email}
              </p>
            </div>
            <Button
              variant="ghost"
              size="icon"
              onClick={() => logoutMutation.mutate()}
              disabled={logoutMutation.isPending}
              aria-label="Sign out"
              title="Sign out"
            >
              <LogOut className="h-4 w-4" />
            </Button>
          </div>
        </div>
      </header>

      <div className="flex">
        <aside
          className={`fixed left-0 top-16 z-20 flex h-[calc(100vh-4rem)] w-64 flex-col overflow-y-auto border-r border-border bg-background transition-transform duration-200 ${
            isSidebarOpen ? 'translate-x-0' : '-translate-x-full'
          }`}
        >
          <div className="flex flex-1 flex-col gap-6 p-4">
            <div className="relative">
              <button
                type="button"
                onClick={() => setWsMenuOpen((v) => !v)}
                className="flex w-full items-center justify-between gap-2 rounded-md border border-border bg-surface px-3 py-2.5 text-left transition-colors hover:bg-white/[0.04]"
              >
                <div className="flex min-w-0 items-center gap-2.5">
                  <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-sm border border-border bg-background text-[11px] font-medium text-ink">
                    {(activeWorkspace?.name || 'W').charAt(0).toUpperCase()}
                  </span>
                  <div className="min-w-0">
                    <p className="truncate text-sm text-foreground">
                      {activeWorkspace?.name || 'Select workspace'}
                    </p>
                    <p className="text-[11px] text-muted-foreground">
                      {activeWorkspace?.hasEncryptionKey ? 'Encrypted' : 'No encryption key'}
                    </p>
                  </div>
                </div>
                <ChevronDown className="h-4 w-4 shrink-0 text-muted-foreground" />
              </button>
              {wsMenuOpen && (
                <div className="absolute z-40 mt-2 max-h-60 w-full overflow-auto rounded-md border border-border bg-card p-1">
                  {workspaces.length === 0 && (
                    <p className="px-3 py-2 text-xs text-muted-foreground">No workspaces yet</p>
                  )}
                  {workspaces.map((w) => (
                    <button
                      key={w.id}
                      type="button"
                      onClick={() => selectWorkspace(w.id)}
                      className={`w-full rounded-sm px-3 py-2 text-left text-sm transition-colors hover:bg-white/[0.06] ${
                        w.id === activeWorkspace?.id ? 'text-foreground' : 'text-ink'
                      }`}
                    >
                      {w.name}
                    </button>
                  ))}
                  <Link
                    to="/workspace"
                    onClick={() => setWsMenuOpen(false)}
                    className="block px-3 py-2 text-xs text-tertiary"
                  >
                    Manage workspaces
                  </Link>
                </div>
              )}
            </div>

            <nav className="flex flex-1 flex-col gap-6">
              {navSections.map((section) => (
                <div key={section.title}>
                  <h2 className="mb-2 px-2 text-[11px] font-medium text-muted-foreground">
                    {section.title}
                  </h2>
                  <div className="space-y-0.5">
                    {section.items.map((item) => {
                      const Icon = item.icon
                      const active = isActive(item.path)
                      return (
                        <Link
                          key={`${section.title}-${item.label}`}
                          to={item.path}
                          className={`flex items-center gap-3 rounded-md px-2.5 py-2 text-sm transition-colors ${
                            active
                              ? 'bg-white/[0.07] text-foreground'
                              : 'text-ink hover:bg-white/[0.04] hover:text-foreground'
                          }`}
                        >
                          <Icon className={`h-4 w-4 shrink-0 ${active ? 'text-tertiary' : ''}`} />
                          <span className="truncate">{item.label}</span>
                        </Link>
                      )
                    })}
                  </div>
                </div>
              ))}
            </nav>

            <Link
              to="/about"
              className="px-2 text-xs text-muted-foreground transition-colors hover:text-foreground"
            >
              About CryptEnv
            </Link>
          </div>
        </aside>

        <main
          className={`min-h-[calc(100vh-4rem)] flex-1 px-4 py-8 transition-all duration-200 sm:px-8 ${
            isSidebarOpen ? 'ml-64' : 'ml-0'
          }`}
        >
          <div className="mx-auto w-full max-w-content">
            <Outlet context={{ activeWorkspace, workspaces, user }} />
          </div>
        </main>
      </div>
    </div>
  )
}
