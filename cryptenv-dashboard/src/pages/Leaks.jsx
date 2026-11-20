import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useOutletContext } from 'react-router-dom'
import { leaksAPI } from '../lib/api'
import { toast } from 'sonner'
import { Button } from '../components/ui/button'
import { Input, Textarea } from '../components/ui/input'
import { Label } from '../components/ui/label'
import { Skeleton } from '../components/ui/skeleton'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog'
import { ScanSearch, Plus } from 'lucide-react'

export function Leaks() {
  const queryClient = useQueryClient()
  const { activeWorkspace } = useOutletContext() || {}
  const [scanOpen, setScanOpen] = useState(false)
  const [source, setSource] = useState('')
  const [content, setContent] = useState('')

  const { data: findings = [], isLoading } = useQuery({
    queryKey: ['leaks'],
    queryFn: () => leaksAPI.list().then((res) => res.data),
  })

  const scanMutation = useMutation({
    mutationFn: () =>
      leaksAPI.scan({
        source: source || 'manual-paste',
        content,
        workspaceId: activeWorkspace?.id,
      }),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['leaks'] })
      const found = res.data?.length || 0
      toast.success(found ? `${found} finding(s) recorded` : 'No leaks detected in this content')
      setScanOpen(false)
      setContent('')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Scan failed'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, status }) => leaksAPI.update(id, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['leaks'] })
      toast.success('Finding updated')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Update failed'),
  })

  const open = findings.filter((f) => f.status === 'OPEN').length

  return (
    <div className="space-y-8">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="eyebrow">Security</p>
          <h1 className="page-title mt-3">Leak findings</h1>
          <p className="page-subtitle">
            Metadata-only findings — values are fingerprinted, never stored or displayed
          </p>
        </div>
        <Button onClick={() => setScanOpen(true)}>
          <ScanSearch className="h-4 w-4" />
          Scan content
        </Button>
      </header>

      <section className="grid divide-y divide-border rounded-lg border border-border sm:grid-cols-3 sm:divide-y-0 sm:divide-x">
        <div className="p-5">
          <p className="text-xs text-muted-foreground">Total findings</p>
          <p className="mt-3 text-2xl font-light tracking-[-0.02em]">{findings.length}</p>
        </div>
        <div className="p-5">
          <p className="text-xs text-muted-foreground">Open</p>
          <p className="mt-3 text-2xl font-light tracking-[-0.02em]">{open}</p>
        </div>
        <div className="p-5">
          <p className="text-xs text-muted-foreground">Rotation suggested</p>
          <p className="mt-3 text-2xl font-light tracking-[-0.02em]">
            {findings.filter((f) => f.status === 'ROTATION_SUGGESTED').length}
          </p>
        </div>
      </section>

      <section className="rounded-lg border border-border">
        <div className="border-b border-border px-5 py-4">
          <h2 className="text-base">Detected findings</h2>
          <p className="text-xs text-muted-foreground">
            Paste content above, or run <code className="font-mono">cryptenv scan &lt;path&gt;</code> from the CLI.
          </p>
        </div>
        {isLoading ? (
          <div className="space-y-px p-2">
            {[1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-16 w-full" />
            ))}
          </div>
        ) : findings.length === 0 ? (
          <p className="px-5 py-16 text-center text-sm text-muted-foreground">No leak findings yet.</p>
        ) : (
          <ul className="divide-y divide-border/60">
            {findings.map((item) => (
              <li key={item.id} className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="text-sm">{item.ruleName}</p>
                    <span className="chip">{item.status.replace('_', ' ')}</span>
                  </div>
                  <p className="mt-1 truncate font-mono text-xs text-muted-foreground">
                    {item.source}
                    {item.lineNumber ? `:${item.lineNumber}` : ''} · sha256:{item.fingerprint}
                  </p>
                </div>
                {item.status === 'OPEN' && (
                  <div className="flex shrink-0 gap-2 self-end sm:self-center">
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => updateMutation.mutate({ id: item.id, status: 'IGNORED' })}
                    >
                      Ignore
                    </Button>
                    <Button size="sm" onClick={() => updateMutation.mutate({ id: item.id, status: 'ROTATION_SUGGESTED' })}>
                      Suggest rotation
                    </Button>
                  </div>
                )}
                {item.status === 'ROTATION_SUGGESTED' && (
                  <Button
                    size="sm"
                    variant="outline"
                    className="self-end sm:self-center"
                    onClick={() => updateMutation.mutate({ id: item.id, status: 'RESOLVED' })}
                  >
                    Mark resolved
                  </Button>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      <Dialog open={scanOpen} onOpenChange={setScanOpen}>
        <DialogContent className="max-w-xl">
          <DialogHeader>
            <DialogTitle>Scan content for leaks</DialogTitle>
            <DialogDescription>
              Content is scanned in memory. Only rule names, source labels and short fingerprints are stored.
            </DialogDescription>
          </DialogHeader>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              scanMutation.mutate()
            }}
            className="space-y-4"
          >
            <div className="space-y-2">
              <Label htmlFor="scan-source">Source label</Label>
              <Input
                id="scan-source"
                value={source}
                onChange={(e) => setSource(e.target.value)}
                placeholder="github.com/acme/api/.env"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="scan-content">Content</Label>
              <Textarea
                id="scan-content"
                value={content}
                onChange={(e) => setContent(e.target.value)}
                rows={8}
                required
                className="font-mono"
                placeholder="Paste file content to scan"
              />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setScanOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={scanMutation.isPending || !content.trim()}>
                <Plus className="h-4 w-4" />
                {scanMutation.isPending ? 'Scanning…' : 'Scan'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
