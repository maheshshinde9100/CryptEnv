import { useMemo, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useOutletContext } from 'react-router-dom'
import { approvalsAPI, jitAPI, environmentAPI } from '../lib/api'
import { toast } from 'sonner'
import { Button } from '../components/ui/button'
import { Input, Textarea } from '../components/ui/input'
import { Label } from '../components/ui/label'
import { Skeleton } from '../components/ui/skeleton'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../components/ui/select'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog'
import { Check, Clock, KeyRound, Plus, ShieldCheck, X } from 'lucide-react'

const statusTone = (status) => {
  if (['PENDING', 'REQUESTED'].includes(status)) return 'chip'
  if (['APPROVED', 'ACTIVE', 'EXECUTED'].includes(status)) return 'chip'
  return 'chip'
}

export function AccessRequests() {
  const queryClient = useQueryClient()
  const { activeWorkspace } = useOutletContext() || {}
  const [tab, setTab] = useState('approvals')
  const [raiseOpen, setRaiseOpen] = useState(false)
  const [jitOpen, setJitOpen] = useState(false)
  const [decision, setDecision] = useState(null)
  const [note, setNote] = useState('')
  const [action, setAction] = useState('SECRET_DELETE')
  const [envId, setEnvId] = useState('')
  const [secretKey, setSecretKey] = useState('')
  const [reason, setReason] = useState('')
  const [ttlHours, setTtlHours] = useState('24')
  const [jitEnvId, setJitEnvId] = useState('')
  const [jitReason, setJitReason] = useState('')
  const [jitHours, setJitHours] = useState('2')

  const workspaceId = activeWorkspace?.id

  const { data: environments = [] } = useQuery({
    queryKey: ['environments', workspaceId],
    queryFn: () => environmentAPI.list(workspaceId).then((res) => res.data),
    enabled: !!workspaceId,
  })

  const { data: supported = [] } = useQuery({
    queryKey: ['approval-actions'],
    queryFn: () => approvalsAPI.supported().then((res) => res.data),
  })

  const { data: mine = [], isLoading: mineLoading } = useQuery({
    queryKey: ['approvals', 'mine'],
    queryFn: () => approvalsAPI.mine().then((res) => res.data),
  })

  const { data: pending = [], isLoading: pendingLoading } = useQuery({
    queryKey: ['approvals', 'pending'],
    queryFn: () => approvalsAPI.pending().then((res) => res.data),
  })

  const { data: jitRequests = [], isLoading: jitLoading } = useQuery({
    queryKey: ['jit', workspaceId],
    queryFn: () => jitAPI.list(workspaceId).then((res) => res.data),
    enabled: !!workspaceId,
  })

  const envName = (id) => {
    const env = environments.find((e) => e.id === id)
    return env ? env.displayName || env.label || env.name : `Environment ${id}`
  }

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['approvals'] })
    queryClient.invalidateQueries({ queryKey: ['jit', workspaceId] })
  }

  const raiseMutation = useMutation({
    mutationFn: () =>
      approvalsAPI.request({
        action,
        workspaceId,
        resourceType: 'SECRET',
        resourceId: secretKey,
        payload: { environmentId: Number(envId), key: secretKey },
        reason,
        ttlHours: Number(ttlHours) || undefined,
      }),
    onSuccess: () => {
      refresh()
      toast.success('Approval request raised')
      setRaiseOpen(false)
      setSecretKey('')
      setReason('')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Could not raise request'),
  })

  const decideMutation = useMutation({
    mutationFn: ({ id, approve }) =>
      approve ? approvalsAPI.approve(id, note || undefined) : approvalsAPI.reject(id, note || undefined),
    onSuccess: (_, vars) => {
      refresh()
      toast.success(vars.approve ? 'Request approved and executed' : 'Request rejected')
      setDecision(null)
      setNote('')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Decision failed'),
  })

  const cancelMutation = useMutation({
    mutationFn: (id) => approvalsAPI.cancel(id),
    onSuccess: () => {
      refresh()
      toast.success('Request cancelled')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Could not cancel'),
  })

  const jitMutation = useMutation({
    mutationFn: () =>
      jitAPI.request({
        workspaceId,
        environmentId: Number(jitEnvId),
        justification: jitReason,
        durationHours: Number(jitHours),
      }),
    onSuccess: () => {
      refresh()
      toast.success('JIT access requested')
      setJitOpen(false)
      setJitReason('')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Could not request access'),
  })

  const jitDecision = useMutation({
    mutationFn: ({ id, approve }) => (approve ? jitAPI.approve(id) : jitAPI.deny(id)),
    onSuccess: (_, vars) => {
      refresh()
      toast.success(vars.approve ? 'JIT access granted' : 'JIT request denied')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Action failed'),
  })

  const actions = useMemo(() => (supported.length ? supported.map((s) => s.action) : ['SECRET_DELETE']), [supported])

  return (
    <div className="space-y-8">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="eyebrow">Access</p>
          <h1 className="page-title mt-3">Access requests</h1>
          <p className="page-subtitle">
            Four-eyes approvals for destructive actions and time-bound JIT access to protected environments
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => setJitOpen(true)} disabled={!workspaceId || environments.length === 0}>
            <Clock className="h-4 w-4" />
            Request JIT
          </Button>
          <Button onClick={() => setRaiseOpen(true)} disabled={!workspaceId}>
            <Plus className="h-4 w-4" />
            Raise approval
          </Button>
        </div>
      </header>

      <div className="inline-flex rounded-md border border-border p-1">
        {[
          ['approvals', 'Four-eyes approvals'],
          ['jit', 'JIT access'],
        ].map(([value, label]) => (
          <button
            key={value}
            type="button"
            onClick={() => setTab(value)}
            className={`rounded-sm px-3 py-1.5 text-sm transition-colors ${
              tab === value ? 'bg-white/[0.08] text-foreground' : 'text-muted-foreground hover:text-foreground'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === 'approvals' ? (
        <div className="grid gap-6 lg:grid-cols-2">
          <section className="rounded-lg border border-border">
            <div className="flex items-center gap-2 border-b border-border px-5 py-4">
              <ShieldCheck className="h-4 w-4 text-muted-foreground" />
              <div>
                <h2 className="text-base">Awaiting your decision</h2>
                <p className="text-xs text-muted-foreground">You cannot approve your own requests</p>
              </div>
            </div>
            {pendingLoading ? (
              <div className="space-y-px p-2">
                <Skeleton className="h-20 w-full" />
              </div>
            ) : pending.length === 0 ? (
              <p className="px-5 py-12 text-center text-sm text-muted-foreground">Nothing pending.</p>
            ) : (
              <ul className="divide-y divide-border/60">
                {pending.map((req) => (
                  <li key={req.id} className="space-y-3 px-5 py-4">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="text-sm">{req.action}</p>
                      <span className={statusTone(req.status)}>{req.status}</span>
                      <span className="text-xs text-muted-foreground">by user #{req.requesterId}</span>
                    </div>
                    <p className="text-xs text-muted-foreground">
                      Resource {req.resourceType}
                      {req.resourceId ? ` · ${req.resourceId}` : ''}
                      {req.expiresAt ? ` · expires ${new Date(req.expiresAt).toLocaleString()}` : ''}
                    </p>
                    {req.reason && <p className="text-sm text-ink">{req.reason}</p>}
                    <div className="flex gap-2">
                      <Button size="sm" onClick={() => setDecision({ req, approve: true })}>
                        <Check className="h-3.5 w-3.5" />
                        Approve
                      </Button>
                      <Button size="sm" variant="outline" onClick={() => setDecision({ req, approve: false })}>
                        <X className="h-3.5 w-3.5" />
                        Reject
                      </Button>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="rounded-lg border border-border">
            <div className="border-b border-border px-5 py-4">
              <h2 className="text-base">Your requests</h2>
              <p className="text-xs text-muted-foreground">Requests you raised across your workspaces</p>
            </div>
            {mineLoading ? (
              <div className="space-y-px p-2">
                <Skeleton className="h-20 w-full" />
              </div>
            ) : mine.length === 0 ? (
              <p className="px-5 py-12 text-center text-sm text-muted-foreground">You have not raised any requests.</p>
            ) : (
              <ul className="divide-y divide-border/60">
                {mine.map((req) => (
                  <li key={req.id} className="flex flex-col gap-2 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <p className="text-sm">{req.action}</p>
                        <span className={statusTone(req.status)}>{req.status}</span>
                      </div>
                      <p className="mt-1 truncate text-xs text-muted-foreground">
                        {req.reason || 'No reason'} · {req.createdAt ? new Date(req.createdAt).toLocaleString() : ''}
                      </p>
                      {req.resultDetail && <p className="mt-1 text-xs text-danger">{req.resultDetail}</p>}
                    </div>
                    {req.status === 'PENDING' && (
                      <Button size="sm" variant="outline" onClick={() => cancelMutation.mutate(req.id)}>
                        Cancel
                      </Button>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      ) : (
        <section className="rounded-lg border border-border">
          <div className="flex items-center gap-2 border-b border-border px-5 py-4">
            <KeyRound className="h-4 w-4 text-muted-foreground" />
            <div>
              <h2 className="text-base">JIT access</h2>
              <p className="text-xs text-muted-foreground">
                {activeWorkspace?.name || 'Workspace'} · grants expire automatically
              </p>
            </div>
          </div>
          {!workspaceId ? (
            <p className="px-5 py-16 text-center text-sm text-muted-foreground">Select a workspace.</p>
          ) : jitLoading ? (
            <div className="space-y-px p-2">
              <Skeleton className="h-16 w-full" />
            </div>
          ) : jitRequests.length === 0 ? (
            <p className="px-5 py-16 text-center text-sm text-muted-foreground">
              No JIT requests yet. Protected environments require an approved grant.
            </p>
          ) : (
            <ul className="divide-y divide-border/60">
              {jitRequests.map((request) => (
                <li key={request.id} className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="text-sm">{envName(request.environmentId)}</p>
                      <span className={statusTone(request.status)}>{request.status}</span>
                      <span className="text-xs text-muted-foreground">
                        {request.durationHours}h · user #{request.requesterId}
                      </span>
                    </div>
                    <p className="mt-1 text-xs text-muted-foreground">{request.justification}</p>
                    {request.endsAt && (
                      <p className="mt-0.5 text-xs text-muted-foreground">
                        Ends {new Date(request.endsAt).toLocaleString()}
                      </p>
                    )}
                  </div>
                  {request.status === 'PENDING' && (
                    <div className="flex gap-2 self-end sm:self-center">
                      <Button size="sm" onClick={() => jitDecision.mutate({ id: request.id, approve: true })}>
                        Approve
                      </Button>
                      <Button size="sm" variant="outline" onClick={() => jitDecision.mutate({ id: request.id, approve: false })}>
                        Deny
                      </Button>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      <Dialog open={raiseOpen} onOpenChange={setRaiseOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Raise approval request</DialogTitle>
            <DialogDescription>
              A different owner or admin must approve before the action executes.
            </DialogDescription>
          </DialogHeader>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              raiseMutation.mutate()
            }}
            className="space-y-4"
          >
            <div className="space-y-2">
              <Label>Action</Label>
              <Select value={action} onValueChange={setAction}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {actions.map((value) => (
                    <SelectItem key={value} value={value}>
                      {value}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label>Environment</Label>
                <Select value={envId} onValueChange={setEnvId}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select environment" />
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
              <div className="space-y-2">
                <Label>Secret key</Label>
                <Input
                  value={secretKey}
                  onChange={(e) => setSecretKey(e.target.value.toUpperCase())}
                  className="font-mono"
                  placeholder="DATABASE_URL"
                  required
                />
              </div>
            </div>
            <div className="space-y-2">
              <Label>Reason</Label>
              <Textarea
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                rows={3}
                placeholder="Why is this action needed?"
                required
              />
            </div>
            <div className="space-y-2">
              <Label>Expires in (hours)</Label>
              <Input type="number" min={1} max={168} value={ttlHours} onChange={(e) => setTtlHours(e.target.value)} />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setRaiseOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={raiseMutation.isPending || !envId || !secretKey}>
                {raiseMutation.isPending ? 'Submitting…' : 'Raise request'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={!!decision} onOpenChange={(open) => !open && setDecision(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{decision?.approve ? 'Approve request' : 'Reject request'}</DialogTitle>
            <DialogDescription>
              {decision?.approve
                ? 'Approving executes the action immediately and writes it to the audit log.'
                : 'The requester will see your note.'}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label>Note (optional)</Label>
              <Textarea value={note} onChange={(e) => setNote(e.target.value)} rows={3} />
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setDecision(null)}>
                Cancel
              </Button>
              <Button
                onClick={() => decideMutation.mutate({ id: decision.req.id, approve: decision.approve })}
                disabled={decideMutation.isPending}
              >
                {decideMutation.isPending ? 'Saving…' : decision?.approve ? 'Approve and execute' : 'Reject'}
              </Button>
            </DialogFooter>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={jitOpen} onOpenChange={setJitOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Request JIT access</DialogTitle>
            <DialogDescription>
              Temporary access to a protected environment. An owner or admin must approve.
            </DialogDescription>
          </DialogHeader>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              jitMutation.mutate()
            }}
            className="space-y-4"
          >
            <div className="space-y-2">
              <Label>Environment</Label>
              <Select value={jitEnvId} onValueChange={setJitEnvId}>
                <SelectTrigger>
                  <SelectValue placeholder="Select environment" />
                </SelectTrigger>
                <SelectContent>
                  {environments.map((env) => (
                    <SelectItem key={env.id} value={String(env.id)}>
                      {env.displayName || env.label || env.name}
                      {env.isProtected ? ' · protected' : ''}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>Justification</Label>
              <Textarea value={jitReason} onChange={(e) => setJitReason(e.target.value)} rows={3} required />
            </div>
            <div className="space-y-2">
              <Label>Duration (hours, 1–24)</Label>
              <Input type="number" min={1} max={24} value={jitHours} onChange={(e) => setJitHours(e.target.value)} />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setJitOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={jitMutation.isPending || !jitEnvId}>
                {jitMutation.isPending ? 'Requesting…' : 'Request access'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
