import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { notificationsAPI } from '../lib/api'
import { toast } from 'sonner'
import { Button } from '../components/ui/button'
import { Skeleton } from '../components/ui/skeleton'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../components/ui/select'
import { Bell, CheckCheck } from 'lucide-react'

const EVENT_GROUPS = [
  {
    title: 'Secrets',
    items: [
      ['SECRET_ACCESS', 'Secret access', 'A secret is read by the CLI or SDK'],
      ['SECRET_REVEAL', 'Secret reveal', 'A value is revealed in the dashboard'],
      ['SECRET_DELETE', 'Secret deletion', 'A secret is removed'],
    ],
  },
  {
    title: 'Account',
    items: [
      ['FAILED_LOGIN', 'Failed logins', 'Repeated failed authentication attempts'],
      ['MFA_ENABLED', 'MFA changes', 'Two-factor authentication is enabled or disabled'],
      ['WORKSPACE_INVITE', 'Workspace invitations', 'You are invited to a workspace'],
    ],
  },
  {
    title: 'Approvals and access',
    items: [
      ['APPROVAL_REQUEST', 'Approval requests', 'A four-eyes approval needs a decision'],
      ['JIT_REQUEST', 'JIT requests', 'Someone requests temporary access'],
      ['ACCESS_REVIEW', 'Access reviews', 'Reviews are created or completed'],
    ],
  },
]

const SEVERITIES = ['INFO', 'WARNING', 'CRITICAL']

function Toggle({ checked, onChange, label }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className={`relative inline-flex h-5 w-9 shrink-0 items-center rounded-full border transition-colors ${
        checked ? 'border-tertiary/60 bg-tertiary/30' : 'border-border bg-surface'
      }`}
    >
      <span
        className={`inline-block h-3.5 w-3.5 transform rounded-full bg-foreground transition-transform ${
          checked ? 'translate-x-[18px]' : 'translate-x-[3px]'
        }`}
      />
    </button>
  )
}

