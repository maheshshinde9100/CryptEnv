import { useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { driftAPI, environmentAPI } from '../lib/api'
import { toast } from 'sonner'
import { Button } from '../components/ui/button'
import { Label } from '../components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '../components/ui/dialog'
import { Skeleton } from '../components/ui/skeleton'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../components/ui/select'
import {
  ShieldCheck, Radar, RefreshCw, Download, RotateCcw, Loader2,
  CheckCircle2, AlertTriangle, Clock, XCircle, History,
} from 'lucide-react'

const classificationStyles = {
  IN_SYNC: 'bg-green-500/10 text-green-600 border-green-500/30',
  STALE: 'bg-yellow-500/10 text-yellow-600 border-yellow-500/30',
  MODIFIED: 'bg-orange-500/10 text-orange-600 border-orange-500/30',
  MISSING: 'bg-red-500/10 text-red-600 border-red-500/30',
}

const scanStatusStyles = {
  RUNNING: 'bg-blue-500/10 text-blue-600 border-blue-500/30',
  COMPLETED: 'bg-green-500/10 text-green-600 border-green-500/30',
  FAILED: 'bg-red-500/10 text-red-600 border-red-500/30',
}

const classificationMeta = [
  { key: 'inSync', label: 'In sync', icon: CheckCircle2, tone: 'text-green-600' },
  { key: 'stale', label: 'Stale', icon: Clock, tone: 'text-yellow-600' },
  { key: 'modified', label: 'Modified', icon: AlertTriangle, tone: 'text-orange-600' },
  { key: 'missing', label: 'Missing', icon: XCircle, tone: 'text-red-600' },
]

function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString()
}

function shortFingerprint(value) {
  if (!value) return '—'
  return `${value.slice(0, 10)}…`
}

