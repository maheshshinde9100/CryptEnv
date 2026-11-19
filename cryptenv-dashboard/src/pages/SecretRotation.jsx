import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { secretLifecycleAPI, secretsAPI } from '../lib/api'
import { toast } from 'sonner'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Button } from '../components/ui/button'
import { Skeleton } from '../components/ui/skeleton'
import { RefreshCw, Key, Clock, ShieldCheck, AlertTriangle } from 'lucide-react'

export function SecretRotation() {
  const queryClient = useQueryClient()
  const [rotatingKey, setRotatingKey] = useState(null)

  const { data: secrets, isLoading } = useQuery({
    queryKey: ['secrets'],
    queryFn: () => secretsAPI.list().then((res) => res.data),
  })

  const rotateMutation = useMutation({
    mutationFn: async (secret) => {
      setRotatingKey(`${secret.environmentId}:${secret.key}`)
      return secretsAPI.rotate(secret.environmentId, secret.key)
    },
    onSuccess: (_, secret) => {
      queryClient.invalidateQueries({ queryKey: ['secrets'] })
      toast.success(`Secret "${secret.key}" rotated with a newly generated value`)
      setRotatingKey(null)
    },
    onError: (_, secret) => {
      toast.error(`Failed to rotate secret "${secret.key}"`)
      setRotatingKey(null)
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
      toast.success('Automatic rotation policy updated')
    },
    onError: (error) => toast.error(error.response?.data?.message || 'Could not update rotation policy'),
  })

  const getAge = (createdAt) => {
    if (!createdAt) return 'Unknown'
    const diff = Date.now() - new Date(createdAt).getTime()
    const days = Math.floor(diff / (1000 * 60 * 60 * 24))
    if (days === 0) return 'Today'
    if (days === 1) return '1 day ago'
    return `${days} days ago`
  }

  const getAgeStatus = (secret) => {
    const reference = secret.nextRotationAt || secret.lastRotatedAt || secret.createdAt
    if (!reference) return 'unknown'
    if (secret.nextRotationAt && new Date(secret.nextRotationAt).getTime() <= Date.now()) return 'overdue'
    const interval = secret.rotationIntervalDays || 30
    const days = Math.floor((Date.now() - new Date(reference).getTime()) / (1000 * 60 * 60 * 24))
    if (!secret.nextRotationAt && days > interval * 3) return 'overdue'
    if (!secret.nextRotationAt && days > interval) return 'due'
    return 'ok'
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Secret Rotation</h1>
        <p className="text-muted-foreground">Manage and rotate your secrets to maintain security hygiene</p>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <Card className="border-green-500/30">
          <CardContent className="pt-6 flex items-center gap-4">
            <div className="p-3 rounded-full bg-green-500/10">
              <ShieldCheck className="h-5 w-5 text-green-500" />
            </div>
            <div>
              <p className="text-sm text-muted-foreground">Up-to-date</p>
              <p className="text-2xl font-bold">{secrets?.filter(s => getAgeStatus(s) === 'ok').length || 0}</p>
            </div>
          </CardContent>
        </Card>
        <Card className="border-yellow-500/30">
          <CardContent className="pt-6 flex items-center gap-4">
            <div className="p-3 rounded-full bg-yellow-500/10">
              <Clock className="h-5 w-5 text-yellow-500" />
            </div>
            <div>
              <p className="text-sm text-muted-foreground">Rotation Due</p>
              <p className="text-2xl font-bold">{secrets?.filter(s => getAgeStatus(s) === 'due').length || 0}</p>
            </div>
          </CardContent>
        </Card>
        <Card className="border-red-500/30">
          <CardContent className="pt-6 flex items-center gap-4">
            <div className="p-3 rounded-full bg-red-500/10">
              <AlertTriangle className="h-5 w-5 text-red-500" />
            </div>
            <div>
              <p className="text-sm text-muted-foreground">Overdue</p>
              <p className="text-2xl font-bold">{secrets?.filter(s => getAgeStatus(s) === 'overdue').length || 0}</p>
            </div>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <RefreshCw className="h-5 w-5 text-primary" />
            Secrets Rotation Status
          </CardTitle>
          <CardDescription>
            Manual rotation generates a new type-aware value and re-encrypts it. Automatic rotation uses each secret’s configured policy.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="space-y-3">
              {[1,2,3,4].map(i => <Skeleton key={i} className="h-14 w-full" />)}
            </div>
          ) : secrets && secrets.length > 0 ? (
            <div className="space-y-3">
              {secrets.map((secret) => {
                const status = getAgeStatus(secret)
                const rotationId = `${secret.environmentId}:${secret.key}`
                const statusColors = {
                  ok: 'bg-green-500/10 text-green-600',
                  due: 'bg-yellow-500/10 text-yellow-600',
                  overdue: 'bg-red-500/10 text-red-600',
                  unknown: 'bg-gray-500/10 text-gray-600',
                }
                return (
                  <div key={secret.key} className="flex items-center justify-between p-4 border rounded-lg hover:bg-accent/30 transition-colors">
                    <div className="flex items-center gap-4">
                      <div className="p-2 rounded-full bg-primary/10">
                        <Key className="h-4 w-4 text-primary" />
                      </div>
                      <div>
                        <p className="font-medium text-sm">{secret.key}</p>
                        <p className="text-xs text-muted-foreground">Last rotated: {getAge(secret.lastRotatedAt || secret.createdAt)}{secret.nextRotationAt ? ` · Next: ${new Date(secret.nextRotationAt).toLocaleDateString()}` : ''}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-3">
                      <span className={`text-xs font-bold px-2.5 py-1 rounded-full ${statusColors[status]}`}>
                        {status === 'ok' ? 'Current' : status === 'due' ? 'Due' : status === 'overdue' ? 'Overdue' : 'Unknown'}
                      </span>
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => rotateMutation.mutate(secret)}
                        disabled={rotatingKey === rotationId}
                        className="flex items-center gap-1.5"
                      >
                        <RefreshCw className={`h-3.5 w-3.5 ${rotatingKey === rotationId ? 'animate-spin' : ''}`} />
                        Rotate
                      </Button>
                      <Button
                        size="sm"
                        variant={secret.autoRotate ? 'secondary' : 'outline'}
                        onClick={() => autoRotateMutation.mutate(secret)}
                        disabled={autoRotateMutation.isPending}
                      >
                        {secret.autoRotate ? 'Auto on' : 'Enable auto'}
                      </Button>
                    </div>
                  </div>
                )
              })}
            </div>
          ) : (
            <p className="text-center py-8 text-sm text-muted-foreground">No secrets yet. Add secrets to manage their rotation.</p>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
