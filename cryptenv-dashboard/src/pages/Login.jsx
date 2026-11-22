import { useEffect, useRef, useState } from 'react'
import { useNavigate, Link, useSearchParams } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { authAPI } from '../lib/api'
import { toast } from 'sonner'
import { Input } from '../components/ui/input'
import { Label } from '../components/ui/label'
import { Button } from '../components/ui/button'
import { Eye, EyeOff } from 'lucide-react'
import { BrandLogo } from '../components/BrandLogo'

export function Login() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPw, setShowPw] = useState(false)
  const [googleLoading, setGoogleLoading] = useState(false)
  const resetToastShown = useRef(false)

  const { data: googleStatus } = useQuery({
    queryKey: ['google-oauth-status'],
    queryFn: () => authAPI.googleStatus().then((res) => res.data),
    staleTime: 5 * 60 * 1000,
    retry: false,
  })

  useEffect(() => {
    if (searchParams.get('reset') && !resetToastShown.current) {
      resetToastShown.current = true
      toast.success('Password updated — sign in with your new password')
    }
  }, [searchParams])

  const loginMutation = useMutation({
    mutationFn: () => authAPI.login(email, password),
    onSuccess: (response) => {
      const data = response.data
      localStorage.setItem('token', data.token)
      if (data.refreshToken) localStorage.setItem('refreshToken', data.refreshToken)
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
      const message = error.response?.data?.message || 'Invalid email or password'
      toast.error(message)
      if (error.response?.status === 403 && /verify/i.test(message)) {
        navigate(`/verify-email?email=${encodeURIComponent(email)}`)
      }
    },
  })

  const startGoogle = async () => {
    setGoogleLoading(true)
    try {
      const response = await authAPI.googleAuthorize(window.location.origin)
      window.location.assign(response.data.authorizationUrl)
    } catch (error) {
      toast.error(error.response?.data?.message || 'Google sign-in is not available')
      setGoogleLoading(false)
    }
  }

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
              <div className="flex items-center justify-between">
                <Label htmlFor="password">Password</Label>
                <Link to="/forgot-password" className="text-xs text-muted-foreground hover:text-foreground">
                  Forgot password?
                </Link>
              </div>
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

          {googleStatus?.configured !== false && (
            <>
              <div className="my-5 flex items-center gap-3 text-[11px] text-muted-foreground">
                <span className="h-px flex-1 bg-border" />
                <span>or</span>
                <span className="h-px flex-1 bg-border" />
              </div>
              <Button
                type="button"
                variant="outline"
                className="h-[53px] w-full gap-2"
                onClick={startGoogle}
                disabled={googleLoading}
              >
                <svg viewBox="0 0 24 24" className="h-4 w-4" aria-hidden="true">
                  <path fill="#EA4335" d="M12 10.2v3.9h5.5c-.24 1.25-1.7 3.7-5.5 3.7a6.1 6.1 0 0 1 0-12.2c1.9 0 3.2.8 3.9 1.5l2.7-2.6C16.9 2.9 14.7 2 12 2a10 10 0 1 0 0 20c5.8 0 9.6-4.1 9.6-9.8 0-.7-.1-1.2-.2-2H12z" />
                  <path fill="#4285F4" d="M21.6 12.2c0-.7-.1-1.2-.2-2H12v3.9h5.5a4.7 4.7 0 0 1-2 3.1l3.1 2.4c1.8-1.7 3-4.3 3-7.4z" />
                  <path fill="#34A853" d="M12 22c2.7 0 5-.9 6.6-2.4l-3.1-2.4c-.9.6-2 1-3.5 1a6.1 6.1 0 0 1-5.7-4.2H2.9l-.1.1A10 10 0 0 0 12 22z" />
                  <path fill="#FBBC05" d="M6.3 14a6.1 6.1 0 0 1 0-4L2.9 7.7A10 10 0 0 0 2 12c0 1.6.4 3 1 4.3l3.3-2.3z" />
                </svg>
                {googleLoading ? 'Opening Google…' : 'Continue with Google'}
              </Button>
            </>
          )}

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
