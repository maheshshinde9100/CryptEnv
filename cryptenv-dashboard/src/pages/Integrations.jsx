import { useMemo, useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { integrationsAPI, environmentAPI } from '../lib/api'
import { toast } from 'sonner'
import { Button } from '../components/ui/button'
import { Input } from '../components/ui/input'
import { Label } from '../components/ui/label'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog'
import { Skeleton } from '../components/ui/skeleton'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../components/ui/select'
import { cn } from '../lib/utils'
import {
  Code2, Terminal, Globe, Package, Webhook, Database,
  Plug, RefreshCw, Trash2, Send, Search,
  AlertTriangle, Loader2, ExternalLink, RotateCcw, Download,
} from 'lucide-react'

const CATEGORIES = ['All', 'Source Control', 'Deployment', 'Cloud', 'Notifications']

const providerConfigFields = {
  gitlab: [{ key: 'apiUrl', label: 'GitLab API URL', optional: true, placeholder: 'https://gitlab.com' }],
  'gitlab-self-managed': [{ key: 'apiUrl', label: 'GitLab instance URL', required: true, placeholder: 'https://gitlab.example.com' }],
  vercel: [{ key: 'teamId', label: 'Team ID', optional: true }],
  cloudflare: [{ key: 'accountId', label: 'Account ID', required: true }],
  kubernetes: [
    { key: 'apiUrl', label: 'API URL', required: true, placeholder: 'https://k8s.example.com' },
    { key: 'namespace', label: 'Namespace', required: true, placeholder: 'default' },
    { key: 'secretName', label: 'Secret name', optional: true, placeholder: 'cryptenv-secrets' },
  ],
}

const jobStatusStyles = {
  PENDING: 'text-muted-foreground',
  RUNNING: 'text-ink',
  SUCCESS: 'text-muted-foreground',
  FAILED: 'text-danger',
  DEAD: 'text-danger',
}

const toolingIntegrations = [
  { id: 'sdk-java', name: 'Java SDK', description: 'Use CryptEnv in Spring Boot, Quarkus, or any JVM app.', icon: Code2, category: 'SDK', docs: 'https://github.com/maheshshinde9100/CryptEnv/tree/main/cryptenv-sdk' },
  { id: 'cli', name: 'CLI Tool', description: 'Command-line interface for secret management and runtime injection.', icon: Terminal, category: 'CLI', docs: 'https://github.com/maheshshinde9100/CryptEnv/tree/main/cryptenv-cli' },
  { id: 'rest-api', name: 'REST API', description: 'Direct HTTP API access with JWT or API key authentication.', icon: Globe, category: 'API', docs: 'https://cryptenv-backend.onrender.com/swagger-ui.html' },
  { id: 'docker', name: 'Docker / Compose', description: 'Pull secrets at container startup via the CLI run command.', icon: Package, category: 'Container', docs: 'https://github.com/maheshshinde9100/CryptEnv' },
  { id: 'webhooks', name: 'Webhooks', description: 'Receive real-time notifications when secrets change.', icon: Webhook, category: 'Automation', docs: '#' },
  { id: 'postgresql', name: 'PostgreSQL', description: 'Built-in PostgreSQL backend with Flyway migrations.', icon: Database, category: 'Database', docs: '#' },
]

function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString()
}

function monogram(name) {
  const words = (name || '').replace(/[^A-Za-z0-9 ]/g, ' ').split(/\s+/).filter(Boolean)
  if (words.length >= 2) return (words[0][0] + words[1][0]).toUpperCase()
  return (name || '?').replace(/[^A-Za-z0-9]/g, '').slice(0, 2).toUpperCase() || '?'
}