export function Drift() {
  const { activeWorkspace } = useOutletContext()
  const workspaceId = activeWorkspace?.id
  const queryClient = useQueryClient()

  const [scanEnv, setScanEnv] = useState('')
  const [selectedScanId, setSelectedScanId] = useState(null)

  const { data: environments = [] } = useQuery({
    queryKey: ['environments', workspaceId],
    queryFn: () => environmentAPI.list(workspaceId).then((res) => res.data),
    enabled: !!workspaceId,
  })

  const { data: scans = [], isLoading: scansLoading } = useQuery({
    queryKey: ['drift-scans'],
    queryFn: () => driftAPI.scans().then((res) => res.data),
  })

  const { data: scanDetail, isLoading: detailLoading } = useQuery({
    queryKey: ['drift-scan', selectedScanId],
    queryFn: () => driftAPI.scanDetail(selectedScanId).then((res) => res.data),
    enabled: !!selectedScanId,
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['drift-scans'] })
    queryClient.invalidateQueries({ queryKey: ['drift-scan'] })
  }

  const scanMutation = useMutation({
    mutationFn: (environmentId) => driftAPI.scan(environmentId),
    onSuccess: (res) => {
      const scan = res.data
      toast.success('Drift scan complete', {
        description: `modified=${scan.modified} missing=${scan.missing} stale=${scan.stale} inSync=${scan.inSync}`,
      })
      invalidate()
      if (scan.id) setSelectedScanId(scan.id)
    },
    onError: (error) => toast.error(error.response?.data?.message || 'Drift scan failed'),
  })

  const remediateMutation = useMutation({
    mutationFn: (id) => driftAPI.remediate(id),
    onSuccess: (res) => {
      toast.success(`Queued ${res.data?.queued ?? 0} remediation syncs`)
      invalidate()
    },
    onError: (error) => toast.error(error.response?.data?.message || 'Remediation failed'),
  })

  const handleScan = () => {
    if (!scanEnv) {
      toast.error('Select an environment to scan')
      return
    }
    scanMutation.mutate(Number(scanEnv))
  }

  const handleExport = async (scanId, format) => {
    try {
      const res = await driftAPI.report(scanId, format)
      const text = typeof res.data === 'string' ? res.data : JSON.stringify(res.data, null, 2)
      const blob = new Blob([text], { type: format === 'csv' ? 'text/csv' : 'application/json' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `drift-report-${scanId}.${format}`
      link.click()
      URL.revokeObjectURL(url)
      toast.success('Report exported')
    } catch (error) {
      toast.error(error.response?.data?.message || 'Export failed')
    }
  }

  const latest = scans[0]
  const activeScan = scanDetail || latest
  const findings = activeScan?.findings || []
  const actionable = findings.filter((f) => f.classification !== 'IN_SYNC').length

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Drift Detection</h1>
          <p className="text-muted-foreground">
            Fingerprint-only comparison between CryptEnv and values pushed to providers
          </p>
        </div>
        <Button variant="outline" onClick={() => invalidate()} disabled={scansLoading}>
          <RefreshCw className={`mr-2 h-4 w-4 ${scansLoading ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </div>

      {/* Run scan */}
      <section className="glass-panel rounded-2xl p-5 sm:p-6 space-y-4">
        <div className="flex items-center gap-2">
          <Radar className="h-5 w-5 text-primary" />
          <div>
            <h2 className="font-semibold">Run a scan</h2>
            <p className="text-sm text-muted-foreground">
              Compares current secret fingerprints against the last pushed state. No plaintext leaves the server.
            </p>
          </div>
        </div>
        <div className="flex flex-col sm:flex-row gap-3 sm:items-end">
          <div className="flex-1">
            <Label className="mb-1.5 block">Environment</Label>
            <Select value={scanEnv} onValueChange={setScanEnv}>
              <SelectTrigger>
                <SelectValue placeholder="Select environment" />
              </SelectTrigger>
              <SelectContent>
                {environments.map((env) => (
                  <SelectItem key={env.id} value={String(env.id)}>{env.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <Button onClick={handleScan} disabled={!scanEnv || scanMutation.isPending}>
            {scanMutation.isPending ? (
              <Loader2 className="h-4 w-4 mr-1.5 animate-spin" />
            ) : (
              <Radar className="h-4 w-4 mr-1.5" />
            )}
            Run scan
          </Button>
        </div>
      </section>

      {/* Summary tiles */}
      {activeScan && (
        <section className="grid gap-3 grid-cols-2 lg:grid-cols-5">
          {classificationMeta.map(({ key, label, icon: Icon, tone }) => (
            <div key={key} className="glass-panel rounded-xl p-4">
              <div className="flex items-center justify-between">
                <p className="text-xs text-muted-foreground uppercase tracking-wider">{label}</p>
                <Icon className={`h-4 w-4 ${tone}`} />
              </div>
              <p className="text-2xl font-semibold mt-1">{activeScan[key] ?? 0}</p>
            </div>
          ))}
          <div className="glass-panel rounded-xl p-4">
            <div className="flex items-center justify-between">
              <p className="text-xs text-muted-foreground uppercase tracking-wider">Total</p>
              <ShieldCheck className="h-4 w-4 text-primary" />
            </div>
            <p className="text-2xl font-semibold mt-1">{activeScan.total ?? findings.length}</p>
          </div>
        </section>
      )}

      {/* Findings */}
      <section className="space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <AlertTriangle className="h-5 w-5 text-primary" />
            <div>
              <h2 className="font-semibold">
                Findings{activeScan?.id ? ` · scan #${activeScan.id}` : ''}
              </h2>
              <p className="text-sm text-muted-foreground">
                {actionable > 0 ? `${actionable} finding(s) need attention` : 'All secrets are in sync'}
              </p>
            </div>
          </div>
          {activeScan?.id && (
            <div className="flex flex-wrap gap-2">
              <Button
                size="sm"
                variant="outline"
                onClick={() => handleExport(activeScan.id, 'json')}
              >
                <Download className="h-3.5 w-3.5 mr-1.5" />
                JSON
              </Button>
              <Button
                size="sm"
                variant="outline"
                onClick={() => handleExport(activeScan.id, 'csv')}
              >
                <Download className="h-3.5 w-3.5 mr-1.5" />
                CSV
              </Button>
              <Button
                size="sm"
                className="brand-gradient text-white border-0"
                onClick={() => remediateMutation.mutate(activeScan.id)}
                disabled={remediateMutation.isPending || actionable === 0}
              >
                {remediateMutation.isPending ? (
                  <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />
                ) : (
                  <RotateCcw className="h-3.5 w-3.5 mr-1.5" />
                )}
                Remediate
              </Button>
            </div>
          )}
        </div>

        {detailLoading ? (
          <Skeleton className="h-40 w-full rounded-2xl" />
        ) : findings.length === 0 ? (
          <div className="glass-panel rounded-2xl p-8 text-center text-sm text-muted-foreground">
            No findings yet. Run a scan against an environment that has pushed secrets to a provider.
          </div>
        ) : (
          <div className="glass-panel rounded-2xl overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="border-b border-border/70 bg-muted/40">
                  <tr className="text-left text-xs uppercase tracking-wider text-muted-foreground">
                    <th className="px-4 py-3 font-medium">Secret key</th>
                    <th className="px-4 py-3 font-medium">Provider</th>
                    <th className="px-4 py-3 font-medium">Target</th>
                    <th className="px-4 py-3 font-medium">Classification</th>
                    <th className="px-4 py-3 font-medium">Expected</th>
                    <th className="px-4 py-3 font-medium">Actual</th>
                    <th className="px-4 py-3 font-medium">Detected</th>
                  </tr>
                </thead>
                <tbody>
                  {findings.map((f) => (
                    <tr key={f.id} className="border-b border-border/40 last:border-0">
                      <td className="px-4 py-3 font-mono text-xs font-medium">{f.secretKey}</td>
                      <td className="px-4 py-3">{f.provider || '—'}</td>
                      <td className="px-4 py-3 font-mono text-xs">{f.target || '—'}</td>
                      <td className="px-4 py-3">
                        <span className={`text-xs font-semibold px-2 py-0.5 rounded-full border ${classificationStyles[f.classification] || ''}`}>
                          {f.classification}
                        </span>
                        {f.detail && (
                          <p className="text-[11px] text-muted-foreground mt-1 line-clamp-1" title={f.detail}>
                            {f.detail}
                          </p>
                        )}
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-muted-foreground">{shortFingerprint(f.expectedFingerprint)}</td>
                      <td className="px-4 py-3 font-mono text-xs text-muted-foreground">{shortFingerprint(f.actualFingerprint)}</td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">{formatDate(f.detectedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </section>

      {/* Scan history */}
      <section className="space-y-4">
        <div className="flex items-center gap-2">
          <History className="h-5 w-5 text-primary" />
          <div>
            <h2 className="font-semibold">Scan history</h2>
            <p className="text-sm text-muted-foreground">Manual and scheduled scans, newest first</p>
          </div>
        </div>

        {scansLoading ? (
          <Skeleton className="h-40 w-full rounded-2xl" />
        ) : scans.length === 0 ? (
          <div className="glass-panel rounded-2xl p-8 text-center text-sm text-muted-foreground">
            No scans yet. Run your first scan above.
          </div>
        ) : (
          <div className="glass-panel rounded-2xl overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="border-b border-border/70 bg-muted/40">
                  <tr className="text-left text-xs uppercase tracking-wider text-muted-foreground">
                    <th className="px-4 py-3 font-medium">Scan</th>
                    <th className="px-4 py-3 font-medium">Env</th>
                    <th className="px-4 py-3 font-medium">Trigger</th>
                    <th className="px-4 py-3 font-medium">Status</th>
                    <th className="px-4 py-3 font-medium">In sync</th>
                    <th className="px-4 py-3 font-medium">Stale</th>
                    <th className="px-4 py-3 font-medium">Modified</th>
                    <th className="px-4 py-3 font-medium">Missing</th>
                    <th className="px-4 py-3 font-medium">Started</th>
                    <th className="px-4 py-3 font-medium text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {scans.map((scan) => (
                    <tr
                      key={scan.id}
                      className={`border-b border-border/40 last:border-0 cursor-pointer hover:bg-accent/40 ${
                        activeScan?.id === scan.id ? 'bg-accent/30' : ''
                      }`}
                      onClick={() => setSelectedScanId(scan.id)}
                    >
                      <td className="px-4 py-3 font-medium">#{scan.id}</td>
                      <td className="px-4 py-3 text-xs">{scan.environmentId}</td>
                      <td className="px-4 py-3 text-xs">{scan.trigger}</td>
                      <td className="px-4 py-3">
                        <span className={`text-xs font-semibold px-2 py-0.5 rounded-full border ${scanStatusStyles[scan.status] || ''}`}>
                          {scan.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-xs">{scan.inSync ?? 0}</td>
                      <td className="px-4 py-3 text-xs">{scan.stale ?? 0}</td>
                      <td className="px-4 py-3 text-xs">{scan.modified ?? 0}</td>
                      <td className="px-4 py-3 text-xs">{scan.missing ?? 0}</td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">{formatDate(scan.startedAt || scan.createdAt)}</td>
                      <td className="px-4 py-3 text-right">
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={(e) => {
                            e.stopPropagation()
                            setSelectedScanId(scan.id)
                          }}
                        >
                          View
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </section>

      {/* Scan detail dialog */}
      <Dialog open={!!selectedScanId && !!scanDetail} onOpenChange={(open) => !open && setSelectedScanId(null)}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>Scan #{scanDetail?.id}</DialogTitle>
            <DialogDescription>
              {scanDetail?.detail ||
                `Started ${formatDate(scanDetail?.startedAt)} · completed ${formatDate(scanDetail?.completedAt)}`}
            </DialogDescription>
          </DialogHeader>
          <div className="grid grid-cols-2 sm:grid-cols-5 gap-3">
            {classificationMeta.map(({ key, label }) => (
              <div key={key} className="rounded-xl border border-border/70 p-3 text-center">
                <p className="text-xs text-muted-foreground uppercase tracking-wider">{label}</p>
                <p className="text-xl font-semibold mt-1">{scanDetail?.[key] ?? 0}</p>
              </div>
            ))}
            <div className="rounded-xl border border-border/70 p-3 text-center">
              <p className="text-xs text-muted-foreground uppercase tracking-wider">Total</p>
              <p className="text-xl font-semibold mt-1">{scanDetail?.total ?? 0}</p>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2 pt-2">
            <Button size="sm" variant="outline" onClick={() => handleExport(scanDetail.id, 'json')}>
              <Download className="h-3.5 w-3.5 mr-1.5" />
              JSON report
            </Button>
            <Button size="sm" variant="outline" onClick={() => handleExport(scanDetail.id, 'csv')}>
              <Download className="h-3.5 w-3.5 mr-1.5" />
              CSV report
            </Button>
            <Button
              size="sm"
              className="brand-gradient text-white border-0"
              onClick={() => remediateMutation.mutate(scanDetail.id)}
              disabled={remediateMutation.isPending}
            >
              {remediateMutation.isPending ? (
                <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />
              ) : (
                <RotateCcw className="h-3.5 w-3.5 mr-1.5" />
              )}
              Remediate
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}
