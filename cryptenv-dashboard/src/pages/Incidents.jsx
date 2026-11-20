import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useOutletContext } from 'react-router-dom'
import { incidentsAPI, environmentAPI } from '../lib/api'
import { toast } from 'sonner'
import { Button } from '../components/ui/button'
import { Input, Textarea } from '../components/ui/input'
import { Label } from '../components/ui/label'
import { Skeleton } from '../components/ui/skeleton'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../components/ui/select'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog'
import { Plus, ShieldAlert } from 'lucide-react'

const SEVERITIES = ['SEV1', 'SEV2', 'SEV3', 'SEV4']
const TRANSITIONS = {
  OPEN: ['INVESTIGATING', 'CONTAINED', 'RESOLVED'],
  INVESTIGATING: ['CONTAINED', 'RESOLVED'],
  CONTAINED: ['INVESTIGATING', 'RESOLVED'],
  RESOLVED: ['POST_MORTEM', 'OPEN'],
  POST_MORTEM: [],
}

const severityTone = (severity) => {
  if (severity === 'SEV1' || severity === 'SEV2') return 'text-danger'
  return 'text-muted-foreground'
}

export function Incidents() {
  const queryClient = useQueryClient()
  const { activeWorkspace } = useOutletContext() || {}
  const [createOpen, setCreateOpen] = useState(false)
  const [postMortemTarget, setPostMortemTarget] = useState(null)
  const [postMortem, setPostMortem] = useState('')
  const [form, setForm] = useState({
    title: '',
    description: '',
    severity: 'SEV3',
    environmentId: '',
    affectedSecrets: '',
  })

  const { data: incidents = [], isLoading } = useQuery({
    queryKey: ['incidents'],
    queryFn: () => incidentsAPI.list().then((res) => res.data),
  })

  const { data: environments = [] } = useQuery({
    queryKey: ['environments', activeWorkspace?.id],
    queryFn: () => environmentAPI.list(activeWorkspace.id).then((res) => res.data),
    enabled: !!activeWorkspace?.id,
  })

  const envName = (id) => {
    const env = environments.find((e) => e.id === id)
    return env ? env.displayName || env.label || env.name : id ? `Environment ${id}` : '—'
  }

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['incidents'] })

  const createMutation = useMutation({
    mutationFn: () =>
      incidentsAPI.create({
        title: form.title,
        description: form.description || undefined,
        severity: form.severity,
        workspaceId: activeWorkspace?.id,
        environmentId: form.environmentId ? Number(form.environmentId) : undefined,
        affectedSecrets: form.affectedSecrets || undefined,
      }),
    onSuccess: () => {
      refresh()
      toast.success('Incident opened')
      setCreateOpen(false)
      setForm({ title: '', description: '', severity: 'SEV3', environmentId: '', affectedSecrets: '' })
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Could not create incident'),
  })

  const transitionMutation = useMutation({
    mutationFn: ({ id, status }) => incidentsAPI.status(id, status),
    onSuccess: () => {
      refresh()
      toast.success('Incident updated')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Transition failed'),
  })

  const quarantineMutation = useMutation({
    mutationFn: (id) => incidentsAPI.quarantine(id),
    onSuccess: () => {
      refresh()
      toast.success('Environment quarantined')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Could not quarantine environment'),
  })

  const postMortemMutation = useMutation({
    mutationFn: () => incidentsAPI.postMortem(postMortemTarget.id, postMortem),
    onSuccess: () => {
      refresh()
      toast.success('Post-mortem saved')
      setPostMortemTarget(null)
      setPostMortem('')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Could not save post-mortem'),
  })

  return (
    <div className="space-y-8">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="eyebrow">Security</p>
          <h1 className="page-title mt-3">Incidents</h1>
          <p className="page-subtitle">Track, contain and learn from security incidents across your workspaces</p>
        </div>
        <Button onClick={() => setCreateOpen(true)} disabled={!activeWorkspace?.id}>
          <Plus className="h-4 w-4" />
          Open incident
        </Button>
      </header>

      <section className="rounded-lg border border-border">
        <div className="border-b border-border px-5 py-4">
          <h2 className="text-base">Incident log</h2>
          <p className="text-xs text-muted-foreground">
            Lifecycle: open → investigating → contained → resolved → post-mortem
          </p>
        </div>
        {isLoading ? (
          <div className="space-y-px p-2">
            {[1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-20 w-full" />
            ))}
          </div>
        ) : incidents.length === 0 ? (
          <p className="px-5 py-16 text-center text-sm text-muted-foreground">No incidents recorded.</p>
        ) : (
          <ul className="divide-y divide-border/60">
            {incidents.map((incident) => {
              const nextStates = TRANSITIONS[incident.status] || []
              return (
                <li key={incident.id} className="space-y-3 px-5 py-5">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-mono text-xs text-muted-foreground">{incident.incidentNumber}</span>
                    <p className="text-sm">{incident.title}</p>
                    <span className="chip">{incident.status}</span>
                    {incident.severity && (
                      <span className={`text-xs ${severityTone(incident.severity)}`}>{incident.severity}</span>
                    )}
                  </div>
                  {incident.description && (
                    <p className="max-w-3xl text-sm text-muted-foreground">{incident.description}</p>
                  )}
                  <p className="text-xs text-muted-foreground">
                    {envName(incident.environmentId)}
                    {incident.createdAt ? ` · opened ${new Date(incident.createdAt).toLocaleString()}` : ''}
                  </p>
                  {incident.postMortem && (
                    <div className="rounded-md border border-border bg-surface p-3 text-xs text-muted-foreground">
                      <p className="mb-1 text-foreground">Post-mortem</p>
                      <p className="whitespace-pre-wrap">{incident.postMortem}</p>
                    </div>
                  )}
                  <div className="flex flex-wrap gap-2">
                    {nextStates.map((status) => (
                      <Button
                        key={status}
                        size="sm"
                        variant={status === 'RESOLVED' ? 'default' : 'outline'}
                        onClick={() => transitionMutation.mutate({ id: incident.id, status })}
                      >
                        {status.charAt(0) + status.slice(1).toLowerCase().replace('_', ' ')}
                      </Button>
                    ))}
                    {incident.environmentId && !['CONTAINED', 'RESOLVED', 'POST_MORTEM'].includes(incident.status) && (
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => {
                          if (confirm(`Quarantine ${envName(incident.environmentId)}? Writes will require owner/admin or JIT.`)) {
                            quarantineMutation.mutate(incident.id)
                          }
                        }}
                      >
                        <ShieldAlert className="h-3.5 w-3.5" />
                        Quarantine environment
                      </Button>
                    )}
                    {incident.status === 'RESOLVED' && !incident.postMortem && (
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => {
                          setPostMortemTarget(incident)
                          setPostMortem('')
                        }}
                      >
                        Write post-mortem
                      </Button>
                    )}
                  </div>
                </li>
              )
            })}
          </ul>
        )}
      </section>

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Open incident</DialogTitle>
            <DialogDescription>
              Attach an environment to enable one-click quarantine of its writes.
            </DialogDescription>
          </DialogHeader>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              createMutation.mutate()
            }}
            className="space-y-4"
          >
            <div className="space-y-2">
              <Label htmlFor="incident-title">Title</Label>
              <Input
                id="incident-title"
                value={form.title}
                onChange={(e) => setForm({ ...form, title: e.target.value })}
                required
              />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label>Severity</Label>
                <Select value={form.severity} onValueChange={(severity) => setForm({ ...form, severity })}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {SEVERITIES.map((severity) => (
                      <SelectItem key={severity} value={severity}>
                        {severity}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>Environment (optional)</Label>
                <Select
                  value={form.environmentId}
                  onValueChange={(environmentId) => setForm({ ...form, environmentId })}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="None" />
                  </SelectTrigger>
                  <SelectContent>
                    {environments.map((env) => (
                      <SelectItem key={env.id} value={String(env.id)}>
                        {env.displayName || env.label || env.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="incident-description">Description</Label>
              <Textarea
                id="incident-description"
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
                rows={3}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="incident-affected">Affected secrets</Label>
              <Input
                id="incident-affected"
                value={form.affectedSecrets}
                onChange={(e) => setForm({ ...form, affectedSecrets: e.target.value })}
                placeholder="DATABASE_URL, STRIPE_KEY"
              />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setCreateOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={createMutation.isPending}>
                {createMutation.isPending ? 'Opening…' : 'Open incident'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={!!postMortemTarget} onOpenChange={(open) => !open && setPostMortemTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Post-mortem</DialogTitle>
            <DialogDescription>{postMortemTarget?.incidentNumber} · {postMortemTarget?.title}</DialogDescription>
          </DialogHeader>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              postMortemMutation.mutate()
            }}
            className="space-y-4"
          >
            <Textarea
              value={postMortem}
              onChange={(e) => setPostMortem(e.target.value)}
              rows={8}
              placeholder="Timeline, root cause, impact, remediation and follow-ups."
              required
            />
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setPostMortemTarget(null)}>
                Cancel
              </Button>
              <Button type="submit" disabled={postMortemMutation.isPending}>
                {postMortemMutation.isPending ? 'Saving…' : 'Save post-mortem'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
