import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { authAPI } from '../lib/api'
import { toast } from 'sonner'
import { Input } from '../components/ui/input'
import { Label } from '../components/ui/label'
import { Button } from '../components/ui/button'
import { BrandLogo } from '../components/BrandLogo'

export function ForgotPassword() {
  const navigate = useNavigate()
  const [step, setStep] = useState('email')
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [resetToken, setResetToken] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')

  const { data: mailStatus } = useQuery({
    queryKey: ['email-verification-status'],
    queryFn: () => authAPI.verificationStatus().then((res) => res.data),
    staleTime: 5 * 60 * 1000,
    retry: false,
  })
  const mailConfigured = mailStatus?.mailConfigured !== false

  const requestCode = useMutation({
    mutationFn: async () => {
      const checked = await authAPI.checkEmail(email)
      if (!checked.data.valid) {
        throw new Error(checked.data.reason || 'Enter a valid email address')
      }
      return authAPI.forgotPassword(checked.data.normalized || email)
    },
    onSuccess: () => {
      toast.success('If that account exists, a reset code is on its way')
      setStep('code')
    },
    onError: (error) => toast.error(error.message || error.response?.data?.message || 'Could not send the code'),
  })

  const verifyCode = useMutation({
    mutationFn: () => authAPI.verifyResetCode(email, code),
    onSuccess: (response) => {
      setResetToken(response.data.resetToken)
      setStep('password')
    },
    onError: (error) => toast.error(error.response?.data?.message || 'Invalid or expired code'),
  })

  const resetPassword = useMutation({
    mutationFn: () => authAPI.resetPassword(resetToken, password),
    onSuccess: () => {
      toast.success('Password updated — sign in with your new password')
      navigate('/login?reset=1')
    },
    onError: (error) => toast.error(error.response?.data?.message || 'Could not reset the password'),
  })

  const submitPassword = (event) => {
    event.preventDefault()
    if (password !== confirm) {
      toast.error('Passwords do not match')
      return
    }
    if (password.length < 8) {
      toast.error('Password must be at least 8 characters')
      return
    }
    resetPassword.mutate()
  }

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background px-4 py-16">
      <div className="w-full max-w-sm">
        <div className="flex flex-col items-center gap-3 text-center">
          <Link to="/" className="inline-flex items-center gap-2.5">
            <BrandLogo size="md" />
            <span className="text-lg font-normal tracking-[-0.02em]">CryptEnv</span>
          </Link>
          <p className="text-sm text-muted-foreground">Recover access to your vault.</p>
        </div>

        <div className="mt-10 rounded-lg border border-border p-6 sm:p-7">
          <div className="mb-6">
            <h1 className="text-2xl font-light tracking-[-0.02em]">
              {step === 'email' && 'Reset your password'}
              {step === 'code' && 'Enter the code'}
              {step === 'password' && 'Choose a new password'}
            </h1>
            <p className="mt-1.5 text-sm text-muted-foreground">
              {step === 'email' && 'We will email a six-digit code if the account exists.'}
              {step === 'code' && `Sent to ${email}. Codes expire after 10 minutes.`}
              {step === 'password' && 'Use at least 8 characters. All other sessions are signed out.'}
            </p>
          </div>

          {!mailConfigured && (
            <div className="mb-5 rounded-md border border-border bg-surface p-3 text-xs leading-relaxed text-muted-foreground">
              <span className="text-foreground">This server cannot send email yet.</span>{' '}
              Configure <code className="font-mono text-[11px]">SPRING_MAIL_*</code> or{' '}
              <code className="font-mono text-[11px]">BREVO_API_KEY</code> on the API, then restart it.
            </div>
          )}

          {step === 'email' && (
            <form
              className="space-y-4"
              onSubmit={(event) => {
                event.preventDefault()
                requestCode.mutate()
              }}
            >
              <div className="space-y-2">
                <Label htmlFor="email">Email</Label>
                <Input
                  id="email"
                  type="email"
                  autoComplete="email"
                  placeholder="you@company.com"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  required
                />
              </div>
              <Button type="submit" className="h-[53px] w-full" disabled={requestCode.isPending}>
                {requestCode.isPending ? 'Sending…' : 'Send reset code'}
              </Button>
            </form>
          )}

          {step === 'code' && (
            <form
              className="space-y-4"
              onSubmit={(event) => {
                event.preventDefault()
                verifyCode.mutate()
              }}
            >
              <div className="space-y-2">
                <Label htmlFor="code">Six-digit code</Label>
                <Input
                  id="code"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  placeholder="123456"
                  maxLength={6}
                  value={code}
                  onChange={(event) => setCode(event.target.value.replace(/\D/g, '').slice(0, 6))}
                  required
                />
              </div>
              <Button type="submit" className="h-[53px] w-full" disabled={verifyCode.isPending || code.length !== 6}>
                {verifyCode.isPending ? 'Verifying…' : 'Verify code'}
              </Button>
              <div className="flex items-center justify-between text-xs text-muted-foreground">
                <button type="button" className="hover:text-foreground" onClick={() => setStep('email')}>
                  Change email
                </button>
                <button
                  type="button"
                  className="hover:text-foreground"
                  onClick={() => requestCode.mutate()}
                  disabled={requestCode.isPending}
                >
                  Resend code
                </button>
              </div>
            </form>
          )}

          {step === 'password' && (
            <form className="space-y-4" onSubmit={submitPassword}>
              <div className="space-y-2">
                <Label htmlFor="new-password">New password</Label>
                <Input
                  id="new-password"
                  type="password"
                  autoComplete="new-password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="confirm-password">Confirm password</Label>
                <Input
                  id="confirm-password"
                  type="password"
                  autoComplete="new-password"
                  value={confirm}
                  onChange={(event) => setConfirm(event.target.value)}
                  required
                />
              </div>
              <Button type="submit" className="h-[53px] w-full" disabled={resetPassword.isPending}>
                {resetPassword.isPending ? 'Updating…' : 'Update password'}
              </Button>
            </form>
          )}

          <p className="mt-5 text-center text-sm text-muted-foreground">
            <Link to="/login" className="text-foreground hover:underline">
              Back to sign in
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}