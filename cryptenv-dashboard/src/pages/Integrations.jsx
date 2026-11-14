import { useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { integrationsAPI, environmentAPI } from '../lib/api'
import { toast } from 'sonner'
import { Button } from '../components/ui/button'
import { Input } from '../components/ui/input'
import { Label } from '../components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog'
import { Skeleton } from '../components/ui/skeleton'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../components/ui/select'
import {
  Github, Triangle, Cloud, Boxes, Plug, RefreshCw, Trash2, Send,
  AlertTriangle, CheckCircle2, Loader2, ExternalLink, RotateCcw, Download,
  Code2, Terminal, Globe, Package, Webhook, Database, Server,
} from 'lucide-react'

const providerMeta = {
  github: { icon: Github, hint: 'Personal access token with repo + actions scope' },
  vercel: { icon: Triangle, hint: 'Vercel access token' },
  cloudflare: { icon: Cloud, hint: 'Cloudflare API token with Workers/Pages edit' },
  kubernetes: { icon: Boxes, hint: 'Kubernetes bearer token or service-account token' },
}

const providerConfigFields = {
  github: [],
  vercel: [{ key: 'teamId', label: 'Team ID', optional: true }],
  cloudflare: [{ key: 'accountId', label: 'Account ID', required: true }],
  kubernetes: [
    { key: 'apiUrl', label: 'API URL', required: true, placeholder: 'https://k8s.example.com' },
    { key: 'namespace', label: 'Namespace', required: true, placeholder: 'default' },
    { key: 'secretName', label: 'Secret name', optional: true, placeholder: 'cryptenv-secrets' },
  ],
}

const statusStyles = {
  CONNECTED: 'bg-green-500/10 text-green-600 border-green-500/30',
  ERROR: 'bg-red-500/10 text-red-600 border-red-500/30',
  DISCONNECTED: 'bg-muted text-muted-foreground border-border',
}

const jobStatusStyles = {
  PENDING: 'bg-yellow-500/10 text-yellow-600 border-yellow-500/30',
  RUNNING: 'bg-blue-500/10 text-blue-600 border-blue-500/30',
  SUCCESS: 'bg-green-500/10 text-green-600 border-green-500/30',
  FAILED: 'bg-orange-500/10 text-orange-600 border-orange-500/30',
  DEAD: 'bg-red-500/10 text-red-600 border-red-500/30',
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

export function Integrations() {
  const { activeWorkspace } = useOutletContext()
  const workspaceId = activeWorkspace?.id
  const queryClient = useQueryClient()

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

  const providers = overview?.providers || []
  const integrationsByProvider = Object.fromEntries((overview?.integrations || []).map((i) => [i.provider, i]))
  const configFields = providerConfigFields[connectProvider] || []
  const connectProviderName = providers.find((p) => p.id === connectProvider)?.name || connectProvider
  const syncProviderName = providers.find((p) => p.id === syncProvider)?.name || syncProvider

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
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Integrations</h1>
          <p className="text-muted-foreground">Push workspace secrets to cloud providers and CI/CD platforms</p>
        </div>
        <Button
          variant="outline"
          onClick={() => invalidateProviderData()}
          disabled={overviewLoading}
        >
          <RefreshCw className={`mr-2 h-4 w-4 ${overviewLoading ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </div>

      {/* Queue summary */}
      <section className="grid gap-3 grid-cols-2 sm:grid-cols-3 lg:grid-cols-6">
        {['PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'DEAD'].map((status) => (
          <div key={status} className="glass-panel rounded-xl p-4">
            <p className="text-xs text-muted-foreground uppercase tracking-wider">{status}</p>
            <p className="text-2xl font-semibold mt-1">{queueCounts[status] ?? 0}</p>
          </div>
        ))}
        <div className="glass-panel rounded-xl p-4">
          <p className="text-xs text-muted-foreground uppercase tracking-wider">Total</p>
          <p className="text-2xl font-semibold mt-1">{queue?.total ?? jobs.length}</p>
        </div>
      </section>

      {/* Providers */}
      <section className="space-y-4">
        <div className="flex items-center gap-2">
          <Plug className="h-5 w-5 text-primary" />
          <div>
            <h2 className="font-semibold">Cloud & CI Providers</h2>
            <p className="text-sm text-muted-foreground">Connect a platform, then sync an environment's secrets</p>
          </div>
        </div>

        {overviewLoading ? (
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {[0, 1, 2].map((i) => (
              <Skeleton key={i} className="h-48 w-full rounded-2xl" />
            ))}
          </div>
        ) : (
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {providers.map((provider) => {
              const meta = providerMeta[provider.id] || { icon: Server, hint: 'Provider' }
              const Icon = meta.icon
              const integration = integrationsByProvider[provider.id]
              const status = provider.status || 'DISCONNECTED'
              return (
                <Card key={provider.id} className="rounded-2xl flex flex-col">
                  <CardHeader className="pb-3">
                    <div className="flex items-start justify-between">
                      <div className="p-2.5 rounded-xl bg-primary/10">
                        <Icon className="h-5 w-5 text-primary" />
                      </div>
                      <span className={`text-xs font-semibold px-2 py-0.5 rounded-full border ${statusStyles[status] || statusStyles.DISCONNECTED}`}>
                        {status}
                      </span>
                    </div>
                    <CardTitle className="text-base mt-2">{provider.name}</CardTitle>
                    <CardDescription className="text-xs leading-relaxed">{meta.hint}</CardDescription>
                  </CardHeader>
                  <CardContent className="space-y-3 mt-auto">
                    {integration?.lastError && (
                      <p className="text-xs text-red-600 flex items-start gap-1.5">
                        <AlertTriangle className="h-3.5 w-3.5 mt-0.5 flex-shrink-0" />
                        <span className="line-clamp-2">{integration.lastError}</span>
                      </p>
                    )}
                    {integration && (
                      <p className="text-[11px] text-muted-foreground">
                        Last checked {formatDate(integration.lastCheckedAt)}
                        {integration.lastCheckedAt && (
                          <CheckCircle2 className="h-3 w-3 inline ml-1 text-green-500" />
                        )}
                      </p>
                    )}
                    <div className="flex flex-wrap gap-2">
                      {provider.connected ? (
                        <>
                          <Button size="sm" className="brand-gradient text-white border-0" onClick={() => openSync(provider.id)}>
                            <Send className="h-3.5 w-3.5 mr-1.5" />
                            Sync
                          </Button>
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => testMutation.mutate(provider.id)}
                            disabled={testMutation.isPending}
                          >
                            {testMutation.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <RefreshCw className="h-3.5 w-3.5" />}
                            <span className="ml-1.5">Test</span>
                          </Button>
                          <Button
                            size="sm"
                            variant="outline"
                            className="text-destructive hover:text-destructive"
                            onClick={() => disconnectMutation.mutate(provider.id)}
                            disabled={disconnectMutation.isPending}
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </Button>
                        </>
                      ) : (
                        <Button size="sm" className="brand-gradient text-white border-0 w-full" onClick={() => openConnect(provider.id)}>
                          <Plug className="h-3.5 w-3.5 mr-1.5" />
                          Connect
                        </Button>
                      )}
                    </div>
                  </CardContent>
                </Card>
              )
            })}
          </div>
        )}
      </section>

      {/* Export */}
      <section className="glass-panel rounded-2xl p-5 sm:p-6 space-y-4">
        <div className="flex items-center gap-2">
          <Download className="h-5 w-5 text-primary" />
          <div>
            <h2 className="font-semibold">Export environment</h2>
            <p className="text-sm text-muted-foreground">Download decrypted secrets as .env, JSON, or docker-compose text</p>
          </div>
        </div>
        <div className="flex flex-col sm:flex-row gap-3 sm:items-end">
          <div className="flex-1">
            <Label className="mb-1.5 block">Environment</Label>
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
          <div className="sm:w-40">
            <Label className="mb-1.5 block">Format</Label>
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
            <Download className="h-4 w-4 mr-1.5" />
            Download
          </Button>
        </div>
      </section>

      {/* Sync jobs */}
      <section className="space-y-4">
        <div className="flex items-center gap-2">
          <RotateCcw className="h-5 w-5 text-primary" />
          <div>
            <h2 className="font-semibold">Sync jobs</h2>
            <p className="text-sm text-muted-foreground">Background pushes and their retry status</p>
          </div>
        </div>

        {jobsLoading ? (
          <Skeleton className="h-40 w-full rounded-2xl" />
        ) : jobs.length === 0 ? (
          <div className="glass-panel rounded-2xl p-8 text-center text-sm text-muted-foreground">
            No sync jobs yet. Connect a provider and queue a sync to see activity here.
          </div>
        ) : (
          <div className="glass-panel rounded-2xl overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="border-b border-border/70 bg-muted/40">
                  <tr className="text-left text-xs uppercase tracking-wider text-muted-foreground">
                    <th className="px-4 py-3 font-medium">Provider</th>
                    <th className="px-4 py-3 font-medium">Operation</th>
                    <th className="px-4 py-3 font-medium">Target</th>
                    <th className="px-4 py-3 font-medium">Status</th>
                    <th className="px-4 py-3 font-medium">Attempts</th>
                    <th className="px-4 py-3 font-medium">Created</th>
                    <th className="px-4 py-3 font-medium text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {jobs.map((job) => (
                    <tr key={job.id} className="border-b border-border/40 last:border-0">
                      <td className="px-4 py-3 font-medium">{job.provider}</td>
                      <td className="px-4 py-3">{job.operation}</td>
                      <td className="px-4 py-3 font-mono text-xs">{job.target || '—'}</td>
                      <td className="px-4 py-3">
                        <span className={`text-xs font-semibold px-2 py-0.5 rounded-full border ${jobStatusStyles[job.status] || ''}`}>
                          {job.status}
                        </span>
                        {job.lastError && (
                          <p className="text-[11px] text-red-600 mt-1 line-clamp-1" title={job.lastError}>{job.lastError}</p>
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
                            <RotateCcw className="h-3.5 w-3.5 mr-1.5" />
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

      {/* Developer tooling */}
      <section className="space-y-4">
        <div className="flex items-center gap-2">
          <Code2 className="h-5 w-5 text-primary" />
          <div>
            <h2 className="font-semibold">Developer tooling</h2>
            <p className="text-sm text-muted-foreground">SDKs, CLI, and runtime integrations</p>
          </div>
        </div>
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {toolingIntegrations.map((integration) => {
            const Icon = integration.icon
            return (
              <Card key={integration.id} className="hover:border-primary/50 transition-colors group rounded-2xl">
                <CardHeader className="pb-3">
                  <div className="p-2.5 rounded-xl bg-primary/10 group-hover:bg-primary/20 transition-colors w-fit">
                    <Icon className="h-5 w-5 text-primary" />
                  </div>
                  <CardTitle className="text-base mt-2">{integration.name}</CardTitle>
                  <span className="text-xs font-medium px-2 py-0.5 rounded-full w-fit bg-muted text-muted-foreground">
                    {integration.category}
                  </span>
                </CardHeader>
                <CardContent className="space-y-3">
                  <CardDescription className="text-xs leading-relaxed">{integration.description}</CardDescription>
                  <Button
                    variant="outline"
                    size="sm"
                    className="w-full"
                    disabled={integration.docs === '#'}
                    onClick={() => integration.docs !== '#' && window.open(integration.docs, '_blank')}
                  >
                    <ExternalLink className="h-3.5 w-3.5 mr-1.5" />
                    View Docs
                  </Button>
                </CardContent>
              </Card>
            )
          })}
        </div>
      </section>

      {/* Connect dialog */}
      <Dialog open={!!connectProvider} onOpenChange={(open) => !open && setConnectProvider(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Connect {connectProviderName}</DialogTitle>
            <DialogDescription>
              Credentials are validated and then encrypted with the master key before they are stored.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleConnect} className="space-y-4">
            <div>
              <Label htmlFor="token" className="mb-1.5 block">Provider token</Label>
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
              <Label htmlFor="name" className="mb-1.5 block">Display name</Label>
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
              <Button type="submit" className="brand-gradient text-white border-0" disabled={connectMutation.isPending}>
                {connectMutation.isPending && <Loader2 className="h-4 w-4 mr-1.5 animate-spin" />}
                Connect
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Sync dialog */}
      <Dialog open={!!syncProvider} onOpenChange={(open) => !open && setSyncProvider(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Sync secrets to {syncProviderName}</DialogTitle>
            <DialogDescription>
              Choose an environment and a target. Secrets may rotate; the worker retries transient failures automatically.
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
              <Label htmlFor="secretKeys" className="mb-1.5 block">Secret keys (optional)</Label>
              <Input
                id="secretKeys"
                value={syncForm.secretKeys}
                onChange={(e) => setSyncForm((f) => ({ ...f, secretKeys: e.target.value }))}
                placeholder="DATABASE_URL,API_KEY — leave empty to push all"
              />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setSyncProvider(null)}>Cancel</Button>
              <Button type="submit" className="brand-gradient text-white border-0" disabled={syncMutation.isPending}>
                {syncMutation.isPending ? <Loader2 className="h-4 w-4 mr-1.5 animate-spin" /> : <Send className="h-4 w-4 mr-1.5" />}
                Queue sync
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
