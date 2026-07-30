import { useQuery } from '@tanstack/react-query'
import { secretsAPI, auditLogAPI } from '../lib/api'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Skeleton } from '../components/ui/skeleton'
import { BarChart3, Key, Activity, TrendingUp, Clock, Eye } from 'lucide-react'

export function UsageAnalytics() {
  const { data: secrets, isLoading: secretsLoading } = useQuery({
    queryKey: ['secrets'],
    queryFn: () => secretsAPI.list().then((res) => res.data),
  })

  const { data: logs, isLoading: logsLoading } = useQuery({
    queryKey: ['audit-logs'],
    queryFn: () => auditLogAPI.list().then((res) => res.data),
  })

  const isLoading = secretsLoading || logsLoading

  // Compute analytics from real data
  const totalSecrets = secrets?.length || 0
  const totalEvents = logs?.length || 0
  const loginEvents = logs?.filter(l => l.action === 'LOGIN').length || 0
  const secretAccessEvents = logs?.filter(l => l.action?.includes('SECRET')).length || 0
  const todayEvents = logs?.filter(l => {
    if (!l.timestamp) return false
    return new Date(l.timestamp).toDateString() === new Date().toDateString()
  }).length || 0

  // Action breakdown
  const actionCounts = {}
  logs?.forEach(l => {
    if (l.action) actionCounts[l.action] = (actionCounts[l.action] || 0) + 1
  })
  const topActions = Object.entries(actionCounts)
    .sort(([,a],[,b]) => b - a)
    .slice(0, 6)

  const stats = [
    { label: 'Total Secrets', value: totalSecrets, icon: Key, color: 'text-blue-500', bg: 'bg-blue-500/10' },
    { label: 'Total Events', value: totalEvents, icon: Activity, color: 'text-green-500', bg: 'bg-green-500/10' },
    { label: 'Today\'s Events', value: todayEvents, icon: Clock, color: 'text-orange-500', bg: 'bg-orange-500/10' },
    { label: 'Secret Access Events', value: secretAccessEvents, icon: Eye, color: 'text-purple-500', bg: 'bg-purple-500/10' },
  ]

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Usage Analytics</h1>
        <p className="text-muted-foreground">Insights into how your secrets and workspaces are being used</p>
      </div>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        {stats.map((stat) => {
          const Icon = stat.icon
          return (
            <Card key={stat.label}>
              <CardContent className="pt-6">
                <div className="flex items-center gap-4">
                  <div className={`p-3 rounded-full ${stat.bg}`}>
                    <Icon className={`h-5 w-5 ${stat.color}`} />
                  </div>
                  <div>
                    <p className="text-sm text-muted-foreground">{stat.label}</p>
                    {isLoading ? (
                      <Skeleton className="h-8 w-12 mt-1" />
                    ) : (
                      <p className="text-2xl font-bold">{stat.value}</p>
                    )}
                  </div>
                </div>
              </CardContent>
            </Card>
          )
        })}
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <BarChart3 className="h-5 w-5 text-primary" />
              Top Actions
            </CardTitle>
            <CardDescription>Most frequent audit log actions</CardDescription>
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <div className="space-y-3">
                {[1,2,3,4].map(i => <Skeleton key={i} className="h-10 w-full" />)}
              </div>
            ) : topActions.length > 0 ? (
              <div className="space-y-3">
                {topActions.map(([action, count]) => {
                  const pct = totalEvents > 0 ? Math.round((count / totalEvents) * 100) : 0
                  return (
                    <div key={action}>
                      <div className="flex justify-between mb-1">
                        <span className="text-sm font-medium">{action}</span>
                        <span className="text-sm text-muted-foreground">{count} ({pct}%)</span>
                      </div>
                      <div className="w-full bg-muted rounded-full h-2">
                        <div
                          className="bg-primary h-2 rounded-full transition-all"
                          style={{ width: `${pct}%` }}
                        />
                      </div>
                    </div>
                  )
                })}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground text-center py-8">No audit events recorded yet</p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <TrendingUp className="h-5 w-5 text-primary" />
              Recent Activity
            </CardTitle>
            <CardDescription>Last 5 audit log events</CardDescription>
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <div className="space-y-3">
                {[1,2,3,4,5].map(i => <Skeleton key={i} className="h-12 w-full" />)}
              </div>
            ) : logs && logs.length > 0 ? (
              <div className="space-y-3">
                {logs.slice(0, 5).map((log, i) => (
                  <div key={log.id || i} className="flex items-center justify-between p-3 border rounded-lg">
                    <div>
                      <p className="text-sm font-medium">{log.action}</p>
                      <p className="text-xs text-muted-foreground">
                        {log.resourceType && `${log.resourceType} `}
                        {log.timestamp && new Date(log.timestamp).toLocaleString()}
                      </p>
                    </div>
                    <span className={`text-xs px-2 py-1 rounded-full font-medium ${
                      log.success !== false
                        ? 'bg-green-500/10 text-green-600'
                        : 'bg-red-500/10 text-red-600'
                    }`}>
                      {log.success !== false ? 'Success' : 'Failed'}
                    </span>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground text-center py-8">No recent activity</p>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
