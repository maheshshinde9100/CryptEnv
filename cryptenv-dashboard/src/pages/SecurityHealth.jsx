import { useQuery } from '@tanstack/react-query'
import { useOutletContext } from 'react-router-dom'
import { secretsAPI, postureAPI } from '../lib/api'
import { Skeleton } from '../components/ui/skeleton'
import { ShieldCheck } from 'lucide-react'

function Metric({ label, value, hint }) {
  return (
    <div className="p-5">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-3 text-2xl font-light tracking-[-0.02em]">{value}</p>
      {hint && <p className="mt-1 text-xs text-muted-foreground">{hint}</p>}
    </div>
  )
}

export function SecurityHealth() {
  const { activeWorkspace } = useOutletContext() || {}

  const { data: secrets = [], isLoading: secretsLoading } = useQuery({
    queryKey: ['secrets'],
    queryFn: () => secretsAPI.list().then((res) => res.data),
  })

  const { data: posture, isLoading: postureLoading } = useQuery({
    queryKey: ['security-posture'],
    queryFn: () => postureAPI.current().then((res) => res.data),
  })

  const isLoading = secretsLoading || postureLoading
  const metrics = posture?.metrics || {}
  const score = posture?.score

  const checks = [
    ['AES-256-GCM at rest', 'Every secret is encrypted before it is stored', true],
    ['JWT-authenticated API', 'All endpoints require a bearer or API-key session', true],
    ['Workspace isolation', activeWorkspace?.hasEncryptionKey ? 'Encryption key configured' : 'No encryption key set', !!activeWorkspace?.hasEncryptionKey],
    ['MFA on the account', metrics.mfaEnabled ? 'TOTP is active' : 'TOTP is not enabled', !!metrics.mfaEnabled],
    ['Expired secrets', `${metrics.expiredSecrets ?? 0} expired`, !(metrics.expiredSecrets > 0)],
    ['Rotation coverage', `${metrics.withoutRotationPolicy ?? 0} without policy`, !(metrics.withoutRotationPolicy > 0)],
  ]

  return (
    <div className="space-y-8">
      <header>
        <p className="eyebrow">Security</p>
        <h1 className="page-title mt-3">Security health</h1>
        <p className="page-subtitle">Live posture computed from your secret metadata, rotation policy and account MFA</p>
      </header>

      {isLoading ? (
        <Skeleton className="h-40 w-full" />
      ) : (
        <>
          <section className="grid divide-y divide-border rounded-lg border border-border sm:grid-cols-4 sm:divide-y-0 sm:divide-x">
            <Metric label="Posture score" value={score != null ? `${score}` : '—'} hint={posture?.grade ? `Grade ${posture.grade}` : undefined} />
            <Metric label="Secrets" value={secrets.length} hint={`${metrics.totalSecrets ?? 0} tracked`} />
            <Metric label="Expired" value={metrics.expiredSecrets ?? 0} hint="Should be zero" />
            <Metric label="Overdue rotation" value={metrics.overdueRotation ?? 0} hint="Past rotation date" />
          </section>

          <section className="rounded-lg border border-border">
            <div className="flex items-center gap-2 border-b border-border px-5 py-4">
              <ShieldCheck className="h-4 w-4 text-muted-foreground" />
              <div>
                <h2 className="text-base">Controls</h2>
                <p className="text-xs text-muted-foreground">Signals that drive the posture score</p>
              </div>
            </div>
            <ul className="divide-y divide-border/60">
              {checks.map(([label, description, pass]) => (
                <li key={label} className="flex items-center justify-between gap-4 px-5 py-3.5">
                  <div>
                    <p className="text-sm">{label}</p>
                    <p className="mt-0.5 text-xs text-muted-foreground">{description}</p>
                  </div>
                  <span className={pass ? 'chip' : 'chip text-danger'}>{pass ? 'Pass' : 'Attention'}</span>
                </li>
              ))}
            </ul>
          </section>

          <section className="rounded-lg border border-border">
            <div className="border-b border-border px-5 py-4">
              <h2 className="text-base">Prioritized remediation</h2>
              <p className="text-xs text-muted-foreground">Actions calculated from the current posture</p>
            </div>
            {posture?.remediation?.length ? (
              <ul className="divide-y divide-border/60">
                {posture.remediation.map((item) => (
                  <li key={item.title} className="flex items-start justify-between gap-4 px-5 py-4">
                    <div>
                      <p className="text-sm">{item.title}</p>
                      <p className="mt-1 text-xs text-muted-foreground">{item.description}</p>
                    </div>
                    <span className="chip">{item.priority}</span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="px-5 py-10 text-center text-sm text-muted-foreground">Nothing to remediate.</p>
            )}
          </section>
        </>
      )}
    </div>
  )
}
