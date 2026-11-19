import { useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { cloudIntegrationsAPI } from '../lib/api'
import { toast } from 'sonner'
import { Button } from '../components/ui/button'
import { Input } from '../components/ui/input'
import { Label } from '../components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../components/ui/select'
import {
  Github, Gitlab, Bitbucket, Triangle, Cloud, Boxes, Plug, RefreshCw, Trash2,
  CheckCircle2, Loader2, ExternalLink, Code2, Server, Database, Webhook,
  MessageSquare, Users, Bell, AlertTriangle, Globe, Shield, Lock, Key,
  Container, Workflow, Monitor, Shopping, Smartphone, FileText, Zap, Layers,
} from 'lucide-react'

const categoryIcons = {
  'Source Control': Code2,
  'Deployment': Workflow,
  'Cloud': Cloud,
  'Notifications': Bell,
  'Database': Database,
  'Secrets Management': Lock,
}

const integrationIcons = {
  github: Github,
  gitlab: Gitlab,
  bitbucket: Bitbucket,
  vercel: Triangle,
  netlify: Triangle,
  railway: Triangle,
  flyio: Triangle,
  render: Triangle,
  heroku: Triangle,
  circleci: Workflow,
  jenkins: Workflow,
  travisci: Workflow,
  buildkite: Workflow,
  bitbucket_pipelines: Workflow,
  gitlab_self_managed: Server,
  aws: Cloud,
  google_cloud: Cloud,
  azure: Cloud,
  digitalocean: Cloud,
  cloudflare: Cloud,
  kubernetes: Container,
  terraform_cloud: Layers,
  supabase: Database,
  planetscale: Database,
  hasura_cloud: Database,
  doppler: Lock,
  hashicorp_vault: Lock,
  bitwarden: Shield,
  '1password': Key,
  slack: MessageSquare,
  discord: MessageSquare,
  discord_webhook: Webhook,
  teams: Users,
  mattermost: MessageSquare,
  pagerduty: Bell,
  opsgenie: Bell,
  telegram: Smartphone,
  email: Globe,
  sentry: Monitor,
  shopify: Shopping,
  twilio: Smartphone,
  linear: FileText,
  ghost_cms: FileText,
  appwrite: Server,
  notion: FileText,
  firebase: Cloud,
  google_drive: Globe,
  zapier: Zap,
  postman: Code2,
  checkly: Monitor,
}

export function CloudIntegrations() {
  const { activeWorkspace } = useOutletContext()
  const workspaceId = activeWorkspace?.id
  const queryClient = useQueryClient()

  const [selectedCategory, setSelectedCategory] = useState('All')
  const [searchQuery, setSearchQuery] = useState('')
  const [connectDialog, setConnectDialog] = useState(null)
  const [connectForm, setConnectForm] = useState({ token: '', config: {} })

  const { data: integrationTypes = [], isLoading: typesLoading } = useQuery({
    queryKey: ['integration-types'],
    queryFn: () => cloudIntegrationsAPI.getTypes().then((res) => res.data),
  })

  const { data: connectedIntegrations = [], isLoading: connectedLoading } = useQuery({
    queryKey: ['workspace-integrations', workspaceId],
    queryFn: () => cloudIntegrationsAPI.getWorkspaceIntegrations(workspaceId).then((res) => res.data),
    enabled: !!workspaceId,
  })

  const connectMutation = useMutation({
    mutationFn: (data) => cloudIntegrationsAPI.connect(data),
    onSuccess: () => {
      toast.success('Integration connected successfully')
      queryClient.invalidateQueries({ queryKey: ['workspace-integrations'] })
      setConnectDialog(null)
      setConnectForm({ token: '', config: {} })
    },
    onError: (error) => toast.error(error.response?.data?.message || 'Failed to connect integration'),
  })

  const disconnectMutation = useMutation({
    mutationFn: (data) => cloudIntegrationsAPI.disconnect(data),
    onSuccess: () => {
      toast.success('Integration disconnected')
      queryClient.invalidateQueries({ queryKey: ['workspace-integrations'] })
    },
    onError: (error) => toast.error(error.response?.data?.message || 'Failed to disconnect'),
  })

  const syncMutation = useMutation({
    mutationFn: ({ integrationId, data }) => cloudIntegrationsAPI.sync(integrationId, data),
    onSuccess: () => {
      toast.success('Sync triggered successfully')
      queryClient.invalidateQueries({ queryKey: ['workspace-integrations'] })
    },
    onError: (error) => toast.error(error.response?.data?.message || 'Sync failed'),
  })

  const categories = ['All', 'Source Control', 'Deployment', 'Cloud', 'Notifications', 'Database', 'Secrets Management']
  const connectedMap = new Map(connectedIntegrations.map((i) => [i.integrationType, i]))

  const filteredIntegrations = integrationTypes.filter((integration) => {
    const matchesCategory = selectedCategory === 'All' || integration.category === selectedCategory
    const matchesSearch = integration.name.toLowerCase().includes(searchQuery.toLowerCase())
    return matchesCategory && matchesSearch
  })

  const connectedCount = connectedIntegrations.filter((i) => i.isConnected).length

  const handleConnect = (e) => {
    e.preventDefault()
    if (!connectForm.token.trim()) {
      toast.error('Token is required')
      return
    }
    connectMutation.mutate({
      workspaceId,
      integrationType: connectDialog,
      token: connectForm.token,
      config: connectForm.config,
    })
  }

  const handleDisconnect = (integrationType) => {
    disconnectMutation.mutate({ workspaceId, integrationType })
  }

  const handleSync = (integrationId) => {
    syncMutation.mutate({ integrationId, data: { syncType: 'full' } })
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Integrations</h1>
          <p className="text-muted-foreground">
            Connect your stack to sync secrets and trigger automated workflows
          </p>
        </div>
        <div className="flex items-center gap-2">
          <div className="text-sm text-muted-foreground">
            <span className="font-semibold text-foreground">{connectedCount}</span> Connected
          </div>
        </div>
      </div>

      {/* Search and Filter */}
      <div className="flex flex-col sm:flex-row gap-3">
        <div className="flex-1">
          <Input
            placeholder="Search integrations..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>
        <Select value={selectedCategory} onValueChange={setSelectedCategory}>
          <SelectTrigger className="sm:w-48">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {categories.map((cat) => (
              <SelectItem key={cat} value={cat}>{cat}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Integration Cards */}
      {typesLoading ? (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {[...Array(12)].map((_, i) => (
            <Card key={i} className="rounded-2xl">
              <CardHeader>
                <Skeleton className="h-12 w-12 rounded-xl" />
                <Skeleton className="h-6 w-3/4 mt-3" />
                <Skeleton className="h-4 w-full mt-2" />
              </CardHeader>
            </Card>
          ))}
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {filteredIntegrations.map((integration) => {
            const Icon = integrationIcons[integration.type] || Plug
            const CategoryIcon = categoryIcons[integration.category] || Cloud
            const connected = connectedMap.get(integration.type)
            const isConnected = connected?.isConnected

            return (
              <Card key={integration.type} className="rounded-2xl hover:border-primary/50 transition-colors">
                <CardHeader className="pb-3">
                  <div className="flex items-start justify-between">
                    <div className="p-2.5 rounded-xl bg-primary/10">
                      <Icon className="h-5 w-5 text-primary" />
                    </div>
                    {isConnected ? (
                      <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-green-500/10 text-green-600 border-green-500/30">
                        Connected
                      </span>
                    ) : (
                      <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-muted text-muted-foreground border-border">
                        Not connected
                      </span>
                    )}
                  </div>
                  <CardTitle className="text-base mt-2">{integration.name}</CardTitle>
                  <div className="flex items-center gap-1.5 mt-1">
                    <CategoryIcon className="h-3 w-3 text-muted-foreground" />
                    <CardDescription className="text-xs">{integration.category}</CardDescription>
                  </div>
                </CardHeader>
                <CardContent className="space-y-3">
                  {connected?.syncError && (
                    <p className="text-xs text-red-600 flex items-start gap-1.5">
                      <AlertTriangle className="h-3.5 w-3.5 mt-0.5 flex-shrink-0" />
                      <span className="line-clamp-2">{connected.syncError}</span>
                    </p>
                  )}
                  {connected?.lastSyncedAt && (
                    <p className="text-[11px] text-muted-foreground">
                      Last synced {new Date(connected.lastSyncedAt).toLocaleString()}
                    </p>
                  )}
                  <div className="flex flex-wrap gap-2">
                    {isConnected ? (
                      <>
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => handleSync(connected.id)}
                          disabled={syncMutation.isPending}
                        >
                          {syncMutation.isPending ? (
                            <Loader2 className="h-3.5 w-3.5 animate-spin" />
                          ) : (
                            <RefreshCw className="h-3.5 w-3.5" />
                          )}
                          <span className="ml-1.5">Sync</span>
                        </Button>
                        <Button
                          size="sm"
                          variant="outline"
                          className="text-destructive hover:text-destructive"
                          onClick={() => handleDisconnect(integration.type)}
                          disabled={disconnectMutation.isPending}
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </Button>
                      </>
                    ) : (
                      <Button
                        size="sm"
                        className="brand-gradient text-white border-0 w-full"
                        onClick={() => {
                          setConnectDialog(integration.type)
                          setConnectForm({ token: '', config: {} })
                        }}
                      >
                        <Plug className="h-3.5 w-3.5 mr-1.5" />
                        Connect
                      </Button>
                    )}
                    {integration.url && (
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => window.open(integration.url, '_blank')}
                      >
                        <ExternalLink className="h-3.5 w-3.5" />
                      </Button>
                    )}
                  </div>
                </CardContent>
              </Card>
            )
          })}
        </div>
      )}

      {/* Connect Dialog */}
      <Dialog open={!!connectDialog} onOpenChange={(open) => !open && setConnectDialog(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Connect {integrationTypes.find((i) => i.type === connectDialog)?.name}</DialogTitle>
            <DialogDescription>
              Enter your API token or access key to connect this integration. Credentials are encrypted before storage.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleConnect} className="space-y-4">
            <div>
              <Label htmlFor="token" className="mb-1.5 block">Token / API Key</Label>
              <Input
                id="token"
                type="password"
                autoComplete="off"
                value={connectForm.token}
                onChange={(e) => setConnectForm((f) => ({ ...f, token: e.target.value }))}
                placeholder="Paste your access token"
              />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setConnectDialog(null)}>Cancel</Button>
              <Button
                type="submit"
                className="brand-gradient text-white border-0"
                disabled={connectMutation.isPending}
              >
                {connectMutation.isPending && <Loader2 className="h-4 w-4 mr-1.5 animate-spin" />}
                Connect
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
