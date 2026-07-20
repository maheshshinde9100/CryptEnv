import { useState } from 'react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Button } from '../components/ui/button'
import { Input } from '../components/ui/input'
import { Label } from '../components/ui/label'
import { User, Bell, Shield, Key } from 'lucide-react'

export function Settings() {
  const [user, setUser] = useState({
    name: 'John Doe',
    email: 'john@example.com',
  })

  const [notifications, setNotifications] = useState({
    email: true,
    push: false,
  })

  const handleSaveProfile = (e) => {
    e.preventDefault()
    // Save profile logic here
    alert('Profile saved successfully')
  }

  const handleSaveNotifications = () => {
    // Save notifications logic here
    alert('Notification preferences saved')
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Settings</h1>
        <p className="text-muted-foreground">Manage your account settings and preferences</p>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <User className="h-5 w-5" />
              Profile Settings
            </CardTitle>
            <CardDescription>Update your personal information</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSaveProfile} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="name">Full Name</Label>
                <Input
                  id="name"
                  value={user.name}
                  onChange={(e) => setUser({ ...user, name: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="email">Email</Label>
                <Input
                  id="email"
                  type="email"
                  value={user.email}
                  onChange={(e) => setUser({ ...user, email: e.target.value })}
                />
              </div>
              <Button type="submit">Save Profile</Button>
            </form>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Bell className="h-5 w-5" />
              Notifications
            </CardTitle>
            <CardDescription>Manage your notification preferences</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="font-medium">Email Notifications</p>
                <p className="text-sm text-muted-foreground">Receive email updates</p>
              </div>
              <Button
                variant={notifications.email ? 'default' : 'outline'}
                onClick={() => setNotifications({ ...notifications, email: !notifications.email })}
              >
                {notifications.email ? 'Enabled' : 'Disabled'}
              </Button>
            </div>
            <div className="flex items-center justify-between">
              <div>
                <p className="font-medium">Push Notifications</p>
                <p className="text-sm text-muted-foreground">Receive push notifications</p>
              </div>
              <Button
                variant={notifications.push ? 'default' : 'outline'}
                onClick={() => setNotifications({ ...notifications, push: !notifications.push })}
              >
                {notifications.push ? 'Enabled' : 'Disabled'}
              </Button>
            </div>
            <Button onClick={handleSaveNotifications} className="w-full">
              Save Preferences
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Shield className="h-5 w-5" />
              Security
            </CardTitle>
            <CardDescription>Manage your security settings</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="current-password">Current Password</Label>
              <Input id="current-password" type="password" />
            </div>
            <div className="space-y-2">
              <Label htmlFor="new-password">New Password</Label>
              <Input id="new-password" type="password" />
            </div>
            <div className="space-y-2">
              <Label htmlFor="confirm-password">Confirm Password</Label>
              <Input id="confirm-password" type="password" />
            </div>
            <Button>Update Password</Button>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Key className="h-5 w-5" />
              API Keys
            </CardTitle>
            <CardDescription>Manage your API access keys</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="rounded-lg border p-4">
              <div className="flex items-center justify-between mb-2">
                <p className="font-medium">Production Key</p>
                <Button variant="outline" size="sm">Regenerate</Button>
              </div>
              <code className="text-sm bg-muted px-2 py-1 rounded">
                sk_live_••••••••••••••••
              </code>
            </div>
            <div className="rounded-lg border p-4">
              <div className="flex items-center justify-between mb-2">
                <p className="font-medium">Development Key</p>
                <Button variant="outline" size="sm">Regenerate</Button>
              </div>
              <code className="text-sm bg-muted px-2 py-1 rounded">
                sk_dev_••••••••••••••••
              </code>
            </div>
            <Button variant="outline" className="w-full">
              Generate New API Key
            </Button>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
