import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { BrandLogo } from '../components/BrandLogo'

export function GoogleCallback() {
  const navigate = useNavigate()
  const [error, setError] = useState('')

  useEffect(() => {
    const params = new URLSearchParams(window.location.hash.replace(/^#/, ''))
    const failure = params.get('error')
    const token = params.get('token')
    const refreshToken = params.get('refreshToken')

    if (failure) {
      setError(failure)
      return
    }
    if (!token) {
      setError('Google sign-in did not return a session')
      return
    }
    localStorage.setItem('token', token)
    if (refreshToken) {
      localStorage.setItem('refreshToken', refreshToken)
    }
    toast.success('Signed in with Google')
    navigate('/overview', { replace: true })
  }, [navigate])

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background px-4 py-16">
      <div className="w-full max-w-sm text-center">
        <Link to="/" className="inline-flex items-center gap-2.5">
          <BrandLogo size="md" />
          <span className="text-lg font-normal tracking-[-0.02em]">CryptEnv</span>
        </Link>
        {error ? (
          <>
            <h1 className="mt-10 text-2xl font-light tracking-[-0.02em]">Google sign-in failed</h1>
            <p className="mt-2 text-sm text-muted-foreground">{error}</p>
            <Link to="/login" className="mt-6 inline-block text-sm text-foreground hover:underline">
              Back to sign in
            </Link>
          </>
        ) : (
          <p className="mt-10 text-sm text-muted-foreground">Completing sign-in…</p>
        )}
      </div>
    </div>
  )
}