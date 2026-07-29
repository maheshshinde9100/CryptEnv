import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { auditLogAPI } from '../lib/api'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Skeleton } from '../components/ui/skeleton'
import { Input } from '../components/ui/input'
import { Activity, ShieldAlert, User, Clock, FileText } from 'lucide-react'

export function AuditLogs() {
  const [filterAction, setFilterAction] = useState('')

  const { data: logs, isLoading, isError } = useQuery({
    queryKey: ['audit-logs'],
    queryFn: () => auditLogAPI.list().then((res) => res.data),
  })

  const filteredLogs = logs?.filter((log) =>
    filterAction ? log.action?.toLowerCase().includes(filterAction.toLowerCase()) || log.details?.toLowerCase().includes(filterAction.toLowerCase()) : true
  )

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Audit Logs</h1>
          <p className="text-muted-foreground">Track security events, access logs, and system modifications</p>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Activity className="h-5 w-5 text-primary" />
            System Audit Trail
          </CardTitle>
          <CardDescription>
            Filter and inspect security activities performed across your CryptEnv organization.
          </CardDescription>
          <div className="pt-2">
            <Input
              placeholder="Filter by action or details..."
              value={filterAction}
              onChange={(e) => setFilterAction(e.target.value)}
              className="max-w-sm"
            />
          </div>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="space-y-3">
              {[1, 2, 3, 4, 5].map((i) => (
                <Skeleton key={i} className="h-14 w-full" />
              ))}
            </div>
          ) : isError ? (
            <div className="flex items-center gap-2 text-destructive p-4 border rounded-lg bg-destructive/10">
              <ShieldAlert className="h-5 w-5" />
              <span>Failed to load audit logs. Please try again or check permissions.</span>
            </div>
          ) : filteredLogs && filteredLogs.length > 0 ? (
            <div className="space-y-3">
              {filteredLogs.map((log) => (
                <div
                  key={log.id}
                  className="flex flex-col md:flex-row md:items-center justify-between p-4 border rounded-lg hover:bg-accent/50 transition-colors gap-3"
                >
                  <div className="flex items-start md:items-center gap-3">
                    <div className="p-2 rounded-full bg-primary/10 text-primary">
                      <FileText className="h-4 w-4" />
                    </div>
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-semibold text-foreground">{log.action}</span>
                        {log.resourceType && (
                          <span className="text-xs px-2 py-0.5 rounded bg-muted font-mono">
                            {log.resourceType} #{log.resourceId}
                          </span>
                        )}
                      </div>
                      {log.details && (
                        <p className="text-sm text-muted-foreground mt-0.5">{log.details}</p>
                      )}
                    </div>
                  </div>

                  <div className="flex items-center gap-4 text-xs text-muted-foreground self-end md:self-center">
                    {log.user && (
                      <span className="flex items-center gap-1">
                        <User className="h-3 w-3" />
                        {log.user.email || log.user.username}
                      </span>
                    )}
                    {log.timestamp && (
                      <span className="flex items-center gap-1">
                        <Clock className="h-3 w-3" />
                        {new Date(log.timestamp).toLocaleString()}
                      </span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="text-center py-8 text-muted-foreground">
              No audit logs found.
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
