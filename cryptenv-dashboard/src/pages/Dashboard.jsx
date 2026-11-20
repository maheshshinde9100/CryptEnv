import { Link, useOutletContext } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { secretsAPI, workspaceAPI, healthAPI, postureAPI, incidentsAPI } from '../lib/api'
import { ArrowUpRight, FolderKanban, KeyRound, ShieldCheck, Activity } from 'lucide-react'

function Stat({ label, value, hint, to }) {
  return (
    <Link to={to} className="group p-5 transition-colors hover:bg-white/[0.02]">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-3 truncate text-2xl font-light tracking-[-0.02em]">{value}</p>
      <p className="mt-1 text-xs text-muted-foreground">{hint}</p>
    </Link>
  )
}

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

  const { data: posture } = useQuery({
    queryKey: ['security-posture'],
    queryFn: () => postureAPI.current().then((r) => r.data),
    retry: 0,
  })

  const { data: incidents = [] } = useQuery({
    queryKey: ['incidents'],
    queryFn: () => incidentsAPI.list().then((r) => r.data),
    retry: 0,
  })

  const encryptedCount = secrets.filter((s) => s.encrypted).length
  const openIncidents = incidents.filter((i) => !['RESOLVED', 'POST_MORTEM'].includes(i.status)).length
  const displayName =
    `${user?.firstName || ''} ${user?.lastName || ''}`.trim() || user?.username || 'there'

  const recent = secrets.slice(0, 5)

  return (
    <div className="space-y-10">
      <header>
        <p className="eyebrow">Overview</p>
        <h1 className="page-title mt-3">Welcome back, {displayName}</h1>
        <p className="page-subtitle max-w-2xl">
          Encrypted environment secrets, workspace access and audit trails in one place.
          {activeWorkspace ? ` You are working in ${activeWorkspace.name}.` : ''}
        </p>
      </header>

      <section className="grid divide-y divide-border rounded-lg border border-border sm:grid-cols-2 sm:divide-y-0 sm:divide-x xl:grid-cols-4">
        <Stat
          label="Workspaces"
          value={workspaces.length}
          hint="Isolated project vaults"
          to="/workspace"
        />
        <Stat
          label="Secrets"
          value={secrets.length}
          hint={`${encryptedCount} encrypted with AES-256-GCM`}
          to="/secrets"
        />
        <Stat
          label="Security posture"
          value={posture?.score != null ? `${posture.score}` : '—'}
          hint={posture?.grade ? `Grade ${posture.grade}` : 'Live posture assessment'}
          to="/security-health"
        />
        <Stat
          label="Open incidents"
          value={openIncidents}
          hint={health?.status ? `API ${health.status}` : 'API reachable'}
          to="/incidents"
        />
      </section>

      <div className="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
        <section className="rounded-lg border border-border">
          <div className="flex items-center justify-between border-b border-border px-5 py-4">
            <div>
              <h2 className="text-base">Recent secrets</h2>
              <p className="text-xs text-muted-foreground">Most recently stored in your workspaces</p>
            </div>
            <Link to="/secrets" className="inline-flex items-center gap-1 text-sm text-ink hover:text-foreground">
              View all <ArrowUpRight className="h-3.5 w-3.5" />
            </Link>
          </div>
          {recent.length === 0 ? (
            <div className="px-5 py-12 text-center text-sm text-muted-foreground">
              No secrets yet.{' '}
              <Link to="/secrets/new" className="text-tertiary">
                Create your first secret
              </Link>
              .
            </div>
          ) : (
            <ul className="divide-y divide-border/60">
              {recent.map((secret) => (
                <li key={`${secret.environmentId}-${secret.key}`} className="flex items-center justify-between gap-4 px-5 py-3.5">
                  <div className="min-w-0">
                    <p className="truncate font-mono text-sm">{secret.key}</p>
                    <p className="mt-0.5 truncate text-xs text-muted-foreground">
                      {secret.description || 'No description'}
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    {secret.autoRotate && <span className="chip">Auto-rotate</span>}
                    <span className="chip">{secret.version ? `v${secret.version}` : 'encrypted'}</span>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>

        <div className="space-y-6">
          <section className="rounded-lg border border-border p-5">
            <h2 className="text-base">Active workspace</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              {activeWorkspace?.name || 'No workspace selected'}
            </p>
            <dl className="mt-5 space-y-3 text-sm">
              <div className="flex items-center justify-between gap-4">
                <dt className="flex items-center gap-2 text-muted-foreground">
                  <KeyRound className="h-3.5 w-3.5" /> Encryption key
                </dt>
                <dd className={activeWorkspace?.hasEncryptionKey ? 'text-foreground' : 'text-danger'}>
                  {activeWorkspace?.hasEncryptionKey ? 'Configured' : 'Missing'}
                </dd>
              </div>
              <div className="flex items-center justify-between gap-4">
                <dt className="flex items-center gap-2 text-muted-foreground">
                  <ShieldCheck className="h-3.5 w-3.5" /> Your role
                </dt>
                <dd>{activeWorkspace?.currentUserRole || '—'}</dd>
              </div>
              <div className="flex items-center justify-between gap-4">
                <dt className="flex items-center gap-2 text-muted-foreground">
                  <FolderKanban className="h-3.5 w-3.5" /> Members
                </dt>
                <dd>{activeWorkspace?.members?.length ?? 0}</dd>
              </div>
              <div className="flex items-center justify-between gap-4">
                <dt className="flex items-center gap-2 text-muted-foreground">
                  <Activity className="h-3.5 w-3.5" /> Runtime
                </dt>
                <dd>{health?.service || 'cryptenv-core'}</dd>
              </div>
            </dl>
            <Link
              to="/secrets/new"
              className="mt-6 inline-flex h-10 w-full items-center justify-center rounded-md bg-primary text-sm font-medium text-primary-foreground transition-colors hover:bg-white/90"
            >
              Add a secret
            </Link>
          </section>

          <section className="rounded-lg border border-border p-5">
            <h2 className="text-base">First steps</h2>
            <ol className="mt-3 space-y-2.5 text-sm text-muted-foreground">
              <li>1. Create a workspace and store its encryption key offline.</li>
              <li>2. Add environments for development, staging and production.</li>
              <li>3. Create secrets; values are encrypted before they are stored.</li>
              <li>4. Generate an API key in Settings for the CLI or SDK.</li>
            </ol>
          </section>
        </div>
      </div>
    </div>
  )
}
