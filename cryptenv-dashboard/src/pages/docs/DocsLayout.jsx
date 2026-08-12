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
      <div className="p-4 border-b border-border/60 space-y-3">
        <Link to="/" className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-primary">
          <ArrowLeft className="h-4 w-4" />
          Back to Dashboard
        </Link>
        <div className="flex items-center gap-2">
          <BookOpen className="h-5 w-5 text-primary" />
          <span className="font-semibold">Documentation</span>
        </div>
        <div className="relative">
          <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
          <input
            id="docs-search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search docs…"
            className="w-full h-9 rounded-lg border border-border/70 bg-background/60 pl-8 pr-12 text-sm outline-none focus:ring-2 focus:ring-primary/40"
          />
          <kbd className="absolute right-2 top-1/2 -translate-y-1/2 text-[10px] text-muted-foreground border rounded px-1.5 py-0.5">
            ⌘K
          </kbd>
        </div>
      </div>
      <nav className="flex-1 overflow-y-auto p-3 space-y-4">
        {Object.entries(groups).map(([group, items]) => (
          <div key={group}>
            <p className="px-2 mb-1 text-[11px] uppercase tracking-wider text-muted-foreground">{group}</p>
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
                      className={`w-full text-left px-2.5 py-2 rounded-lg text-sm flex items-center justify-between ${
                        isActive
                          ? 'bg-primary/15 text-primary font-medium'
                          : 'text-muted-foreground hover:bg-muted/50 hover:text-foreground'
                      }`}
                    >
                      <span>{item.label}</span>
                      {item.soon && (
                        <span className="text-[10px] rounded-full bg-muted px-1.5 py-0.5">Soon</span>
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
      <aside className="hidden md:flex w-64 shrink-0 border-r border-border/70 bg-card/70 backdrop-blur-xl flex-col h-screen sticky top-0">
        {navBody}
      </aside>

      {mobileNav && (
        <div className="fixed inset-0 z-50 md:hidden">
          <button type="button" className="absolute inset-0 bg-black/50" aria-label="Close" onClick={() => setMobileNav(false)} />
          <aside className="relative w-[min(100%,18rem)] h-full bg-card border-r border-border flex flex-col shadow-xl">
            <button
              type="button"
              className="absolute right-3 top-3 p-1.5 rounded-md hover:bg-muted"
              onClick={() => setMobileNav(false)}
            >
              <X className="h-4 w-4" />
            </button>
            {navBody}
          </aside>
        </div>
      )}

      <main className="flex-1 overflow-y-auto min-w-0">
        <div className="md:hidden sticky top-0 z-10 border-b border-border/60 bg-background/90 backdrop-blur px-4 py-3 flex items-center gap-3">
          <button type="button" className="p-2 rounded-lg border border-border/70" onClick={() => setMobileNav(true)}>
            <Menu className="h-4 w-4" />
          </button>
          <span className="text-sm font-medium truncate">{DOCS_NAV.find((d) => d.id === active)?.label || 'Docs'}</span>
        </div>
        <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8 sm:py-10">
          <Page />
          <Outlet />
        </div>
      </main>
    </div>
  )
}
