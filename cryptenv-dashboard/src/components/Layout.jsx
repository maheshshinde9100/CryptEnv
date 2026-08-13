import { useState, useEffect, useMemo } from 'react'
import { Outlet, Link, useLocation, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { authAPI, workspaceAPI } from '../lib/api'
import { toast } from 'sonner'
import { Button } from './ui/button'
import { BrandWordmark } from './BrandLogo'
import { applyTheme, isDarkTheme } from '../lib/theme'
import {
  LayoutDashboard,
  FolderKanban,
  KeyRound,
  Users,
  FileText,
  Settings as SettingsIcon,
  BookOpen,
  LogOut,
  Menu,
  Moon,
  Sun,
  ChevronDown,
  Lock,
  Activity,
  X,
  UserCircle2,
} from 'lucide-react'

const navSections = [
  {
    title: 'Platform',
    items: [
      { path: '/overview', icon: LayoutDashboard, label: 'Overview' },
      { path: '/workspace', icon: FolderKanban, label: 'Workspaces' },
      { path: '/secrets', icon: Lock, label: 'Secrets' },
      { path: '/members', icon: Users, label: 'Members' },
    ],
  },
  {
    title: 'Security',
    items: [
      { path: '/rotation', icon: Activity, label: 'Rotation' },
      { path: '/audit', icon: FileText, label: 'Audit Logs' },
      { path: '/settings', icon: KeyRound, label: 'API Keys' },
    ],
  },
  {
    title: 'More',
    items: [
      { path: '/settings', icon: SettingsIcon, label: 'Settings' },
      { path: '/about', icon: UserCircle2, label: 'About' },
      { path: '/docs/getting-started', icon: BookOpen, label: 'Documentation' },
    ],
  },
]

export function Layout() {
  const location = useLocation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [isDark, setIsDark] = useState(true)
  const [isSidebarOpen, setIsSidebarOpen] = useState(true)
  const [wsMenuOpen, setWsMenuOpen] = useState(false)

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
    const dark = isDarkTheme()
    setIsDark(dark)
    applyTheme(dark)
  }, [])

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

  const toggleDarkMode = () => {
    const next = !isDark
    setIsDark(next)
    applyTheme(next)
  }

  const selectWorkspace = (id) => {
    localStorage.setItem('activeWorkspaceId', String(id))
    setWsMenuOpen(false)
    queryClient.invalidateQueries({ queryKey: ['secrets'] })
    queryClient.invalidateQueries({ queryKey: ['environments'] })
    toast.message('Workspace switched')
  }

  const isActive = (path) => {
    if (path === '/overview') return location.pathname === '/overview'
    if (path.startsWith('/docs')) return location.pathname.startsWith('/docs')
    return location.pathname.startsWith(path)
  }

  const userDisplayName =
    `${user?.firstName || ''} ${user?.lastName || ''}`.trim() || user?.username || 'User'

  return (
    <div className="min-h-screen flex flex-col">
      <header className="sticky top-0 z-30 border-b border-border/70 bg-card/70 backdrop-blur-xl">
        <div className="flex h-16 items-center justify-between px-4 gap-3">
          <div className="flex items-center gap-3">
            <Button variant="ghost" size="icon" onClick={() => setIsSidebarOpen((v) => !v)}>
              {isSidebarOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
            </Button>
            <Link to="/overview" className="flex items-center">
              <BrandWordmark size="sm" textClassName="text-xl" />
            </Link>
          </div>

          <div className="flex items-center gap-2 sm:gap-3">
            <Button variant="ghost" size="icon" onClick={toggleDarkMode} title="Toggle theme">
              {isDark ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}
            </Button>
            <div className="hidden sm:flex flex-col text-right leading-tight">
              <span className="text-sm font-semibold">{userDisplayName}</span>
              <span className="text-xs text-muted-foreground">{user?.email}</span>
            </div>
            <Button
              variant="ghost"
              size="icon"
              onClick={() => logoutMutation.mutate()}
              disabled={logoutMutation.isPending}
              title="Sign out"
            >
              <LogOut className="h-5 w-5 text-destructive" />
            </Button>
          </div>
        </div>
      </header>

      <div className="flex flex-1">
        <aside
          className={`fixed left-0 top-16 z-20 h-[calc(100vh-4rem)] w-64 border-r border-border/70 bg-card/80 backdrop-blur-xl transition-transform duration-200 overflow-y-auto flex flex-col ${
            isSidebarOpen ? 'translate-x-0' : '-translate-x-full'
          }`}
        >
          <div className="p-4 space-y-6 flex-1">
            <div className="relative">
              <button
                type="button"
                onClick={() => setWsMenuOpen((v) => !v)}
                className="w-full p-2.5 rounded-xl border border-border/80 bg-muted/40 flex items-center justify-between hover:bg-muted/70 transition-colors"
              >
                <div className="flex items-center gap-2.5 overflow-hidden">
                  <div className="h-8 w-8 rounded-lg brand-gradient text-white font-bold flex items-center justify-center text-xs flex-shrink-0">
                    {(activeWorkspace?.name || 'W').charAt(0).toUpperCase()}
                  </div>
                  <div className="text-left truncate">
                    <p className="text-sm font-semibold truncate">{activeWorkspace?.name || 'Select workspace'}</p>
                    <p className="text-[11px] text-muted-foreground">
                      {activeWorkspace?.hasEncryptionKey ? 'Encrypted' : 'No encryption key'}
                    </p>
                  </div>
                </div>
                <ChevronDown className="h-4 w-4 text-muted-foreground" />
              </button>
              {wsMenuOpen && (
                <div className="absolute z-40 mt-2 w-full rounded-xl border bg-popover shadow-xl p-1 max-h-56 overflow-auto">
                  {workspaces.length === 0 && (
                    <p className="px-3 py-2 text-xs text-muted-foreground">No workspaces yet</p>
                  )}
                  {workspaces.map((w) => (
                    <button
                      key={w.id}
                      type="button"
                      onClick={() => selectWorkspace(w.id)}
                      className={`w-full text-left px-3 py-2 rounded-lg text-sm hover:bg-accent ${
                        w.id === activeWorkspace?.id ? 'bg-accent font-semibold' : ''
                      }`}
                    >
                      {w.name}
                    </button>
                  ))}
                  <Link
                    to="/workspace"
                    onClick={() => setWsMenuOpen(false)}
                    className="block px-3 py-2 text-xs text-primary font-medium"
                  >
                    Manage workspaces →
                  </Link>
                </div>
              )}
            </div>

            {navSections.map((section) => (
              <div key={section.title} className="space-y-1">
                <h2 className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider px-3 mb-2">
                  {section.title}
                </h2>
                {section.items.map((item) => {
                  const Icon = item.icon
                  const active = isActive(item.path)
                  return (
                    <Link
                      key={`${item.label}-${item.path}`}
                      to={item.path}
                      className={`flex items-center gap-3 rounded-xl px-3 py-2 text-sm font-medium transition-all ${
                        active
                          ? 'brand-gradient text-white shadow-md shadow-cyan-500/20'
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
        </aside>

        <main
          className={`flex-1 p-4 sm:p-6 overflow-y-auto transition-all duration-200 ${
            isSidebarOpen ? 'ml-64' : 'ml-0'
          }`}
        >
          <Outlet context={{ activeWorkspace, workspaces, user }} />
        </main>
      </div>
    </div>
  )
}
