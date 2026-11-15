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
    <div className="relative min-h-screen flex items-center justify-center p-4 overflow-hidden">
      <div className="pointer-events-none absolute inset-0 brand-gradient opacity-[0.1]" />
      <div className="relative w-full max-w-md space-y-6">
        <div className="flex flex-col items-center text-center gap-3">
          <Link to="/"><BrandLogo size="lg" /></Link>
          <h1 className="text-3xl font-bold brand-text">Verify your email</h1>
          <p className="text-sm text-muted-foreground">Enter the six-digit code we sent to your inbox.</p>
        </div>
        <div className="glass-panel surface-glow rounded-2xl p-6 sm:p-8">
          <form className="space-y-4" onSubmit={(event) => { event.preventDefault(); verify.mutate() }}>
            <div className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input id="email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
            </div>
            <div className="space-y-2">
              <Label htmlFor="code">Verification code</Label>
              <Input id="code" inputMode="numeric" autoComplete="one-time-code" maxLength={6} pattern="[0-9]{6}" value={code} onChange={(event) => setCode(event.target.value.replace(/\D/g, ''))} required />
            </div>
            <Button className="w-full brand-gradient border-0 text-white" disabled={verify.isPending}>
              {verify.isPending ? 'Verifying…' : 'Verify email'}
            </Button>
          </form>
          <div className="mt-5 text-center text-sm text-muted-foreground">
            Didn’t receive it?{' '}
            <button type="button" className="text-primary font-semibold hover:underline" disabled={!email || resend.isPending} onClick={() => resend.mutate()}>
              {resend.isPending ? 'Sending…' : 'Resend code'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
