import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { secretLifecycleAPI, secretsAPI } from '../lib/api'
import { toast } from 'sonner'
import { Button } from '../components/ui/button'
import { Skeleton } from '../components/ui/skeleton'
import { RefreshCw } from 'lucide-react'

const rotationStatus = (secret) => {
  const reference = secret.nextRotationAt || secret.lastRotatedAt || secret.createdAt
  if (!reference) return 'unknown'
  if (secret.nextRotationAt && new Date(secret.nextRotationAt).getTime() <= Date.now()) return 'overdue'
  const interval = secret.rotationIntervalDays || 30
  const days = Math.floor((Date.now() - new Date(reference).getTime()) / (1000 * 60 * 60 * 24))
  if (!secret.nextRotationAt && days > interval * 3) return 'overdue'
  if (!secret.nextRotationAt && days > interval) return 'due'
  return 'ok'
}

export function SecretRotation() {
  const queryClient = useQueryClient()
  const [rotatingId, setRotatingId] = useState(null)

  const { data: secrets = [], isLoading } = useQuery({
    queryKey: ['secrets'],
    queryFn: () => secretsAPI.list().then((res) => res.data),
  })

  const rotateMutation = useMutation({
    mutationFn: async (secret) => {
      setRotatingId(`${secret.environmentId}:${secret.key}`)
      return secretsAPI.rotate(secret.environmentId, secret.key)
    },
    onSuccess: (_, secret) => {
      queryClient.invalidateQueries({ queryKey: ['secrets'] })
      toast.success(`Rotated ${secret.key}`)
      setRotatingId(null)
    },
    onError: (_, secret) => {
      toast.error(`Failed to rotate ${secret.key}`)
      setRotatingId(null)
    },
  })

  const autoRotateMutation = useMutation({
    mutationFn: async (secret) => {
      if (secret.autoRotate) return secretLifecycleAPI.disableAutoRotate(secret.key)
      if (!secret.rotationIntervalDays) await secretLifecycleAPI.setRotationInterval(secret.key, 30)
      return secretLifecycleAPI.enableAutoRotate(secret.key)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['secrets'] })
      toast.success('Rotation policy updated')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Could not update rotation policy'),
  })

  const counts = {
    ok: secrets.filter((s) => rotationStatus(s) === 'ok').length,
    due: secrets.filter((s) => rotationStatus(s) === 'due').length,
    overdue: secrets.filter((s) => rotationStatus(s) === 'overdue').length,
  }

  return (
    <div className="space-y-8">
      <header>
        <p className="eyebrow">Security</p>
        <h1 className="page-title mt-3">Rotation</h1>
        <p className="page-subtitle">Rotate on demand or let the scheduler renew secrets on their policy</p>
      </header>

      <section className="grid divide-y divide-border rounded-lg border border-border sm:grid-cols-3 sm:divide-y-0 sm:divide-x">
        {[
          ['Current', counts.ok],
          ['Due', counts.due],
          ['Overdue', counts.overdue],
        ].map(([label, value]) => (
          <div key={label} className="p-5">
            <p className="text-xs text-muted-foreground">{label}</p>
            <p className="mt-3 text-2xl font-light tracking-[-0.02em]">{value}</p>
          </div>
        ))}
      </section>

      <section className="rounded-lg border border-border">
        <div className="border-b border-border px-5 py-4">
          <h2 className="text-base">Secrets</h2>
          <p className="text-xs text-muted-foreground">
            Manual rotation generates a type-aware value, re-encrypts it and records a new version
          </p>
        </div>
        {isLoading ? (
          <div className="space-y-px p-2">
            {[1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-16 w-full" />
            ))}
          </div>
        ) : secrets.length === 0 ? (
          <p className="px-5 py-16 text-center text-sm text-muted-foreground">No secrets to rotate yet.</p>
        ) : (
          <ul className="divide-y divide-border/60">
            {secrets.map((secret) => {
              const status = rotationStatus(secret)
              const id = `${secret.environmentId}:${secret.key}`
              return (
                <li key={id} className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="truncate font-mono text-sm">{secret.key}</p>
                      <span className={status === 'overdue' ? 'chip text-danger' : 'chip'}>
                        {status === 'ok' ? 'Current' : status === 'due' ? 'Due' : status === 'overdue' ? 'Overdue' : 'Unknown'}
                      </span>
                      {secret.autoRotate && <span className="chip">Auto</span>}
                    </div>
                    <p className="mt-1 text-xs text-muted-foreground">
                      Last rotated {secret.lastRotatedAt ? new Date(secret.lastRotatedAt).toLocaleDateString() : 'never'}
                      {secret.nextRotationAt ? ` · next ${new Date(secret.nextRotationAt).toLocaleDateString()}` : ''}
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center gap-2 self-end sm:self-center">
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => rotateMutation.mutate(secret)}
                      disabled={rotatingId === id}
                    >
                      <RefreshCw className={`h-3.5 w-3.5 ${rotatingId === id ? 'animate-spin' : ''}`} />
                      Rotate
                    </Button>
                    <Button
                      size="sm"
                      variant={secret.autoRotate ? 'secondary' : 'ghost'}
                      onClick={() => autoRotateMutation.mutate(secret)}
                      disabled={autoRotateMutation.isPending}
                    >
                      {secret.autoRotate ? 'Disable auto' : 'Enable auto'}
                    </Button>
                  </div>
                </li>
              )
            })}
          </ul>
        )}
      </section>
    </div>
  )
}
