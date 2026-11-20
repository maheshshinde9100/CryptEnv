import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { authAPI } from '../lib/api'
import { toast } from 'sonner'
import { Button } from '../components/ui/button'
import { Input } from '../components/ui/input'
import { Label } from '../components/ui/label'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog'
import { Copy, Eye, EyeOff, RefreshCw, ShieldCheck, ShieldOff, KeyRound, LogOut, Smartphone } from 'lucide-react'

export function Settings() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [showApiKey, setShowApiKey] = useState(false)
  const [mfaSetup, setMfaSetup] = useState(null)
  const [mfaCode, setMfaCode] = useState('')
  const [recoveryCodes, setRecoveryCodes] = useState(null)
  const [disableOpen, setDisableOpen] = useState(false)
  const [password, setPassword] = useState('')
  const [disableCode, setDisableCode] = useState('')

  const { data: user } = useQuery({
    queryKey: ['currentUser'],
    queryFn: () => authAPI.getCurrentUser().then((res) => res.data),
  })

  const regenerateMutation = useMutation({
    mutationFn: () => authAPI.regenerateApiKey(),
    onSuccess: (res) => {
      queryClient.setQueryData(['currentUser'], res.data)
      toast.success('New API key generated')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Failed to regenerate API key'),
  })

  const setupMutation = useMutation({
    mutationFn: () => authAPI.mfaSetup(),
    onSuccess: (res) => {
      setMfaSetup(res.data)
      setMfaCode('')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Could not start MFA setup'),
  })

  const enableMutation = useMutation({
    mutationFn: () => authAPI.mfaEnable(mfaCode.trim()),
    onSuccess: (res) => {
      setRecoveryCodes(res.data.recoveryCodes || [])
      setMfaSetup(null)
      setMfaCode('')
      queryClient.invalidateQueries({ queryKey: ['currentUser'] })
      toast.success('Two-factor authentication enabled')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Invalid code'),
  })

  const disableMutation = useMutation({
    mutationFn: () => authAPI.mfaDisable({ password, code: disableCode.trim() }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['currentUser'] })
      setDisableOpen(false)
      setPassword('')
      setDisableCode('')
      toast.success('Two-factor authentication disabled')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Could not disable MFA'),
  })

  const signOutEverywhere = async () => {
    try {
      await authAPI.logout(true)
    } catch {
      // token may already be revoked; still clear the local session
    }
    localStorage.clear()
    queryClient.clear()
    navigate('/login')
    toast.success('Signed out on all devices')
  }

  const copy = async (value, label) => {
    await navigator.clipboard.writeText(value || '')
    toast.success(`${label} copied`)
  }

  return (
    <div className="space-y-8">
      <header>
        <p className="eyebrow">Account</p>
        <h1 className="page-title mt-3">Settings</h1>
        <p className="page-subtitle">Identity, API access and account security</p>
      </header>

      <section className="rounded-lg border border-border">
        <div className="border-b border-border px-5 py-4">
          <h2 className="text-base">Profile</h2>
          <p className="text-xs text-muted-foreground">Details tied to your CryptEnv identity</p>
        </div>
        <dl className="divide-y divide-border/60">
          {[
            ['Username', user?.username],
            ['Email', user?.email],
            ['Name', `${user?.firstName || ''} ${user?.lastName || ''}`.trim() || '—'],
            ['Global role', user?.role ? user.role.charAt(0) + user.role.slice(1).toLowerCase() : '—'],
            ['Email verified', user?.emailVerified ? 'Verified' : 'Pending verification'],
          ].map(([label, value]) => (
            <div key={label} className="flex items-center justify-between gap-4 px-5 py-3.5 text-sm">
              <dt className="text-muted-foreground">{label}</dt>
              <dd className="truncate text-right">{value || '—'}</dd>
            </div>
          ))}
        </dl>
      </section>

      <section className="rounded-lg border border-border">
        <div className="flex items-center justify-between border-b border-border px-5 py-4">
          <div>
            <h2 className="flex items-center gap-2 text-base">
              <KeyRound className="h-4 w-4 text-muted-foreground" />
              API key
            </h2>
            <p className="text-xs text-muted-foreground">Used by the CLI, SDK and custom backends via X-API-Key</p>
          </div>
          <Button variant="outline" size="sm" onClick={() => regenerateMutation.mutate()} disabled={regenerateMutation.isPending}>
            <RefreshCw className={`h-4 w-4 ${regenerateMutation.isPending ? 'animate-spin' : ''}`} />
            Regenerate
          </Button>
        </div>
        <div className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center">
          <div className="relative flex-1">
            <Input
              type={showApiKey ? 'text' : 'password'}
              readOnly
              value={user?.apiKey || 'ce_live_****************************'}
              className="pr-10 font-mono text-xs"
            />
            <Button
              type="button"
              variant="ghost"
              size="icon"
              className="absolute right-1 top-1/2 h-8 w-8 -translate-y-1/2"
              onClick={() => setShowApiKey((v) => !v)}
              aria-label={showApiKey ? 'Hide API key' : 'Show API key'}
            >
              {showApiKey ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </Button>
          </div>
          <Button variant="outline" onClick={() => copy(user?.apiKey, 'API key')} disabled={!user?.apiKey}>
            <Copy className="h-4 w-4" />
            Copy
          </Button>
        </div>
        <p className="px-5 pb-4 text-xs text-muted-foreground">
          Regenerating immediately invalidates the previous key.
        </p>
      </section>

      <section className="rounded-lg border border-border">
        <div className="flex items-center justify-between border-b border-border px-5 py-4">
          <div>
            <h2 className="flex items-center gap-2 text-base">
              <Smartphone className="h-4 w-4 text-muted-foreground" />
              Two-factor authentication
            </h2>
            <p className="text-xs text-muted-foreground">
              TOTP via an authenticator app · also required for secret reveals when enabled
            </p>
          </div>
          <span className="chip">{user?.mfaEnabled ? 'Enabled' : 'Off'}</span>
        </div>
        <div className="flex flex-col gap-4 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-start gap-3">
            {user?.mfaEnabled ? (
              <ShieldCheck className="mt-0.5 h-5 w-5 text-tertiary" />
            ) : (
              <ShieldOff className="mt-0.5 h-5 w-5 text-muted-foreground" />
            )}
            <div>
              <p className="text-sm">{user?.mfaEnabled ? 'Your account is protected with TOTP.' : 'Add a second factor to your account.'}</p>
              <p className="mt-1 text-xs text-muted-foreground">
                {user?.mfaEnabled
                  ? 'Reveals will ask for a 6-digit code. Recovery codes were shown when you enabled MFA.'
                  : 'Set up an authenticator app such as 1Password, Authy or Google Authenticator.'}
              </p>
            </div>
          </div>
          {user?.mfaEnabled ? (
            <Button variant="outline" onClick={() => setDisableOpen(true)}>
              Disable MFA
            </Button>
          ) : (
            <Button onClick={() => setupMutation.mutate()} disabled={setupMutation.isPending}>
              {setupMutation.isPending ? 'Starting…' : 'Enable MFA'}
            </Button>
          )}
        </div>
      </section>

      <section className="rounded-lg border border-border">
        <div className="border-b border-border px-5 py-4">
          <h2 className="text-base">Sessions</h2>
          <p className="text-xs text-muted-foreground">Revoke refresh tokens on every device</p>
        </div>
        <div className="flex flex-col gap-4 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm text-muted-foreground">Signing out everywhere invalidates all refresh tokens for your account.</p>
          <Button variant="outline" onClick={signOutEverywhere}>
            <LogOut className="h-4 w-4" />
            Sign out everywhere
          </Button>
        </div>
      </section>

      <Dialog open={!!mfaSetup} onOpenChange={(open) => !open && setMfaSetup(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Set up authenticator</DialogTitle>
            <DialogDescription>
              Add this account to your authenticator app, then enter the generated 6-digit code.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label>Secret</Label>
              <div className="flex gap-2">
                <Input readOnly value={mfaSetup?.secret || ''} className="font-mono text-xs" />
                <Button variant="outline" size="icon" onClick={() => copy(mfaSetup?.secret, 'Secret')} aria-label="Copy secret">
                  <Copy className="h-4 w-4" />
                </Button>
              </div>
            </div>
            <div className="space-y-2">
              <Label>otpauth URI</Label>
              <Input readOnly value={mfaSetup?.otpAuthUri || ''} className="font-mono text-[11px]" />
            </div>
            <form
              onSubmit={(e) => {
                e.preventDefault()
                enableMutation.mutate()
              }}
              className="space-y-4"
            >
              <div className="space-y-2">
                <Label htmlFor="mfa-code">6-digit code</Label>
                <Input
                  id="mfa-code"
                  value={mfaCode}
                  onChange={(e) => setMfaCode(e.target.value)}
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  placeholder="123456"
                  className="font-mono tracking-[0.4em]"
                  required
                />
              </div>
              <DialogFooter>
                <Button type="button" variant="outline" onClick={() => setMfaSetup(null)}>
                  Cancel
                </Button>
                <Button type="submit" disabled={enableMutation.isPending || mfaCode.trim().length < 6}>
                  {enableMutation.isPending ? 'Verifying…' : 'Enable MFA'}
                </Button>
              </DialogFooter>
            </form>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={!!recoveryCodes} onOpenChange={(open) => !open && setRecoveryCodes(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Save your recovery codes</DialogTitle>
            <DialogDescription>Each code works once. Store them somewhere safe — they are shown only now.</DialogDescription>
          </DialogHeader>
          <div className="grid grid-cols-2 gap-2">
            {(recoveryCodes || []).map((code) => (
              <code key={code} className="rounded-sm border border-border bg-surface px-2 py-1.5 font-mono text-xs">
                {code}
              </code>
            ))}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => copy((recoveryCodes || []).join('\n'), 'Recovery codes')}>
              <Copy className="h-4 w-4" />
              Copy all
            </Button>
            <Button onClick={() => setRecoveryCodes(null)}>Done</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={disableOpen} onOpenChange={setDisableOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Disable two-factor authentication</DialogTitle>
            <DialogDescription>Confirm with your password and a current code or recovery code.</DialogDescription>
          </DialogHeader>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              disableMutation.mutate()
            }}
            className="space-y-4"
          >
            <div className="space-y-2">
              <Label htmlFor="disable-password">Password</Label>
              <Input
                id="disable-password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="disable-code">TOTP or recovery code</Label>
              <Input
                id="disable-code"
                value={disableCode}
                onChange={(e) => setDisableCode(e.target.value)}
                className="font-mono"
                required
              />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setDisableOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={disableMutation.isPending}>
                {disableMutation.isPending ? 'Disabling…' : 'Disable MFA'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
