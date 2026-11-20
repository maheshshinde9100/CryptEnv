import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useOutletContext } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { secretsAPI, environmentAPI, authAPI } from '../lib/api'
import { toast } from 'sonner'
import { Button } from '../components/ui/button'
import { Input } from '../components/ui/input'
import { Label } from '../components/ui/label'
import { Skeleton } from '../components/ui/skeleton'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../components/ui/select'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog'
import { Search, Plus, Eye, EyeOff, Trash2, Edit, Copy, History, Download, ShieldCheck } from 'lucide-react'

export function Secrets() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { activeWorkspace, user } = useOutletContext() || {}
  const [searchQuery, setSearchQuery] = useState('')
  const [envFilter, setEnvFilter] = useState('all')
  const [revealed, setRevealed] = useState({})
  const [revealTarget, setRevealTarget] = useState(null)
  const [totpCode, setTotpCode] = useState('')
  const [stepUpToken, setStepUpToken] = useState('')
  const [revealPending, setRevealPending] = useState(false)
  const revealTimers = useRef(new Map())

  useEffect(() => () => {
    revealTimers.current.forEach((timer) => clearTimeout(timer))
  }, [])

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
    mutationFn: ({ environmentId, key }) => secretsAPI.deleteFromEnvironment(environmentId, key),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['secrets'] })
      toast.success('Secret deleted')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Delete failed'),
  })

  const filtered = useMemo(
    () => secrets.filter((s) => s.key?.toLowerCase().includes(searchQuery.toLowerCase())),
    [secrets, searchQuery]
  )

  const envName = (id) => {
    const env = environments.find((e) => e.id === id)
    return env ? env.displayName || env.label || env.name : '—'
  }

  const secretId = (secret) => `${secret.environmentId}:${secret.key}`

  const hideSecret = (secret) => {
    const id = secretId(secret)
    const timer = revealTimers.current.get(id)
    if (timer) clearTimeout(timer)
    revealTimers.current.delete(id)
    setRevealed((previous) => {
      const next = { ...previous }
      delete next[id]
      return next
    })
  }

  const storeRevealed = (secret, value) => {
    const id = secretId(secret)
    setRevealed((previous) => ({ ...previous, [id]: value }))
    revealTimers.current.set(
      id,
      setTimeout(() => hideSecret(secret), 30_000)
    )
  }

  const requestReveal = async (secret) => {
    if (revealed[secretId(secret)]) {
      hideSecret(secret)
      return
    }
    if (user?.mfaEnabled) {
      setRevealTarget(secret)
      setTotpCode('')
      try {
        const res = await authAPI.stepUp()
        setStepUpToken(res.data.stepUpToken)
      } catch (e) {
        toast.error(e.response?.data?.message || 'Could not start MFA step-up')
      }
      return
    }
    try {
      const res = await secretsAPI.reveal(secret.environmentId, secret.key)
      storeRevealed(secret, res.data.value)
    } catch (e) {
      toast.error(e.response?.data?.message || 'Reveal failed')
    }
  }

  const confirmMfaReveal = async () => {
    if (!revealTarget) return
    setRevealPending(true)
    try {
      await authAPI.verifyStepUp(stepUpToken, totpCode.trim())
      const res = await secretsAPI.reveal(revealTarget.environmentId, revealTarget.key, {
        stepUpToken,
        totpCode: totpCode.trim(),
      })
      storeRevealed(revealTarget, res.data.value)
      setRevealTarget(null)
      setTotpCode('')
      setStepUpToken('')
    } catch (e) {
      toast.error(e.response?.data?.message || 'Invalid verification code')
    } finally {
      setRevealPending(false)
    }
  }

  const copyValue = async (secret) => {
    const value = revealed[secretId(secret)]
    if (!value) {
      toast.error('Reveal the value first')
      return
    }
    await navigator.clipboard.writeText(value)
    setTimeout(() => {
      navigator.clipboard.writeText('').catch(() => {})
    }, 30_000)
    toast.success('Copied. Clipboard clears in 30 seconds.')
  }

  const exportEnvironment = async () => {
    if (envFilter === 'all') {
      toast.error('Select a single environment to export')
      return
    }
    try {
      const res = await secretsAPI.export(Number(envFilter), 'ENV')
      const blob = new Blob([res.data], { type: 'text/plain' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `secrets-${envFilter}.env`
      link.click()
      URL.revokeObjectURL(url)
      toast.success('Export downloaded')
    } catch (e) {
      toast.error(e.response?.data?.message || 'Export failed')
    }
  }

  const mask = (secret) => {
    const length = secret.value ? Math.min(28, Math.max(12, String(secret.value).length)) : 18
    return '•'.repeat(length)
  }

  return (
    <div className="space-y-8">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="eyebrow">Secrets</p>
          <h1 className="page-title mt-3">Encrypted keys</h1>
          <p className="page-subtitle">
            {activeWorkspace?.name || 'Workspace'} · values are decrypted only when revealed and every reveal is audited
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={exportEnvironment}>
            <Download className="h-4 w-4" />
            Export .env
          </Button>
          <Button onClick={() => navigate('/secrets/new')}>
            <Plus className="h-4 w-4" />
            Add secret
          </Button>
        </div>
      </header>

      <div className="rounded-lg border border-border">
        <div className="flex flex-col gap-3 border-b border-border p-4 sm:flex-row">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Search by key"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-10"
            />
          </div>
          <Select value={envFilter} onValueChange={setEnvFilter}>
            <SelectTrigger className="w-full sm:w-56">
              <SelectValue placeholder="Environment" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All environments</SelectItem>
              {environments.map((env) => (
                <SelectItem key={env.id} value={String(env.id)}>
                  {env.displayName || env.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {isLoading ? (
          <div className="space-y-px">
            {[1, 2, 3, 4].map((i) => (
              <Skeleton key={i} className="h-16 w-full rounded-none" />
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <div className="px-4 py-16 text-center text-sm text-muted-foreground">
            {searchQuery ? 'No secrets match your search.' : 'No secrets yet. Add your first encrypted key.'}
          </div>
        ) : (
          <ul className="divide-y divide-border/60">
            {filtered.map((secret) => {
              const shown = revealed[secretId(secret)]
              return (
                <li
                  key={`${secret.environmentId}-${secret.key}`}
                  className="flex flex-col gap-3 px-4 py-4 sm:flex-row sm:items-center sm:gap-4"
                >
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="truncate font-mono text-sm">{secret.key}</p>
                      <span className="chip">{envName(secret.environmentId)}</span>
                      {secret.encrypted && <span className="chip">AES-256-GCM</span>}
                      {secret.autoRotate && <span className="chip">Rotating</span>}
                    </div>
                    <code className="mt-1.5 block truncate font-mono text-xs text-muted-foreground">
                      {shown || mask(secret)}
                    </code>
                  </div>
                  <div className="flex items-center gap-1 self-end sm:self-center">
                    <Button
                      variant="ghost"
                      size="icon"
                      title={shown ? 'Hide' : 'Reveal for 30 seconds'}
                      onClick={() => requestReveal(secret)}
                    >
                      {shown ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </Button>
                    <Button variant="ghost" size="icon" title="Copy" onClick={() => copyValue(secret)}>
                      <Copy className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      title="Version history"
                      onClick={() =>
                        navigate(`/secrets/${encodeURIComponent(secret.key)}/history`, {
                          state: { environmentId: secret.environmentId },
                        })
                      }
                    >
                      <History className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      title="Edit"
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
                      title="Delete"
                      onClick={() => {
                        if (confirm(`Delete secret "${secret.key}" from ${envName(secret.environmentId)}?`)) {
                          deleteMutation.mutate({ environmentId: secret.environmentId, key: secret.key })
                        }
                      }}
                    >
                      <Trash2 className="h-4 w-4 text-danger" />
                    </Button>
                  </div>
                </li>
              )
            })}
          </ul>
        )}
      </div>

      <p className="flex items-center gap-2 text-xs text-muted-foreground">
        <ShieldCheck className="h-3.5 w-3.5" />
        Reveals expire automatically after 30 seconds and are written to the audit log.
      </p>

      <Dialog open={!!revealTarget} onOpenChange={(open) => !open && setRevealTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Confirm it is you</DialogTitle>
            <DialogDescription>
              Your account has two-factor authentication enabled. Enter the 6-digit code from your authenticator app
              to reveal {revealTarget?.key}.
            </DialogDescription>
          </DialogHeader>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              confirmMfaReveal()
            }}
            className="space-y-4"
          >
            <div className="space-y-2">
              <Label htmlFor="totp">Authenticator code</Label>
              <Input
                id="totp"
                value={totpCode}
                onChange={(e) => setTotpCode(e.target.value)}
                inputMode="numeric"
                autoComplete="one-time-code"
                placeholder="123456"
                className="font-mono tracking-[0.4em]"
                required
              />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setRevealTarget(null)}>
                Cancel
              </Button>
              <Button type="submit" disabled={revealPending || totpCode.trim().length < 6}>
                {revealPending ? 'Verifying…' : 'Reveal value'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
