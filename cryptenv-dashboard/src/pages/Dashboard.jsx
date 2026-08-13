import { Link, useOutletContext } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { secretsAPI, workspaceAPI, healthAPI } from '../lib/api'
import { FolderKanban, Lock, ShieldCheck, ArrowRight, Activity } from 'lucide-react'
import { BrandLogo } from '../components/BrandLogo'

export function Dashboard() {
  const { user, activeWorkspace } = useOutletContext() || {}

  const { data: workspaces = [] } = useQuery({
    queryKey: ['workspaces'],
    queryFn: () => workspaceAPI.list().then((r) => r.data),
  })

  const { data: secrets = [] } = useQuery({
    queryKey: ['secrets', 'overview'],
    queryFn: () => secretsAPI.list().then((r) => r.data),
  })

  const { data: health } = useQuery({
    queryKey: ['health'],
    queryFn: () => healthAPI.check().then((r) => r.data),
    retry: 0,
  })

  const encryptedCount = secrets.filter((s) => s.encrypted).length
  const displayName = `${user?.firstName || ''} ${user?.lastName || ''}`.trim() || user?.username || 'there'

  const cards = [
    {
      label: 'Workspaces',
      value: workspaces.length,
      icon: FolderKanban,
      to: '/workspace',
      hint: 'Isolated project vaults',
    },
    {
      label: 'Secrets',
      value: secrets.length,
      icon: Lock,
      to: '/secrets',
      hint: `${encryptedCount} encrypted at rest`,
    },
    {
      label: 'Active workspace',
      value: activeWorkspace?.name || '—',
      icon: ShieldCheck,
      to: '/workspace',
      hint: activeWorkspace?.hasEncryptionKey ? 'Encryption key ready' : 'Set an encryption key',
    },
    {
      label: 'API status',
      value: health?.status || '…',
      icon: Activity,
      to: '/settings',
      hint: health?.service || 'cryptenv-core',
    },
  ]

  return (
    <div className="space-y-8 max-w-6xl">
      <section className="relative overflow-hidden rounded-3xl border border-border/60 bg-card/50 p-6 sm:p-8 surface-glow">
        <div className="absolute inset-0 brand-gradient opacity-[0.08]" />
        <div className="relative flex flex-col sm:flex-row sm:items-center gap-6">
          <BrandLogo size="xl" />
          <div className="flex-1">
            <p className="text-sm text-muted-foreground mb-1">Welcome back</p>
            <h1 className="text-3xl sm:text-4xl font-bold tracking-tight">
              {displayName}
            </h1>
            <p className="mt-2 text-muted-foreground max-w-xl">
              Manage encrypted environment secrets across workspaces — reveal values only when you need them.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Link
              to="/secrets/new"
              className="inline-flex h-10 items-center justify-center rounded-md px-4 text-sm font-medium brand-gradient text-white shadow"
            >
              Add secret
            </Link>
            <Link
              to="/workspace"
              className="inline-flex h-10 items-center justify-center rounded-md border border-input bg-background px-4 text-sm font-medium hover:bg-accent"
            >
              Workspaces
            </Link>
          </div>
        </div>
      </section>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {cards.map((card) => {
          const Icon = card.icon
          return (
            <Link
              key={card.label}
              to={card.to}
              className="glass-panel rounded-2xl p-5 hover:border-primary/40 transition-colors group"
            >
              <div className="flex items-start justify-between mb-4">
                <div className="h-10 w-10 rounded-xl brand-gradient/20 bg-muted flex items-center justify-center">
                  <Icon className="h-5 w-5 text-primary" />
                </div>
                <ArrowRight className="h-4 w-4 text-muted-foreground opacity-0 group-hover:opacity-100 transition-opacity" />
              </div>
              <p className="text-xs uppercase tracking-wider text-muted-foreground">{card.label}</p>
              <p className="mt-1 text-2xl font-bold truncate">{card.value}</p>
              <p className="mt-1 text-xs text-muted-foreground">{card.hint}</p>
            </Link>
          )
        })}
      </div>

      <section className="glass-panel rounded-2xl p-6">
        <h2 className="font-semibold mb-3">Quick start</h2>
        <ol className="space-y-2 text-sm text-muted-foreground list-decimal list-inside">
          <li>Create a workspace and save the generated encryption key offline.</li>
          <li>Add a DEVELOPMENT (or STAGING / PRODUCTION) environment.</li>
          <li>Create secrets — they are encrypted with your workspace key before storage.</li>
          <li>Use the eye icon to reveal decrypted values in the Secrets view.</li>
          <li>Generate an API key in Settings for CLI / VS Code / SDK access.</li>
        </ol>
      </section>
    </div>
  )
}
