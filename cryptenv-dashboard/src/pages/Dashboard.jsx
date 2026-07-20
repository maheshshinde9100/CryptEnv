import { useQuery } from '@tanstack/react-query'
import { secretsAPI, workspaceAPI } from '../lib/api'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Skeleton } from '../components/ui/skeleton'
import { Key, FolderOpen, Users, Activity } from 'lucide-react'

export function Dashboard() {
  const { data: secrets, isLoading: secretsLoading } = useQuery({
    queryKey: ['secrets'],
    queryFn: () => secretsAPI.list().then((res) => res.data),
  })

  const { data: workspaces, isLoading: workspacesLoading } = useQuery({
    queryKey: ['workspaces'],
    queryFn: () => workspaceAPI.list().then((res) => res.data),
  })

  const stats = [
    {
      title: 'Total Secrets',
      value: secrets?.length || 0,
      icon: Key,
      loading: secretsLoading,
    },
    {
      title: 'Workspaces',
      value: workspaces?.length || 0,
      icon: FolderOpen,
      loading: workspacesLoading,
    },
    {
      title: 'Team Members',
      value: '3',
      icon: Users,
      loading: false,
    },
    {
      title: 'Active Sessions',
      value: '5',
      icon: Activity,
      loading: false,
    },
  ]

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Dashboard</h1>
        <p className="text-muted-foreground">Overview of your secrets and workspaces</p>
      </div>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        {stats.map((stat) => {
          const Icon = stat.icon
          return (
            <Card key={stat.title}>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{stat.title}</CardTitle>
                <Icon className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                {stat.loading ? (
                  <Skeleton className="h-8 w-20" />
                ) : (
                  <div className="text-2xl font-bold">{stat.value}</div>
                )}
              </CardContent>
            </Card>
          )
        })}
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Recent Secrets</CardTitle>
            <CardDescription>Your recently added secrets</CardDescription>
          </CardHeader>
          <CardContent>
            {secretsLoading ? (
              <div className="space-y-2">
                {[1, 2, 3].map((i) => (
                  <Skeleton key={i} className="h-10 w-full" />
                ))}
              </div>
            ) : secrets && secrets.length > 0 ? (
              <div className="space-y-2">
                {secrets.slice(0, 5).map((secret) => (
                  <div
                    key={secret.key}
                    className="flex items-center justify-between rounded-lg border p-3"
                  >
                    <div className="flex items-center gap-3">
                      <Key className="h-4 w-4 text-muted-foreground" />
                      <span className="font-medium">{secret.key}</span>
                    </div>
                    <span className="text-sm text-muted-foreground">
                      {'•'.repeat(20)}
                    </span>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">No secrets yet</p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Workspaces</CardTitle>
            <CardDescription>Your active workspaces</CardDescription>
          </CardHeader>
          <CardContent>
            {workspacesLoading ? (
              <div className="space-y-2">
                {[1, 2, 3].map((i) => (
                  <Skeleton key={i} className="h-10 w-full" />
                ))}
              </div>
            ) : workspaces && workspaces.length > 0 ? (
              <div className="space-y-2">
                {workspaces.slice(0, 5).map((workspace) => (
                  <div
                    key={workspace.id}
                    className="flex items-center justify-between rounded-lg border p-3"
                  >
                    <div className="flex items-center gap-3">
                      <FolderOpen className="h-4 w-4 text-muted-foreground" />
                      <span className="font-medium">{workspace.name}</span>
                    </div>
                    <span className="text-sm text-muted-foreground">
                      {workspace.environments?.length || 0} envs
                    </span>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">No workspaces yet</p>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
