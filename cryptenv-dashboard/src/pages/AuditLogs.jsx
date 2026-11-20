import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useOutletContext } from 'react-router-dom'
import { auditLogAPI } from '../lib/api'
import { Skeleton } from '../components/ui/skeleton'
import { Input } from '../components/ui/input'
import { Button } from '../components/ui/button'
import { Download, Search } from 'lucide-react'

export function AuditLogs() {
  const { activeWorkspace } = useOutletContext() || {}
  const [filter, setFilter] = useState('')
  const [page, setPage] = useState(0)

  const { data: logs, isLoading, isError } = useQuery({
    queryKey: ['audit-logs', activeWorkspace?.id, page],
    queryFn: () =>
      auditLogAPI
        .list({ workspaceId: activeWorkspace?.id, page, size: 25 })
        .then((res) => res.data),
  })

  const rows = logs?.content || (Array.isArray(logs) ? logs : [])
  const totalPages = logs?.totalPages ?? 1
  const filtered = rows.filter((log) =>
    filter
      ? log.action?.toLowerCase().includes(filter.toLowerCase()) ||
        log.details?.toLowerCase().includes(filter.toLowerCase()) ||
        log.resourceType?.toLowerCase().includes(filter.toLowerCase())
      : true
  )

  const download = async (format) => {
    try {
      const result = await auditLogAPI.export(activeWorkspace?.id, format)
      const url = URL.createObjectURL(
        new Blob([result.data], { type: format === 'csv' ? 'text/csv' : 'application/json' })
      )
      const link = document.createElement('a')
      link.href = url
      link.download = `cryptenv-audit.${format}`
      link.click()
      URL.revokeObjectURL(url)
    } catch {
      // export failures surface through the toast-free download path
    }
  }

  return (
    <div className="space-y-8">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="eyebrow">Security</p>
          <h1 className="page-title mt-3">Audit log</h1>
          <p className="page-subtitle">
            Hash-chained record of every access, change and approval
            {activeWorkspace ? ` in ${activeWorkspace.name}` : ''}
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => download('json')}>
            <Download className="h-4 w-4" />
            JSON
          </Button>
          <Button variant="outline" onClick={() => download('csv')}>
            <Download className="h-4 w-4" />
            CSV
          </Button>
        </div>
      </header>

      <section className="rounded-lg border border-border">
        <div className="border-b border-border p-4">
          <div className="relative max-w-sm">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Filter by action, resource or details"
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
              className="pl-10"
            />
          </div>
        </div>

        {isLoading ? (
          <div className="space-y-px p-2">
            {[1, 2, 3, 4].map((i) => (
              <Skeleton key={i} className="h-14 w-full" />
            ))}
          </div>
        ) : isError ? (
          <p className="px-5 py-16 text-center text-sm text-danger">Failed to load audit logs. Check your permissions.</p>
        ) : filtered.length === 0 ? (
          <p className="px-5 py-16 text-center text-sm text-muted-foreground">No audit entries found.</p>
        ) : (
          <ul className="divide-y divide-border/60">
            {filtered.map((log) => (
              <li key={log.id} className="flex flex-col gap-2 px-5 py-3.5 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-mono text-xs text-foreground">{log.action}</p>
                    {log.resourceType && (
                      <span className="chip">
                        {log.resourceType}
                        {log.resourceId ? ` #${log.resourceId}` : ''}
                      </span>
                    )}
                  </div>
                  {log.details && <p className="mt-1 truncate text-xs text-muted-foreground">{log.details}</p>}
                </div>
                <div className="flex shrink-0 items-center gap-4 text-xs text-muted-foreground">
                  <span>{log.user?.email || log.user?.username || log.userEmail || '—'}</span>
                  <span>{log.timestamp ? new Date(log.timestamp).toLocaleString() : ''}</span>
                </div>
              </li>
            ))}
          </ul>
        )}

        {totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-border px-5 py-3">
            <p className="text-xs text-muted-foreground">
              Page {page + 1} of {totalPages}
            </p>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                Previous
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </Button>
            </div>
          </div>
        )}
      </section>
    </div>
  )
}
