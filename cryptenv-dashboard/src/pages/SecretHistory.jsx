import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { secretVersionAPI } from '../lib/api'
import { toast } from 'sonner'
import { Button } from '../components/ui/button'
import { Skeleton } from '../components/ui/skeleton'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../components/ui/select'
import { ArrowLeft, GitCompareArrows, RotateCcw } from 'lucide-react'

export function SecretHistory() {
  const { key } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const environmentId = location.state?.environmentId || Number(new URLSearchParams(location.search).get('environmentId'))
  const [fromVersion, setFromVersion] = useState('')
  const [toVersion, setToVersion] = useState('')
  const [diff, setDiff] = useState(null)

  const { data: versions = [], isLoading } = useQuery({
    queryKey: ['secret-versions', key],
    queryFn: () => secretVersionAPI.list(key).then((response) => response.data),
    enabled: !!key,
  })

  const ordered = useMemo(
    () => [...versions].sort((a, b) => b.versionNumber - a.versionNumber),
    [versions]
  )

  const rollback = useMutation({
    mutationFn: (version) => secretVersionAPI.rollback(key, version),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['secret-versions', key] })
      queryClient.invalidateQueries({ queryKey: ['secrets'] })
      toast.success('Rolled back to the selected version')
    },
    onError: (error) => toast.error(error.response?.data?.message || 'Rollback failed'),
  })

  const compare = async () => {
    if (!environmentId || !fromVersion || !toVersion || fromVersion === toVersion) {
      toast.error('Choose two different versions and an environment')
      return
    }
    try {
      const res = await secretVersionAPI.diff(environmentId, key, fromVersion, toVersion)
      setDiff(res.data)
    } catch (error) {
      toast.error(error.response?.data?.message || 'Could not compare versions')
    }
  }

  return (
    <div className="max-w-4xl space-y-8">
      <header className="flex items-center gap-3">
        <Button variant="ghost" size="icon" onClick={() => navigate('/secrets')} aria-label="Back to secrets">
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div className="min-w-0">
          <p className="eyebrow">Version history</p>
          <h1 className="mt-2 truncate font-mono text-xl">{key}</h1>
        </div>
      </header>

      <section className="rounded-lg border border-border">
        <div className="flex items-center gap-2 border-b border-border px-5 py-4">
          <GitCompareArrows className="h-4 w-4 text-muted-foreground" />
          <div>
            <h2 className="text-base">Compare versions</h2>
            <p className="text-xs text-muted-foreground">
              Fingerprints and change metadata only — values are never rendered
            </p>
          </div>
        </div>
        <div className="flex flex-col gap-3 p-5 sm:flex-row">
          <Select value={fromVersion} onValueChange={setFromVersion}>
            <SelectTrigger>
              <SelectValue placeholder="From version" />
            </SelectTrigger>
            <SelectContent>
              {ordered.map((item) => (
                <SelectItem key={`from-${item.versionNumber}`} value={String(item.versionNumber)}>
                  Version {item.versionNumber}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select value={toVersion} onValueChange={setToVersion}>
            <SelectTrigger>
              <SelectValue placeholder="To version" />
            </SelectTrigger>
            <SelectContent>
              {ordered.map((item) => (
                <SelectItem key={`to-${item.versionNumber}`} value={String(item.versionNumber)}>
                  Version {item.versionNumber}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button onClick={compare}>
            <GitCompareArrows className="h-4 w-4" />
            Compare
          </Button>
        </div>
        {diff && (
          <div className="border-t border-border px-5 py-4">
            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              <div className="flex justify-between gap-4">
                <dt className="text-muted-foreground">Changed</dt>
                <dd>{diff.changed ? 'Yes' : 'No'}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-muted-foreground">Changed lines</dt>
                <dd>{diff.changedLines ?? 0}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-muted-foreground">v{diff.fromVersion} → v{diff.toVersion}</dt>
                <dd>
                  {diff.fromLength} → {diff.toLength} chars
                </dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-muted-foreground">Lines</dt>
                <dd>
                  {diff.fromLines} → {diff.toLines}
                </dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-muted-foreground">v{diff.fromVersion} fingerprint</dt>
                <dd className="truncate font-mono text-xs">{diff.fromFingerprint || '—'}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-muted-foreground">v{diff.toVersion} fingerprint</dt>
                <dd className="truncate font-mono text-xs">{diff.toFingerprint || '—'}</dd>
              </div>
            </dl>
          </div>
        )}
      </section>

      <section className="rounded-lg border border-border">
        <div className="border-b border-border px-5 py-4">
          <h2 className="text-base">Saved versions</h2>
          <p className="text-xs text-muted-foreground">Rollback switches the active version and is audit-logged</p>
        </div>
        {isLoading ? (
          <div className="space-y-px p-2">
            {[1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-16 w-full" />
            ))}
          </div>
        ) : ordered.length === 0 ? (
          <p className="px-5 py-16 text-center text-sm text-muted-foreground">No saved versions yet.</p>
        ) : (
          <ul className="divide-y divide-border/60">
            {ordered.map((item) => (
              <li
                key={item.id || item.versionNumber}
                className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center sm:justify-between"
              >
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="text-sm">Version {item.versionNumber}</p>
                    {item.isActive && <span className="chip">Active</span>}
                  </div>
                  <p className="mt-1 text-xs text-muted-foreground">
                    {item.rotationReason || 'Secret update'}
                    {item.rotatedBy ? ` · ${item.rotatedBy}` : ''}
                    {item.createdAt ? ` · ${new Date(item.createdAt).toLocaleString()}` : ''}
                  </p>
                  <p className="mt-0.5 truncate font-mono text-xs text-muted-foreground">
                    sha256:{item.contentFingerprint || 'not recorded'}
                  </p>
                </div>
                <Button
                  size="sm"
                  variant="outline"
                  disabled={item.isActive || rollback.isPending}
                  onClick={() => {
                    if (confirm(`Roll back ${key} to version ${item.versionNumber}?`)) {
                      rollback.mutate(item.versionNumber)
                    }
                  }}
                >
                  <RotateCcw className="h-3.5 w-3.5" />
                  Rollback
                </Button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