export function Integrations() {
  const { activeWorkspace } = useOutletContext()
  const workspaceId = activeWorkspace?.id
  const queryClient = useQueryClient()

  const [tab, setTab] = useState('All')
  const [search, setSearch] = useState('')
  const [connectProvider, setConnectProvider] = useState(null)
  const [connectForm, setConnectForm] = useState({ token: '', name: '', config: {} })
  const [syncProvider, setSyncProvider] = useState(null)
  const [syncForm, setSyncForm] = useState({ environmentId: '', target: '', secretKeys: '' })
  const [exportEnv, setExportEnv] = useState('')
  const [exportFormat, setExportFormat] = useState('env')

  const { data: overview, isLoading: overviewLoading } = useQuery({
    queryKey: ['integrations'],
    queryFn: () => integrationsAPI.overview().then((res) => res.data),
  })

  const { data: jobs = [], isLoading: jobsLoading } = useQuery({
    queryKey: ['integration-jobs'],
    queryFn: () => integrationsAPI.jobs().then((res) => res.data),
  })

  const { data: queue } = useQuery({
    queryKey: ['integration-queue'],
    queryFn: () => integrationsAPI.queue().then((res) => res.data),
  })

  const { data: environments = [] } = useQuery({
    queryKey: ['environments', workspaceId],
    queryFn: () => environmentAPI.list(workspaceId).then((res) => res.data),
    enabled: !!workspaceId,
  })

  const { data: targets = [], isLoading: targetsLoading } = useQuery({
    queryKey: ['integration-targets', syncProvider],
    queryFn: () => integrationsAPI.targets(syncProvider).then((res) => res.data),
    enabled: !!syncProvider,
  })

  const invalidateProviderData = () => {
    queryClient.invalidateQueries({ queryKey: ['integrations'] })
    queryClient.invalidateQueries({ queryKey: ['integration-jobs'] })
    queryClient.invalidateQueries({ queryKey: ['integration-queue'] })
    queryClient.invalidateQueries({ queryKey: ['integration-targets'] })
  }

  const connectMutation = useMutation({
    mutationFn: ({ provider, data }) => integrationsAPI.connect(provider, data),
    onSuccess: (res, { provider }) => {
      toast.success(`Connected ${provider}`, { description: res.data?.account ? `Verified as ${res.data.account}` : undefined })
      invalidateProviderData()
      setConnectProvider(null)
      setConnectForm({ token: '', name: '', config: {} })
    },
    onError: (error) => toast.error(error.response?.data?.message || 'Failed to connect provider'),
  })

  const testMutation = useMutation({
    mutationFn: (provider) => integrationsAPI.test(provider),
    onSuccess: (res, provider) => {
      if (res.data?.healthy) toast.success(`${provider} connection healthy`)
      else toast.error(`${provider} is unreachable`, { description: res.data?.error })
      invalidateProviderData()
    },
    onError: (error) => toast.error(error.response?.data?.message || 'Test failed'),
  })

  const disconnectMutation = useMutation({
    mutationFn: (provider) => integrationsAPI.disconnect(provider),
    onSuccess: (_res, provider) => {
      toast.success(`Disconnected ${provider}`)
      invalidateProviderData()
    },
    onError: (error) => toast.error(error.response?.data?.message || 'Failed to disconnect'),
  })

  const syncMutation = useMutation({
    mutationFn: ({ provider, data }) => integrationsAPI.sync(provider, data),
    onSuccess: (res, { provider }) => {
      toast.success(`Sync queued for ${provider}`, { description: `${res.data?.secretCount ?? 'Secrets'} queued → ${res.data?.target}` })
      invalidateProviderData()
      setSyncProvider(null)
      setSyncForm({ environmentId: '', target: '', secretKeys: '' })
    },
    onError: (error) => toast.error(error.response?.data?.message || 'Failed to queue sync'),
  })

  const replayMutation = useMutation({
    mutationFn: (id) => integrationsAPI.replay(id),
    onSuccess: () => {
      toast.success('Sync job re-queued')
      invalidateProviderData()
    },
    onError: (error) => toast.error(error.response?.data?.message || 'Failed to replay job'),
  })

  const providers = useMemo(() => overview?.providers ?? [], [overview])
  const integrationsByProvider = Object.fromEntries((overview?.integrations || []).map((i) => [i.provider, i]))
  const connectedCount = providers.filter((p) => p.connected).length
  const connectEntry = providers.find((p) => p.id === connectProvider)
  const connectProviderName = connectEntry?.name || connectProvider
  const syncProviderName = providers.find((p) => p.id === syncProvider)?.name || syncProvider
  const configFields = providerConfigFields[connectProvider] || []

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase()
    return providers.filter((provider) => {
      if (tab !== 'All' && provider.category !== tab) return false
      if (!term) return true
      return (
        provider.name.toLowerCase().includes(term) ||
        provider.id.toLowerCase().includes(term) ||
        (provider.description || '').toLowerCase().includes(term)
      )
    })
  }, [providers, tab, search])

  const grouped = useMemo(() => {
    const sections = tab === 'All' ? CATEGORIES.slice(1) : [tab]
    return sections
      .map((category) => ({
        category,
        items: filtered
          .filter((provider) => provider.category === category)
          .sort((a, b) => Number(b.connected) - Number(a.connected)),
      }))
      .filter((section) => section.items.length > 0)
  }, [filtered, tab])

  const tabCounts = useMemo(() => {
    const counts = { All: providers.length }
    for (const category of CATEGORIES.slice(1)) {
      counts[category] = providers.filter((provider) => provider.category === category).length
    }
    return counts
  }, [providers])

  const openConnect = (providerId) => {
    setConnectProvider(providerId)
    setConnectForm({ token: '', name: '', config: {} })
  }

  const openSync = (providerId) => {
    setSyncProvider(providerId)
    setSyncForm({ environmentId: environments[0] ? String(environments[0].id) : '', target: '', secretKeys: '' })
  }

  const handleConnect = (e) => {
    e.preventDefault()
    if (!connectForm.token.trim()) {
      toast.error('A provider token is required')
      return
    }
    const missing = configFields.filter((f) => f.required && !(connectForm.config[f.key] || '').trim())
    if (missing.length) {
      toast.error(`Missing required config: ${missing.map((f) => f.label).join(', ')}`)
      return
    }
    connectMutation.mutate({
      provider: connectProvider,
      data: {
        token: connectForm.token.trim(),
        name: connectForm.name.trim() || undefined,
        config: connectForm.config,
      },
    })
  }

  const handleSync = (e) => {
    e.preventDefault()
    if (!syncForm.environmentId) {
      toast.error('Select an environment')
      return
    }
    if (!syncForm.target) {
      toast.error('Select a target')
      return
    }
    const secretKeys = syncForm.secretKeys
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean)
    syncMutation.mutate({
      provider: syncProvider,
      data: {
        environmentId: Number(syncForm.environmentId),
        target: syncForm.target,
        secretKeys: secretKeys.length ? secretKeys : undefined,
      },
    })
  }

  const handleExport = async () => {
    if (!exportEnv) {
      toast.error('Select an environment to export')
      return
    }
    try {
      const res = await integrationsAPI.export(exportEnv, exportFormat)
      const text = typeof res.data === 'string' ? res.data : JSON.stringify(res.data, null, 2)
      const ext = exportFormat === 'json' ? 'json' : exportFormat === 'compose' ? 'yaml' : 'env'
      const blob = new Blob([text], { type: 'text/plain' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `secrets-${exportEnv}.${ext}`
      link.click()
      URL.revokeObjectURL(url)
      toast.success('Environment exported')
    } catch (error) {
      toast.error(error.response?.data?.message || 'Export failed')
    }
  }

  const queueCounts = queue?.counts || {}

  return (
    <div className="space-y-8">
      <header>
        <p className="eyebrow">Workspace</p>
        <div className="mt-3 flex flex-wrap items-end justify-between gap-4">
          <div>
            <h1 className="page-title">Integrations</h1>
            <p className="page-subtitle mt-2">
              Connect your stack to sync secrets and trigger automated workflows.
            </p>
          </div>
          <div className="flex items-center gap-4">
            <span className="text-sm text-muted-foreground">{connectedCount} Connected</span>
            <Button variant="outline" size="sm" onClick={() => invalidateProviderData()} disabled={overviewLoading}>
              <RefreshCw className={cn('h-3.5 w-3.5', overviewLoading && 'animate-spin')} />
              Refresh
            </Button>
          </div>
        </div>
      </header>

      <section className="space-y-4">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div className="inline-flex flex-wrap items-center gap-1 rounded-lg border border-border p-1" role="tablist">
            {CATEGORIES.map((category) => (
              <button
                key={category}
                type="button"
                role="tab"
                aria-selected={tab === category}
                onClick={() => setTab(category)}
                className={cn(
                  'rounded-md px-3 py-1.5 text-[13px] transition-colors',
                  tab === category
                    ? 'bg-white/[0.08] text-foreground'
                    : 'text-muted-foreground hover:text-foreground'
                )}
              >
                {category}
                <span className="ml-1.5 text-[11px] text-muted-foreground">{tabCounts[category] ?? 0}</span>
              </button>
            ))}
          </div>
          <div className="relative w-full lg:w-72">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search..."
              aria-label="Search integrations"
              className="pl-9"
            />
          </div>
        </div>

        {overviewLoading ? (
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {[0, 1, 2, 3, 4, 5].map((i) => (
              <Skeleton key={i} className="h-[148px] w-full" />
            ))}
          </div>
        ) : grouped.length === 0 ? (
          <div className="rounded-lg border border-border p-10 text-center text-sm text-muted-foreground">
            No integrations match “{search}”.
          </div>
        ) : (
          <div className="space-y-8">
            {grouped.map((section) => (
              <div key={section.category} className="space-y-3">
                {tab === 'All' && (
                  <div className="flex items-center justify-between">
                    <h2 className="text-sm text-muted-foreground">{section.category}</h2>
                    <span className="text-xs text-muted-foreground">{section.items.length}</span>
                  </div>
                )}
                <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                  {section.items.map((provider) => {
                    const integration = integrationsByProvider[provider.id]
                    return (
                      <article
                        key={provider.id}
                        className={cn(
                          'flex flex-col rounded-lg border border-border bg-surface p-4 transition-colors hover:bg-white/[0.02]',
                          provider.connected && 'border-white/[0.14]'
                        )}
                      >
                        <div className="flex items-start gap-3">
                          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-border bg-background text-[11px] font-medium tracking-wide text-ink">
                            {monogram(provider.name)}
                          </span>
                          <div className="min-w-0">
                            <h3 className="truncate text-sm font-medium">{provider.name}</h3>
                            <p className="mt-0.5 line-clamp-2 text-xs leading-relaxed text-muted-foreground">
                              {provider.description}
                            </p>
                          </div>
                        </div>

                        {integration?.lastError && (
                          <p className="mt-3 flex items-start gap-1.5 text-xs text-danger">
                            <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" />
                            <span className="line-clamp-2">{integration.lastError}</span>
                          </p>
                        )}

                        <div className="mt-auto flex items-center justify-between gap-2 pt-4">
                          <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
                            {provider.connected ? (
                              <>
                                <span className="h-1.5 w-1.5 rounded-full bg-tertiary" />
                                Connected
                              </>
                            ) : (
                              'Not connected'
                            )}
                          </span>
                          {provider.connected ? (
                            <div className="flex items-center gap-1">
                              {provider.native && (
                                <>
                                  <Button size="sm" variant="outline" onClick={() => openSync(provider.id)}>
                                    <Send className="h-3.5 w-3.5" />
                                    Sync
                                  </Button>
                                  <Button
                                    size="icon"
                                    variant="ghost"
                                    aria-label={`Test ${provider.name}`}
                                    onClick={() => testMutation.mutate(provider.id)}
                                    disabled={testMutation.isPending}
                                  >
                                    {testMutation.isPending ? (
                                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                                    ) : (
                                      <RefreshCw className="h-3.5 w-3.5" />
                                    )}
                                  </Button>
                                </>
                              )}
                              <Button
                                size="icon"
                                variant="ghost"
                                aria-label={`Disconnect ${provider.name}`}
                                onClick={() => disconnectMutation.mutate(provider.id)}
                                disabled={disconnectMutation.isPending}
                              >
                                <Trash2 className="h-3.5 w-3.5" />
                              </Button>
                            </div>
                          ) : (
                            <Button size="sm" onClick={() => openConnect(provider.id)}>
                              <Plug className="h-3.5 w-3.5" />
                              Connect
                            </Button>
                          )}
                        </div>
                      </article>
                    )
                  })}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      <section className="space-y-3">
        <div className="flex items-baseline justify-between">
          <h2 className="text-sm text-muted-foreground">Sync queue</h2>
          <span className="text-xs text-muted-foreground">{queue?.total ?? jobs.length} total jobs</span>
        </div>
        <div className="grid grid-cols-3 gap-px overflow-hidden rounded-lg border border-border bg-border lg:grid-cols-5">
          {['PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'DEAD'].map((status) => (
            <div key={status} className="bg-background p-4">
              <p className="text-xs text-muted-foreground">{status}</p>
              <p className="mt-1 text-xl font-medium">{queueCounts[status] ?? 0}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="rounded-lg border border-border p-5">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <h2 className="text-sm font-medium">Export environment</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              Download decrypted secrets as .env, JSON, or docker-compose text.
            </p>
          </div>
          <div className="flex w-full flex-col gap-3 sm:w-auto sm:flex-row sm:items-end">
            <div className="sm:w-56">
              <Label className="mb-1.5 block text-xs text-muted-foreground">Environment</Label>
              <Select value={exportEnv} onValueChange={setExportEnv}>
                <SelectTrigger>
                  <SelectValue placeholder="Select environment" />
                </SelectTrigger>
                <SelectContent>
                  {environments.map((env) => (
                    <SelectItem key={env.id} value={String(env.id)}>{env.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="sm:w-36">
              <Label className="mb-1.5 block text-xs text-muted-foreground">Format</Label>
              <Select value={exportFormat} onValueChange={setExportFormat}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="env">.env</SelectItem>
                  <SelectItem value="json">JSON</SelectItem>
                  <SelectItem value="compose">docker-compose</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <Button onClick={handleExport} disabled={!exportEnv}>
              <Download className="h-4 w-4" />
              Download
            </Button>
          </div>
        </div>
      </section>

      <section className="space-y-3">
        <div className="flex items-baseline justify-between">
          <h2 className="text-sm text-muted-foreground">Sync jobs</h2>
          {jobs.some((job) => job.status === 'DEAD') && (
            <span className="text-xs text-danger">Dead-letter jobs need attention</span>
          )}
        </div>

        {jobsLoading ? (
          <Skeleton className="h-40 w-full" />
        ) : jobs.length === 0 ? (
          <div className="rounded-lg border border-border p-8 text-center text-sm text-muted-foreground">
            No sync jobs yet. Connect a provider and queue a sync to see activity here.
          </div>
        ) : (
          <div className="overflow-hidden rounded-lg border border-border">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="border-b border-border bg-surface">
                  <tr className="text-left text-xs text-muted-foreground">
                    <th className="px-4 py-3 font-medium">Provider</th>
                    <th className="px-4 py-3 font-medium">Operation</th>
                    <th className="px-4 py-3 font-medium">Target</th>
                    <th className="px-4 py-3 font-medium">Status</th>
                    <th className="px-4 py-3 font-medium">Attempts</th>
                    <th className="px-4 py-3 font-medium">Created</th>
                    <th className="px-4 py-3 text-right font-medium">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {jobs.map((job) => (
                    <tr key={job.id} className="border-b border-border/60 transition-colors last:border-0 hover:bg-white/[0.02]">
                      <td className="px-4 py-3 font-medium">{job.provider}</td>
                      <td className="px-4 py-3">{job.operation}</td>
                      <td className="px-4 py-3 font-mono text-xs">{job.target || '—'}</td>
                      <td className="px-4 py-3">
                        <span className={cn('text-xs', jobStatusStyles[job.status])}>{job.status}</span>
                        {job.lastError && (
                          <p className="mt-1 line-clamp-1 text-[11px] text-danger" title={job.lastError}>{job.lastError}</p>
                        )}
                      </td>
                      <td className="px-4 py-3 text-xs">{job.attempts}/{job.maxAttempts}</td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">{formatDate(job.createdAt)}</td>
                      <td className="px-4 py-3 text-right">
                        {(job.status === 'DEAD' || job.status === 'FAILED') && (
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => replayMutation.mutate(job.id)}
                            disabled={replayMutation.isPending}
                          >
                            <RotateCcw className="h-3.5 w-3.5" />
                            Replay
                          </Button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </section>

      <section className="space-y-3">
        <div>
          <h2 className="text-sm text-muted-foreground">Developer tooling</h2>
          <p className="mt-1 text-xs text-muted-foreground">SDKs, CLI, and runtime integrations</p>
        </div>
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {toolingIntegrations.map((integration) => {
            const Icon = integration.icon
            return (
              <article key={integration.id} className="flex flex-col rounded-lg border border-border p-4">
                <div className="flex items-center gap-2">
                  <Icon className="h-4 w-4 text-muted-foreground" />
                  <h3 className="text-sm font-medium">{integration.name}</h3>
                  <span className="ml-auto text-[11px] text-muted-foreground">{integration.category}</span>
                </div>
                <p className="mt-2 text-xs leading-relaxed text-muted-foreground">{integration.description}</p>
                <div className="mt-auto pt-3">
                  <Button
                    variant="ghost"
                    size="sm"
                    className="px-0 text-tertiary hover:bg-transparent"
                    disabled={integration.docs === '#'}
                    onClick={() => integration.docs !== '#' && window.open(integration.docs, '_blank')}
                  >
                    View docs
                    <ExternalLink className="h-3.5 w-3.5" />
                  </Button>
                </div>
              </article>
            )
          })}
        </div>
      </section>

      <Dialog open={!!connectProvider} onOpenChange={(open) => !open && setConnectProvider(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Connect {connectProviderName}</DialogTitle>
            <DialogDescription>
              {connectEntry?.native
                ? 'Credentials are validated against the provider and then encrypted with the master key before storage.'
                : 'The token is encrypted with the master key and stored so your workflows can reference it. Secret push is available on native providers.'}
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleConnect} className="space-y-4">
            <div>
              <Label htmlFor="token" className="mb-1.5 block">Access token</Label>
              <Input
                id="token"
                type="password"
                autoComplete="off"
                value={connectForm.token}
                onChange={(e) => setConnectForm((f) => ({ ...f, token: e.target.value }))}
                placeholder="Paste your access token"
              />
            </div>
            <div>
              <Label htmlFor="name" className="mb-1.5 block">
                Display name <span className="text-muted-foreground">(optional)</span>
              </Label>
              <Input
                id="name"
                value={connectForm.name}
                onChange={(e) => setConnectForm((f) => ({ ...f, name: e.target.value }))}
                placeholder="Optional label"
              />
            </div>
            {configFields.map((field) => (
              <div key={field.key}>
                <Label htmlFor={field.key} className="mb-1.5 block">
                  {field.label}{field.optional ? ' (optional)' : ''}
                </Label>
                <Input
                  id={field.key}
                  value={connectForm.config[field.key] || ''}
                  placeholder={field.placeholder}
                  onChange={(e) =>
                    setConnectForm((f) => ({ ...f, config: { ...f.config, [field.key]: e.target.value } }))
                  }
                />
              </div>
            ))}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setConnectProvider(null)}>Cancel</Button>
              <Button type="submit" disabled={connectMutation.isPending}>
                {connectMutation.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                Connect
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={!!syncProvider} onOpenChange={(open) => !open && setSyncProvider(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Sync secrets to {syncProviderName}</DialogTitle>
            <DialogDescription>
              Choose an environment and a target. The worker retries transient failures automatically.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleSync} className="space-y-4">
            <div>
              <Label className="mb-1.5 block">Environment</Label>
              <Select
                value={syncForm.environmentId}
                onValueChange={(v) => setSyncForm((f) => ({ ...f, environmentId: v }))}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select environment" />
                </SelectTrigger>
                <SelectContent>
                  {environments.map((env) => (
                    <SelectItem key={env.id} value={String(env.id)}>{env.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div>
              <Label className="mb-1.5 block">Target</Label>
              {targetsLoading ? (
                <Skeleton className="h-10 w-full" />
              ) : targets.length === 0 ? (
                <p className="text-xs text-muted-foreground">No targets available for this provider.</p>
              ) : (
                <Select
                  value={syncForm.target}
                  onValueChange={(v) => setSyncForm((f) => ({ ...f, target: v }))}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select target" />
                  </SelectTrigger>
                  <SelectContent>
                    {targets.map((target) => (
                      <SelectItem key={target.id} value={target.id}>
                        {target.name || target.id}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            </div>
            <div>
              <Label htmlFor="secretKeys" className="mb-1.5 block">
                Secret keys <span className="text-muted-foreground">(optional)</span>
              </Label>
              <Input
                id="secretKeys"
                value={syncForm.secretKeys}
                onChange={(e) => setSyncForm((f) => ({ ...f, secretKeys: e.target.value }))}
                placeholder="DATABASE_URL,API_KEY — leave empty to push all"
              />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setSyncProvider(null)}>Cancel</Button>
              <Button type="submit" disabled={syncMutation.isPending}>
                {syncMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
                Queue sync
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
