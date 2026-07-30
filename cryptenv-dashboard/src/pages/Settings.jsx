import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { authAPI } from '../lib/api'
import { toast } from 'sonner'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Button } from '../components/ui/button'
import { Input } from '../components/ui/input'
import { Label } from '../components/ui/label'
import { User, Bell, Shield, Key, Copy, RefreshCw, Eye, EyeOff } from 'lucide-react'

export function Settings() {
  const queryClient = useQueryClient()
  const [showApiKey, setShowApiKey] = useState(false)

  const { data: currentUser } = useQuery({
    queryKey: ['currentUser'],
    queryFn: () => authAPI.getCurrentUser().then((res) => res.data),
  })

  const apiKeyMutation = useMutation({
    mutationFn: () => authAPI.regenerateApiKey(),
    onSuccess: (res) => {
      queryClient.setQueryData(['currentUser'], res.data)
      toast.success('New API Key generated successfully!')
    },
    onError: () => {
      toast.error('Failed to regenerate API Key')
    },
  })

  const copyToClipboard = (text) => {
    navigator.clipboard.writeText(text)
    toast.success('API Key copied to clipboard!')
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Settings & API Keys</h1>
        <p className="text-muted-foreground">Manage your account settings, security preferences, and API access credentials</p>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        {/* API Key Access Section */}
        <Card className="md:col-span-2 border-primary/20 bg-primary/5">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Key className="h-5 w-5 text-primary" />
              API Key & Access Credentials
            </CardTitle>
            <CardDescription>
              Use this Access Key to authenticate SDK, CLI, and custom backend applications (`X-API-Key` header) to fetch your secrets securely.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
              <div className="relative flex-1">
                <Input
                  type={showApiKey ? 'text' : 'password'}
                  readOnly
                  value={currentUser?.apiKey || 'ce_live_****************************'}
                  className="font-mono pr-20 bg-background"
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={() => setShowApiKey(!showApiKey)}
                  className="absolute right-1 top-1/2 -translate-y-1/2 h-7 px-2 text-xs"
                >
                  {showApiKey ? <EyeOff className="h-3.5 w-3.5 mr-1" /> : <Eye className="h-3.5 w-3.5 mr-1" />}
                  {showApiKey ? 'Hide' : 'Show'}
                </Button>
              </div>

              <Button
                variant="outline"
                onClick={() => copyToClipboard(currentUser?.apiKey)}
                disabled={!currentUser?.apiKey}
                className="flex items-center gap-1.5"
              >
                <Copy className="h-4 w-4" />
                Copy Key
              </Button>

              <Button
                onClick={() => apiKeyMutation.mutate()}
                disabled={apiKeyMutation.isPending}
                className="flex items-center gap-1.5"
              >
                <RefreshCw className={`h-4 w-4 ${apiKeyMutation.isPending ? 'animate-spin' : ''}`} />
                Regenerate Key
              </Button>
            </div>
            <p className="text-xs text-muted-foreground">
              Keep your API key safe! Do not share or commit this key to public code repositories.
            </p>
          </CardContent>
        </Card>

        {/* Profile Settings */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <User className="h-5 w-5" />
              Profile Details
            </CardTitle>
            <CardDescription>Your registered identity</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label>Username</Label>
              <Input value={currentUser?.username || ''} disabled readOnly className="bg-muted" />
            </div>
            <div className="space-y-2">
              <Label>Email Address</Label>
              <Input value={currentUser?.email || ''} disabled readOnly className="bg-muted" />
            </div>
            <div className="space-y-2">
              <Label>First Name</Label>
              <Input value={currentUser?.firstName || ''} disabled readOnly className="bg-muted" />
            </div>
            <div className="space-y-2">
              <Label>Last Name</Label>
              <Input value={currentUser?.lastName || ''} disabled readOnly className="bg-muted" />
            </div>
          </CardContent>
        </Card>

        {/* Security & Access */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Shield className="h-5 w-5" />
              Security Status
            </CardTitle>
            <CardDescription>Active security controls and authentication mode</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex items-center justify-between p-3 border rounded-lg">
              <div>
                <p className="font-semibold text-sm">JWT Session Token</p>
                <p className="text-xs text-muted-foreground">Short-lived Bearer authentication token</p>
              </div>
              <span className="text-xs bg-green-500/10 text-green-600 font-bold px-2.5 py-1 rounded">Active</span>
            </div>
            <div className="flex items-center justify-between p-3 border rounded-lg">
              <div>
                <p className="font-semibold text-sm">AES-256 GCM Secret Encryption</p>
                <p className="text-xs text-muted-foreground">Server-side master key encryption</p>
              </div>
              <span className="text-xs bg-green-500/10 text-green-600 font-bold px-2.5 py-1 rounded">Enabled</span>
            </div>
            <div className="flex items-center justify-between p-3 border rounded-lg">
              <div>
                <p className="font-semibold text-sm">API Key Authentication</p>
                <p className="text-xs text-muted-foreground">SDK & CLI zero-trust authentication header</p>
              </div>
              <span className="text-xs bg-green-500/10 text-green-600 font-bold px-2.5 py-1 rounded">Enabled</span>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
