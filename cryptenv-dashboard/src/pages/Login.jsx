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
    <div className="flex min-h-screen flex-col items-center justify-center bg-background px-4 py-16">
      <div className="w-full max-w-sm">
        <div className="flex flex-col items-center gap-3 text-center">
          <Link to="/" className="inline-flex items-center gap-2.5">
            <BrandLogo size="md" />
            <span className="text-lg font-normal tracking-[-0.02em]">CryptEnv</span>
          </Link>
          <p className="text-sm text-muted-foreground">
            Encrypted environment secrets for modern teams.
          </p>
        </div>

        <div className="mt-10 rounded-lg border border-border p-6 sm:p-7">
          <div className="mb-6">
            <h1 className="text-2xl font-light tracking-[-0.02em]">Sign in</h1>
            <p className="mt-1.5 text-sm text-muted-foreground">Use your CryptEnv account credentials</p>
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
            <Button type="submit" className="h-[53px] w-full" disabled={loginMutation.isPending}>
              {loginMutation.isPending ? 'Signing in…' : 'Sign in'}
            </Button>
          </form>
          <p className="mt-5 text-center text-sm text-muted-foreground">
            New here?{' '}
            <Link to="/register" className="text-foreground hover:underline">
              Create an account
            </Link>
          </p>
        </div>
        <p className="mt-6 text-center text-xs text-muted-foreground">
          <Link to="/" className="hover:text-foreground">
            Back to home
          </Link>
        </p>
      </div>
    </div>
  )
}
