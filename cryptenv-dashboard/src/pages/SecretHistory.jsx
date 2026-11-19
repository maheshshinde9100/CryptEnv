import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { secretVersionAPI } from '../lib/api'
import { toast } from 'sonner'
import { Button } from '../components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../components/ui/select'
import { ArrowLeft, GitCompareArrows, History, RotateCcw } from 'lucide-react'

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

  const ordered = useMemo(() => [...versions].sort((a, b) => b.versionNumber - a.versionNumber), [versions])
  const rollback = useMutation({
    mutationFn: (version) => secretVersionAPI.rollback(key, version),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['secret-versions', key] })
      queryClient.invalidateQueries({ queryKey: ['secrets'] })
      toast.success('Secret rolled back to the selected version')
    },
    onError: (error) => toast.error(error.response?.data?.message || 'Rollback failed'),
  })

  const compare = async () => {
    if (!environmentId || !fromVersion || !toVersion || fromVersion === toVersion) {
      toast.error('Choose two different versions and an environment to compare')
      return
    }
    try {
      setDiff((await secretVersionAPI.diff(environmentId, key, fromVersion, toVersion)).data)
    } catch (error) {
      toast.error(error.response?.data?.message || 'Could not compare versions')
    }
  }

  return <div className="space-y-6 max-w-4xl">
    <div className="flex items-start justify-between gap-4">
      <div>
        <Button variant="ghost" className="-ml-3 mb-2" onClick={() => navigate('/secrets')}><ArrowLeft className="h-4 w-4 mr-2" />Secrets</Button>
        <h1 className="text-3xl font-bold tracking-tight">Version history</h1>
        <p className="text-muted-foreground mt-1 font-mono text-sm">{key}</p>
      </div>
    </div>

    <Card>
      <CardHeader><CardTitle className="flex items-center gap-2"><GitCompareArrows className="h-5 w-5" />Metadata diff</CardTitle><CardDescription>Compare fingerprints and change metadata. Secret values are never rendered here.</CardDescription></CardHeader>
      <CardContent className="flex flex-col sm:flex-row gap-3">
        <Select value={fromVersion} onValueChange={setFromVersion}><SelectTrigger><SelectValue placeholder="From version" /></SelectTrigger><SelectContent>{ordered.map((item) => <SelectItem key={`from-${item.versionNumber}`} value={String(item.versionNumber)}>Version {item.versionNumber}</SelectItem>)}</SelectContent></Select>
        <Select value={toVersion} onValueChange={setToVersion}><SelectTrigger><SelectValue placeholder="To version" /></SelectTrigger><SelectContent>{ordered.map((item) => <SelectItem key={`to-${item.versionNumber}`} value={String(item.versionNumber)}>Version {item.versionNumber}</SelectItem>)}</SelectContent></Select>
        <Button onClick={compare}><GitCompareArrows className="h-4 w-4 mr-2" />Compare</Button>
      </CardContent>
      {diff && <CardContent className="pt-0"><pre className="rounded-lg bg-muted p-3 text-xs overflow-auto">{JSON.stringify(diff, null, 2)}</pre></CardContent>}
    </Card>

    <Card>
      <CardHeader><CardTitle className="flex items-center gap-2"><History className="h-5 w-5" />Saved versions</CardTitle><CardDescription>Rolling back changes the active version and is recorded in the audit ledger.</CardDescription></CardHeader>
      <CardContent className="space-y-2">
        {isLoading ? <p className="text-sm text-muted-foreground">Loading version history…</p> : ordered.length === 0 ? <p className="text-sm text-muted-foreground">No saved versions yet.</p> : ordered.map((item) => <div key={item.id || item.versionNumber} className="rounded-xl border p-4 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3"><div><p className="font-semibold">Version {item.versionNumber}{item.isActive ? ' · Active' : ''}</p><p className="text-xs text-muted-foreground">{item.rotationReason || 'Secret update'} · {item.createdAt ? new Date(item.createdAt).toLocaleString() : 'date unavailable'}</p><p className="text-xs text-muted-foreground font-mono">SHA-256: {item.contentFingerprint || 'not recorded'}</p></div><Button size="sm" variant="outline" disabled={item.isActive || rollback.isPending} onClick={() => { if (confirm(`Roll back ${key} to version ${item.versionNumber}?`)) rollback.mutate(item.versionNumber) }}><RotateCcw className="h-3.5 w-3.5 mr-2" />Rollback</Button></div>)}
      </CardContent>
    </Card>
  </div>
}
