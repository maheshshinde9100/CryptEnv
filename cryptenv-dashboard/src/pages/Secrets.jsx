import { useMemo, useState } from 'react'
import { useNavigate, useOutletContext } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { secretsAPI, environmentAPI } from '../lib/api'
import { toast } from 'sonner'
import { Button } from '../components/ui/button'
import { Input } from '../components/ui/input'
import { Skeleton } from '../components/ui/skeleton'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../components/ui/select'
import { Search, Plus, Eye, EyeOff, Trash2, Edit, Lock, Copy } from 'lucide-react'

export function Secrets() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { activeWorkspace } = useOutletContext() || {}
  const [searchQuery, setSearchQuery] = useState('')
  const [visibleSecrets, setVisibleSecrets] = useState({})
  const [envFilter, setEnvFilter] = useState('all')

  const workspaceId = activeWorkspace?.id || Number(localStorage.getItem('activeWorkspaceId') || 0)

  const { data: environments = [] } = useQuery({
    queryKey: ['environments', workspaceId],
    queryFn: () => environmentAPI.list(workspaceId).then((res) => res.data),
    enabled: !!workspaceId,
  })

  const { data: secrets = [], isLoading } = useQuery({
    queryKey: ['secrets', envFilter, workspaceId],
    queryFn: async () => {
      if (envFilter !== 'all') {
        return secretsAPI.listByEnvironment(Number(envFilter)).then((r) => r.data)
      }
      const all = await secretsAPI.list().then((r) => r.data)
      if (!workspaceId || !environments.length) return all
      const envIds = new Set(environments.map((e) => e.id))
      return all.filter((s) => envIds.has(s.environmentId))
    },
    enabled: envFilter === 'all' || !!envFilter,
  })

  const deleteMutation = useMutation({
    mutationFn: (key) => secretsAPI.delete(key),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['secrets'] })
      toast.success('Secret deleted')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Delete failed'),
  })

  const filtered = useMemo(
    () =>
      secrets.filter((s) => s.key?.toLowerCase().includes(searchQuery.toLowerCase())),
    [secrets, searchQuery]
  )

  const envName = (id) => environments.find((e) => e.id === id)?.name || '—'

  const copyValue = async (value) => {
    await navigator.clipboard.writeText(value || '')
    toast.success('Copied to clipboard')
  }

  return (
    <div className="space-y-6 max-w-6xl">
      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4">
        <div>
          <div className="inline-flex items-center gap-2 rounded-full border border-border/70 bg-card/60 px-3 py-1 text-xs text-muted-foreground mb-3">
            <Lock className="h-3.5 w-3.5 text-primary" />
            Values stored encrypted at rest · reveal with eye icon
          </div>
          <h1 className="text-3xl font-bold tracking-tight">Secrets</h1>
          <p className="text-muted-foreground mt-1">
            {activeWorkspace?.name || 'Workspace'} — manage encrypted keys for each environment
          </p>
        </div>
        <Button className="brand-gradient border-0 text-white" onClick={() => navigate('/secrets/new')}>
          <Plus className="mr-2 h-4 w-4" />
          Add secret
        </Button>
      </div>

      <div className="glass-panel rounded-2xl p-4 sm:p-5 space-y-4">
        <div className="flex flex-col sm:flex-row gap-3">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Search by key…"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-10"
            />
          </div>
          <Select value={envFilter} onValueChange={setEnvFilter}>
            <SelectTrigger className="w-full sm:w-52">
              <SelectValue placeholder="Environment" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All environments</SelectItem>
              {environments.map((env) => (
                <SelectItem key={env.id} value={String(env.id)}>
                  {env.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {isLoading ? (
          <div className="space-y-2">
            {[1, 2, 3, 4].map((i) => (
              <Skeleton key={i} className="h-14 w-full rounded-xl" />
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <div className="py-14 text-center text-muted-foreground">
            {searchQuery ? 'No secrets match your search' : 'No secrets yet — add your first encrypted key'}
          </div>
        ) : (
          <div className="space-y-2">
            {filtered.map((secret) => {
              const shown = !!visibleSecrets[secret.key]
              const masked =
                secret.value && !String(secret.value).startsWith('[')
                  ? '•'.repeat(Math.min(24, Math.max(12, String(secret.value).length)))
                  : '••••••••••••••••••••'
              return (
                <div
                  key={`${secret.environmentId}-${secret.key}`}
                  className="flex flex-col sm:flex-row sm:items-center gap-3 rounded-xl border border-border/70 bg-background/40 px-4 py-3 hover:border-primary/30 transition-colors"
                >
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <p className="font-semibold font-mono text-sm truncate">{secret.key}</p>
                      <span className="text-[10px] uppercase tracking-wide rounded-full bg-muted px-2 py-0.5 text-muted-foreground">
                        {envName(secret.environmentId)}
                      </span>
                      {secret.encrypted && (
                        <span className="text-[10px] rounded-full bg-cyan-500/15 text-cyan-400 px-2 py-0.5">
                          AES-GCM
                        </span>
                      )}
                    </div>
                    <code className="mt-1 block text-xs text-muted-foreground font-mono truncate">
                      {shown ? secret.value : masked}
                    </code>
                  </div>
                  <div className="flex items-center gap-1 self-end sm:self-center">
                    <Button
                      variant="ghost"
                      size="icon"
                      title={shown ? 'Hide value' : 'Reveal decrypted value'}
                      onClick={() =>
                        setVisibleSecrets((prev) => ({ ...prev, [secret.key]: !prev[secret.key] }))
                      }
                    >
                      {shown ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      title="Copy value"
                      onClick={() => copyValue(secret.value)}
                      disabled={!secret.value || String(secret.value).startsWith('[')}
                    >
                      <Copy className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() =>
                        navigate(`/secrets/${encodeURIComponent(secret.key)}/edit`, {
                          state: { environmentId: secret.environmentId },
                        })
                      }
                    >
                      <Edit className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => {
                        if (confirm(`Delete secret "${secret.key}"?`)) deleteMutation.mutate(secret.key)
                      }}
                    >
                      <Trash2 className="h-4 w-4 text-destructive" />
                    </Button>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
