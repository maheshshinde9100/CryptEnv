import { useEffect, useMemo, useState } from 'react'
import { Link, Outlet, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, BookOpen, Menu, Search, X } from 'lucide-react'
import { DOCS_NAV } from './nav'
import {
  GettingStartedDoc,
  WorkspacesDoc,
  WorkflowDoc,
  CicdDoc,
  CliDoc,
  VscodeDoc,
  SecurityDoc,
  IntegrationsDoc,
  SdksDoc,
  TutorialsDoc,
} from './sections'

const SECTION_MAP = {
  'getting-started': GettingStartedDoc,
  workspaces: WorkspacesDoc,
  workflow: WorkflowDoc,
  cicd: CicdDoc,
  cli: CliDoc,
  vscode: VscodeDoc,
  security: SecurityDoc,
  integrations: IntegrationsDoc,
  sdks: SdksDoc,
  tutorials: TutorialsDoc,
}

export function DocsLayout() {
  const { section } = useParams()
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [mobileNav, setMobileNav] = useState(false)
  const active = section || 'getting-started'

  useEffect(() => {
    const onKey = (e) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        document.getElementById('docs-search')?.focus()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return DOCS_NAV
    return DOCS_NAV.filter((d) => d.label.toLowerCase().includes(q) || d.id.includes(q))
  }, [query])

  const groups = useMemo(() => {
    const map = {}
    filtered.forEach((item) => {
      if (!map[item.group]) map[item.group] = []
      map[item.group].push(item)
    })
    return map
  }, [filtered])

  const Page = SECTION_MAP[active] || GettingStartedDoc

  const navBody = (
    <>
      <div className="space-y-3 border-b border-border p-4">
        <Link to="/overview" className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="h-4 w-4" />
          Back to Dashboard
        </Link>
        <div className="flex items-center gap-2">
          <BookOpen className="h-4 w-4 text-muted-foreground" />
          <span className="text-sm font-medium">Documentation</span>
        </div>
        <div className="relative">
          <Search className="absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
          <input
            id="docs-search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search docs…"
            className="h-9 w-full rounded-md border border-border bg-surface pl-8 pr-12 text-sm outline-none focus:border-white/30"
          />
          <kbd className="absolute right-2 top-1/2 -translate-y-1/2 rounded border border-border px-1.5 py-0.5 text-[10px] text-muted-foreground">
            ⌘K
          </kbd>
        </div>
      </div>
      <nav className="flex-1 space-y-4 overflow-y-auto p-3">
        {Object.entries(groups).map(([group, items]) => (
          <div key={group}>
            <p className="mb-1 px-2 text-[11px] text-muted-foreground">{group}</p>
            <ul className="space-y-0.5">
              {items.map((item) => {
                const isActive = active === item.id
                return (
                  <li key={item.id}>
                    <button
                      type="button"
                      onClick={() => {
                        navigate(`/docs/${item.id}`)
                        setMobileNav(false)
                      }}
                      className={`flex w-full items-center justify-between rounded-sm px-2.5 py-2 text-left text-sm ${
                        isActive
                          ? 'bg-white/[0.07] text-foreground'
                          : 'text-muted-foreground hover:bg-white/[0.04] hover:text-foreground'
                      }`}
                    >
                      <span>{item.label}</span>
                      {item.soon && (
                        <span className="rounded-full border border-border px-1.5 py-0.5 text-[10px] text-muted-foreground">Soon</span>
                      )}
                    </button>
                  </li>
                )
              })}
            </ul>
          </div>
        ))}
      </nav>
    </>
  )

  return (
    <div className="min-h-screen flex bg-background">
      <aside className="sticky top-0 hidden h-screen w-64 shrink-0 flex-col border-r border-border bg-background md:flex">
        {navBody}
      </aside>

      {mobileNav && (
        <div className="fixed inset-0 z-50 md:hidden">
          <button type="button" className="absolute inset-0 bg-black/60" aria-label="Close" onClick={() => setMobileNav(false)} />
          <aside className="relative flex h-full w-[min(100%,18rem)] flex-col border-r border-border bg-background">
            <button
              type="button"
              className="absolute right-3 top-3 rounded-md p-1.5 hover:bg-white/[0.06]"
              onClick={() => setMobileNav(false)}
            >
              <X className="h-4 w-4" />
            </button>
            {navBody}
          </aside>
        </div>
      )}

      <main className="min-w-0 flex-1 overflow-y-auto">
        <div className="sticky top-0 z-10 flex items-center gap-3 border-b border-border bg-background px-4 py-3 md:hidden">
          <button type="button" className="rounded-md border border-border p-2" onClick={() => setMobileNav(true)}>
            <Menu className="h-4 w-4" />
          </button>
          <span className="truncate text-sm font-medium">{DOCS_NAV.find((d) => d.id === active)?.label || 'Docs'}</span>
        </div>
        <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8 sm:py-10">
          <Page />
          <Outlet />
        </div>
      </main>
    </div>
  )
}
