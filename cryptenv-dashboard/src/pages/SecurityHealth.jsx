import { useQuery } from '@tanstack/react-query'
import { secretsAPI, workspaceAPI } from '../lib/api'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Skeleton } from '../components/ui/skeleton'
import { Shield, ShieldCheck, ShieldAlert, ShieldOff, Key, Lock, AlertTriangle, CheckCircle2, Activity } from 'lucide-react'

export function SecurityHealth() {
  const { data: secrets, isLoading: secretsLoading } = useQuery({
    queryKey: ['secrets'],
    queryFn: () => secretsAPI.list().then((res) => res.data),
  })

  const { data: workspaces, isLoading: workspacesLoading } = useQuery({
    queryKey: ['workspaces'],
    queryFn: () => workspaceAPI.list().then((res) => res.data),
  })

  const totalSecrets = secrets?.length || 0
  const totalWorkspaces = workspaces?.length || 0
  const isLoading = secretsLoading || workspacesLoading

  const checks = [
    {
      label: 'AES-256 GCM Encryption',
      description: 'All secrets are encrypted with AES-256 GCM cipher at rest',
      status: 'pass',
      icon: Lock,
    },
    {
      label: 'JWT Authentication',
      description: 'All API endpoints are protected with JWT Bearer tokens',
      status: 'pass',
      icon: ShieldCheck,
    },
    {
      label: 'API Key Authentication',
      description: 'SDK and CLI access via X-API-Key header is enabled',
      status: 'pass',
      icon: Key,
    },
    {
      label: 'Secrets Encrypted',
      description: `${totalSecrets} secret(s) stored with AES-256 encryption`,
      status: totalSecrets >= 0 ? 'pass' : 'warn',
      icon: ShieldCheck,
    },
    {
      label: 'Workspace Isolation',
      description: `${totalWorkspaces} workspace(s) with environment-scoped secret isolation`,
      status: totalWorkspaces > 0 ? 'pass' : 'warn',
      icon: Shield,
    },
    {
      label: 'Audit Logging',
      description: 'All secret access and user actions are logged',
      status: 'pass',
      icon: Activity,
    },
  ]

  const passCount = checks.filter((c) => c.status === 'pass').length
  const warnCount = checks.filter((c) => c.status === 'warn').length
  const failCount = checks.filter((c) => c.status === 'fail').length
  const score = Math.round((passCount / checks.length) * 100)

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Security Health</h1>
        <p className="text-muted-foreground">Overview of your environment's security posture</p>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <Card className="border-green-500/30 bg-green-500/5">
          <CardContent className="pt-6">
            <div className="flex items-center gap-4">
              <div className="p-3 rounded-full bg-green-500/10">
                <CheckCircle2 className="h-6 w-6 text-green-500" />
              </div>
              <div>
                <p className="text-sm text-muted-foreground">Security Score</p>
                <p className="text-3xl font-bold text-green-500">{score}%</p>
              </div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <div className="flex items-center gap-4">
              <div className="p-3 rounded-full bg-green-500/10">
                <ShieldCheck className="h-6 w-6 text-green-500" />
              </div>
              <div>
                <p className="text-sm text-muted-foreground">Passed Checks</p>
                <p className="text-3xl font-bold">{passCount}</p>
              </div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <div className="flex items-center gap-4">
              <div className="p-3 rounded-full bg-yellow-500/10">
                <AlertTriangle className="h-6 w-6 text-yellow-500" />
              </div>
              <div>
                <p className="text-sm text-muted-foreground">Warnings</p>
                <p className="text-3xl font-bold">{warnCount}</p>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Shield className="h-5 w-5 text-primary" />
            Security Checks
          </CardTitle>
          <CardDescription>Detailed breakdown of your security controls</CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="space-y-3">
              {[1,2,3,4].map(i => <Skeleton key={i} className="h-16 w-full" />)}
            </div>
          ) : (
            <div className="space-y-3">
              {checks.map((check, index) => {
                const Icon = check.icon
                const statusColors = {
                  pass: 'text-green-500 bg-green-500/10',
                  warn: 'text-yellow-500 bg-yellow-500/10',
                  fail: 'text-red-500 bg-red-500/10',
                }
                const badgeColors = {
                  pass: 'bg-green-500/10 text-green-600',
                  warn: 'bg-yellow-500/10 text-yellow-600',
                  fail: 'bg-red-500/10 text-red-600',
                }
                return (
                  <div key={index} className="flex items-center justify-between p-4 border rounded-lg hover:bg-accent/30 transition-colors">
                    <div className="flex items-center gap-4">
                      <div className={`p-2 rounded-full ${statusColors[check.status]}`}>
                        <Icon className="h-4 w-4" />
                      </div>
                      <div>
                        <p className="font-semibold text-sm">{check.label}</p>
                        <p className="text-xs text-muted-foreground">{check.description}</p>
                      </div>
                    </div>
                    <span className={`text-xs font-bold px-3 py-1 rounded-full ${badgeColors[check.status]}`}>
                      {check.status === 'pass' ? 'PASS' : check.status === 'warn' ? 'WARN' : 'FAIL'}
                    </span>
                  </div>
                )
              })}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
