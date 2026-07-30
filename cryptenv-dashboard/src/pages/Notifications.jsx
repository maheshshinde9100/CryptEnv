import { useState } from 'react'
import { toast } from 'sonner'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Button } from '../components/ui/button'
import { Label } from '../components/ui/label'
import { Bell, Mail, Shield, Activity, Key, AlertTriangle, CheckCircle2 } from 'lucide-react'

const notificationGroups = [
  {
    title: 'Security Alerts',
    icon: Shield,
    items: [
      { id: 'secret_access', label: 'Secret Access', description: 'Notify when a secret is accessed via CLI or SDK', default: true },
      { id: 'api_key_used', label: 'API Key Used', description: 'Alert when your API key is used for authentication', default: true },
      { id: 'failed_login', label: 'Failed Login Attempts', description: 'Alert on repeated failed login attempts', default: true },
      { id: 'secret_delete', label: 'Secret Deletion', description: 'Notify when a secret is deleted', default: true },
    ],
  },
  {
    title: 'Rotation & Expiry',
    icon: AlertTriangle,
    items: [
      { id: 'rotation_due', label: 'Rotation Due', description: 'Remind when secrets are due for rotation (30+ days)', default: true },
      { id: 'rotation_overdue', label: 'Rotation Overdue', description: 'Alert when secrets are overdue for rotation (90+ days)', default: true },
    ],
  },
  {
    title: 'Account & Activity',
    icon: Activity,
    items: [
      { id: 'workspace_invite', label: 'Workspace Invitations', description: 'When you are invited to a workspace', default: true },
      { id: 'member_added', label: 'Member Added', description: 'When a new member joins your workspace', default: false },
      { id: 'system_updates', label: 'System Updates', description: 'CryptEnv platform update notifications', default: false },
    ],
  },
]

export function Notifications() {
  const [settings, setSettings] = useState(() => {
    const s = {}
    notificationGroups.forEach(g => {
      g.items.forEach(item => {
        s[item.id] = item.default
      })
    })
    return s
  })
  const [email, setEmail] = useState(true)

  const toggle = (id) => {
    setSettings(prev => ({ ...prev, [id]: !prev[id] }))
  }

  const handleSave = () => {
    toast.success('Notification preferences saved!')
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Notifications</h1>
        <p className="text-muted-foreground">Configure how and when you receive security and activity alerts</p>
      </div>

      {/* Delivery channel */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Mail className="h-5 w-5 text-primary" />
            Delivery Channel
          </CardTitle>
          <CardDescription>Choose how you receive notifications</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-between p-4 border rounded-lg">
            <div className="flex items-center gap-3">
              <Mail className="h-5 w-5 text-muted-foreground" />
              <div>
                <p className="font-medium text-sm">Email Notifications</p>
                <p className="text-xs text-muted-foreground">Alerts sent to your registered email address</p>
              </div>
            </div>
            <button
              role="switch"
              aria-checked={email}
              onClick={() => setEmail(!email)}
              className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${email ? 'bg-primary' : 'bg-muted'}`}
            >
              <span className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${email ? 'translate-x-6' : 'translate-x-1'}`} />
            </button>
          </div>
        </CardContent>
      </Card>

      {/* Notification groups */}
      {notificationGroups.map((group) => {
        const Icon = group.icon
        return (
          <Card key={group.title}>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <Icon className="h-5 w-5 text-primary" />
                {group.title}
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {group.items.map((item) => (
                <div key={item.id} className="flex items-center justify-between p-3 border rounded-lg hover:bg-accent/30 transition-colors">
                  <div>
                    <p className="font-medium text-sm">{item.label}</p>
                    <p className="text-xs text-muted-foreground">{item.description}</p>
                  </div>
                  <button
                    role="switch"
                    aria-checked={settings[item.id]}
                    onClick={() => toggle(item.id)}
                    className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors flex-shrink-0 ml-4 ${settings[item.id] ? 'bg-primary' : 'bg-muted'}`}
                  >
                    <span className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${settings[item.id] ? 'translate-x-6' : 'translate-x-1'}`} />
                  </button>
                </div>
              ))}
            </CardContent>
          </Card>
        )
      })}

      <div className="flex justify-end">
        <Button onClick={handleSave} className="flex items-center gap-2">
          <CheckCircle2 className="h-4 w-4" />
          Save Preferences
        </Button>
      </div>
    </div>
  )
}
