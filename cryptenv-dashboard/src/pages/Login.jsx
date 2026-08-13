import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { authAPI } from '../lib/api'
import { toast } from 'sonner'
import { Input } from '../components/ui/input'
import { Label } from '../components/ui/label'
import { Button } from '../components/ui/button'
import { Eye, EyeOff } from 'lucide-react'
import { BrandLogo } from '../components/BrandLogo'

export function Login() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPw, setShowPw] = useState(false)

  const loginMutation = useMutation({
    mutationFn: () => authAPI.login(email, password),
    onSuccess: (response) => {
      const data = response.data
      localStorage.setItem('token', data.token)
      if (data.user || data.email) {
        localStorage.setItem(
          'user',
          JSON.stringify(data.user || { email: data.email, username: data.username, id: data.userId })
        )
      }
      toast.success('Welcome back')
      navigate('/overview')
    },
    onError: (error) => {
      toast.error(error.response?.data?.message || 'Invalid email or password')
    },
  })

  return (
    <div className="relative min-h-screen flex items-center justify-center p-4 overflow-hidden">
      <div className="pointer-events-none absolute inset-0 brand-gradient opacity-[0.12]" />
      <div className="pointer-events-none absolute -top-24 -left-24 h-72 w-72 rounded-full bg-cyan-400/20 blur-3xl" />
      <div className="pointer-events-none absolute -bottom-24 -right-24 h-80 w-80 rounded-full bg-violet-500/20 blur-3xl" />

      <div className="relative w-full max-w-md space-y-8">
        <div className="flex flex-col items-center text-center gap-3">
          <Link to="/">
            <BrandLogo size="xl" />
          </Link>
          <h1 className="text-4xl font-bold tracking-tight brand-text">CryptEnv</h1>
          <p className="text-sm text-muted-foreground max-w-sm">
            Encrypted environment secrets for modern teams — vaulted, isolated, and ready for runtime.
          </p>
        </div>

        <div className="glass-panel surface-glow rounded-2xl p-6 sm:p-8">
          <div className="mb-6">
            <h2 className="text-xl font-semibold">Sign in</h2>
            <p className="text-sm text-muted-foreground mt-1">Use your CryptEnv account credentials</p>
          </div>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              loginMutation.mutate()
            }}
            className="space-y-4"
          >
            <div className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                autoComplete="email"
                placeholder="you@company.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">Password</Label>
              <div className="relative">
                <Input
                  id="password"
                  type={showPw ? 'text' : 'password'}
                  autoComplete="current-password"
                  placeholder="Your password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  className="pr-10"
                />
                <button
                  type="button"
                  className="absolute right-2 top-1/2 -translate-y-1/2 p-1 text-muted-foreground"
                  onClick={() => setShowPw((v) => !v)}
                >
                  {showPw ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>
            <Button type="submit" className="w-full brand-gradient border-0 text-white" disabled={loginMutation.isPending}>
              {loginMutation.isPending ? 'Signing in…' : 'Sign in'}
            </Button>
          </form>
          <p className="mt-5 text-center text-sm text-muted-foreground">
            New here?{' '}
            <Link to="/register" className="text-primary font-semibold hover:underline">
              Create an account
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}
