import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { authAPI } from '../lib/api'
import { toast } from 'sonner'
import { BrandLogo } from '../components/BrandLogo'
import { Button } from '../components/ui/button'
import { Input } from '../components/ui/input'
import { Label } from '../components/ui/label'

export function VerifyEmail() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [email, setEmail] = useState(params.get('email') || '')
  const [code, setCode] = useState('')

  const verify = useMutation({
    mutationFn: () => authAPI.verifyEmail(email.trim(), code.trim()),
    onSuccess: () => {
      toast.success('Email verified — you can now sign in')
      navigate('/login')
    },
    onError: (error) => toast.error(error.response?.data?.message || 'Verification failed'),
  })
  const resend = useMutation({
    mutationFn: () => authAPI.resendVerification(email.trim()),
    onSuccess: () => toast.success('If verification is needed, a new code has been sent'),
    onError: () => toast.error('Could not resend the verification code'),
  })

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background px-4 py-16">
      <div className="w-full max-w-sm">
        <div className="flex flex-col items-center gap-3 text-center">
          <Link to="/" className="inline-flex items-center gap-2.5">
            <BrandLogo size="md" />
            <span className="text-lg font-normal tracking-[-0.02em]">CryptEnv</span>
          </Link>
          <p className="text-sm text-muted-foreground">Enter the six-digit code we sent to your inbox.</p>
        </div>
        <div className="mt-10 rounded-lg border border-border p-6 sm:p-7">
          <div className="mb-6">
            <h1 className="text-2xl font-light tracking-[-0.02em]">Verify your email</h1>
            <p className="mt-1.5 text-sm text-muted-foreground">Codes expire shortly after they are sent.</p>
          </div>
          <form className="space-y-4" onSubmit={(event) => { event.preventDefault(); verify.mutate() }}>
            <div className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input id="email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
            </div>
            <div className="space-y-2">
              <Label htmlFor="code">Verification code</Label>
              <Input id="code" className="font-mono tracking-[0.3em]" inputMode="numeric" autoComplete="one-time-code" maxLength={6} pattern="[0-9]{6}" value={code} onChange={(event) => setCode(event.target.value.replace(/\D/g, ''))} required />
            </div>
            <Button className="h-[53px] w-full" disabled={verify.isPending}>
              {verify.isPending ? 'Verifying…' : 'Verify email'}
            </Button>
          </form>
          <div className="mt-5 text-center text-sm text-muted-foreground">
            Didn’t receive it?{' '}
            <button type="button" className="text-foreground hover:underline" disabled={!email || resend.isPending} onClick={() => resend.mutate()}>
              {resend.isPending ? 'Sending…' : 'Resend code'}
            </button>
          </div>
        </div>
        <p className="mt-6 text-center text-xs text-muted-foreground">
          <Link to="/login" className="hover:text-foreground">
            Back to sign in
          </Link>
        </p>
      </div>
    </div>
  )
}