export function Notifications() {
  const queryClient = useQueryClient()
  const [inApp, setInApp] = useState(true)
  const [email, setEmail] = useState(true)
  const [minSeverity, setMinSeverity] = useState('INFO')
  const [types, setTypes] = useState([])

  const { data: preferences } = useQuery({
    queryKey: ['notification-preferences'],
    queryFn: () => notificationsAPI.preferences().then((res) => res.data),
  })

  const { data: feed, isLoading } = useQuery({
    queryKey: ['notifications'],
    queryFn: () => notificationsAPI.list({ size: 10 }).then((res) => res.data),
  })

  useEffect(() => {
    if (!preferences) return
    setInApp(preferences.inAppEnabled !== false)
    setEmail(Boolean(preferences.emailEnabled))
    setMinSeverity(preferences.minSeverity || 'INFO')
    const subscribed = preferences.subscribedTypes
    if (!subscribed || subscribed === '*' || subscribed.length === 0) {
      setTypes(EVENT_GROUPS.flatMap((group) => group.items.map(([type]) => type)))
    } else if (Array.isArray(subscribed)) {
      setTypes(subscribed)
    } else {
      setTypes(String(subscribed).split(',').map((t) => t.trim()).filter(Boolean))
    }
  }, [preferences])

  const saveMutation = useMutation({
    mutationFn: () =>
      notificationsAPI.updatePreferences({
        inAppEnabled: inApp,
        emailEnabled: email,
        minSeverity,
        subscribedTypes: types,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notification-preferences'] })
      toast.success('Notification preferences saved')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Unable to save preferences'),
  })

  const readMutation = useMutation({
    mutationFn: (id) => notificationsAPI.markRead(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  })

  const readAllMutation = useMutation({
    mutationFn: () => notificationsAPI.markAllRead(),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  })

  const toggleType = (type) =>
    setTypes((prev) => (prev.includes(type) ? prev.filter((t) => t !== type) : [...prev, type]))

  const items = useMemo(() => feed?.content || [], [feed])
  const unread = useMemo(() => items.filter((n) => !n.read).length, [items])

  return (
    <div className="space-y-8">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="eyebrow">Account</p>
          <h1 className="page-title mt-3">Notifications</h1>
          <p className="page-subtitle">Choose which security events reach you and how they are delivered</p>
        </div>
        <Button onClick={() => saveMutation.mutate()} disabled={saveMutation.isPending}>
          {saveMutation.isPending ? 'Saving…' : 'Save preferences'}
        </Button>
      </header>

      <section className="rounded-lg border border-border">
        <div className="border-b border-border px-5 py-4">
          <h2 className="text-base">Delivery</h2>
          <p className="text-xs text-muted-foreground">In-app notifications are always available in the feed below</p>
        </div>
        <ul className="divide-y divide-border/60">
          <li className="flex items-center justify-between gap-4 px-5 py-4">
            <div>
              <p className="text-sm">In-app notifications</p>
              <p className="text-xs text-muted-foreground">Show events in the notification feed</p>
            </div>
            <Toggle checked={inApp} onChange={setInApp} label="Toggle in-app notifications" />
          </li>
          <li className="flex items-center justify-between gap-4 px-5 py-4">
            <div>
              <p className="text-sm">Email notifications</p>
              <p className="text-xs text-muted-foreground">
                {preferences?.emailConfigured === false
                  ? 'SMTP is not configured on this deployment'
                  : 'Send alerts to your registered email address'}
              </p>
            </div>
            <Toggle checked={email} onChange={setEmail} label="Toggle email notifications" />
          </li>
          <li className="flex items-center justify-between gap-4 px-5 py-4">
            <div>
              <p className="text-sm">Minimum severity</p>
              <p className="text-xs text-muted-foreground">Only notify for events at or above this level</p>
            </div>
            <Select value={minSeverity} onValueChange={setMinSeverity}>
              <SelectTrigger className="w-40">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {SEVERITIES.map((severity) => (
                  <SelectItem key={severity} value={severity}>
                    {severity.charAt(0) + severity.slice(1).toLowerCase()}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </li>
        </ul>
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        <section className="rounded-lg border border-border">
          <div className="border-b border-border px-5 py-4">
            <h2 className="text-base">Events</h2>
            <p className="text-xs text-muted-foreground">Subscribe to the events that matter to you</p>
          </div>
          <div className="divide-y divide-border/60">
            {EVENT_GROUPS.map((group) => (
              <div key={group.title} className="px-5 py-4">
                <p className="mb-3 text-xs text-muted-foreground">{group.title}</p>
                <div className="space-y-3">
                  {group.items.map(([type, label, description]) => (
                    <div key={type} className="flex items-center justify-between gap-4">
                      <div>
                        <p className="text-sm">{label}</p>
                        <p className="text-xs text-muted-foreground">{description}</p>
                      </div>
                      <Toggle
                        checked={types.includes(type)}
                        onChange={() => toggleType(type)}
                        label={`Toggle ${label}`}
                      />
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </section>

        <section className="rounded-lg border border-border">
          <div className="flex items-center justify-between border-b border-border px-5 py-4">
            <div>
              <h2 className="flex items-center gap-2 text-base">
                <Bell className="h-4 w-4 text-muted-foreground" />
                Recent activity
              </h2>
              <p className="text-xs text-muted-foreground">{unread} unread</p>
            </div>
            <Button variant="ghost" size="sm" onClick={() => readAllMutation.mutate()} disabled={unread === 0}>
              <CheckCheck className="h-4 w-4" />
              Mark all read
            </Button>
          </div>
          {isLoading ? (
            <div className="space-y-px p-2">
              <Skeleton className="h-14 w-full" />
            </div>
          ) : items.length === 0 ? (
            <p className="px-5 py-12 text-center text-sm text-muted-foreground">No notifications yet.</p>
          ) : (
            <ul className="divide-y divide-border/60">
              {items.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    onClick={() => !item.read && readMutation.mutate(item.id)}
                    className={`w-full px-5 py-3.5 text-left transition-colors hover:bg-white/[0.02] ${
                      item.read ? 'opacity-60' : ''
                    }`}
                  >
                    <div className="flex items-center justify-between gap-3">
                      <p className="truncate text-sm">{item.title}</p>
                      <span className="text-[11px] text-muted-foreground">{item.severity}</span>
                    </div>
                    <p className="mt-1 truncate text-xs text-muted-foreground">
                      {item.body || item.type}
                      {item.createdAt ? ` · ${new Date(item.createdAt).toLocaleString()}` : ''}
                    </p>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  )
}
