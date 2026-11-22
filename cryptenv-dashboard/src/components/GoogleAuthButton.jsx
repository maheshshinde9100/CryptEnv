import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { authAPI } from '../lib/api'
import { toast } from 'sonner'
import { Button } from './ui/button'

export function GoogleAuthButton({ action = 'sign in' }) {
  const [loading, setLoading] = useState(false)

  const { data: status } = useQuery({
    queryKey: ['google-oauth-status'],
    queryFn: () => authAPI.googleStatus().then((res) => res.data),
    staleTime: 5 * 60 * 1000,
    retry: false,
  })

  const configured = status?.configured !== false

  const start = async () => {
    setLoading(true)
    try {
      const response = await authAPI.googleAuthorize(window.location.origin)
      window.location.assign(response.data.authorizationUrl)
    } catch (error) {
      toast.error(error.response?.data?.message || `Google ${action} is not available`)
      setLoading(false)
    }
  }

  return (
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
        onClick={start}
        disabled={!configured || loading}
      >
        <svg viewBox="0 0 24 24" className="h-4 w-4" aria-hidden="true">
          <path fill="#EA4335" d="M12 10.2v3.9h5.5c-.24 1.25-1.7 3.7-5.5 3.7a6.1 6.1 0 0 1 0-12.2c1.9 0 3.2.8 3.9 1.5l2.7-2.6C16.9 2.9 14.7 2 12 2a10 10 0 1 0 0 20c5.8 0 9.6-4.1 9.6-9.8 0-.7-.1-1.2-.2-2H12z" />
          <path fill="#4285F4" d="M21.6 12.2c0-.7-.1-1.2-.2-2H12v3.9h5.5a4.7 4.7 0 0 1-2 3.1l3.1 2.4c1.8-1.7 3-4.3 3-7.4z" />
          <path fill="#34A853" d="M12 22c2.7 0 5-.9 6.6-2.4l-3.1-2.4c-.9.6-2 1-3.5 1a6.1 6.1 0 0 1-5.7-4.2H2.9l-.1.1A10 10 0 0 0 12 22z" />
          <path fill="#FBBC05" d="M6.3 14a6.1 6.1 0 0 1 0-4L2.9 7.7A10 10 0 0 0 2 12c0 1.6.4 3 1 4.3l3.3-2.3z" />
        </svg>
        {loading ? 'Opening Google…' : 'Continue with Google'}
      </Button>
      {!configured && (
        <p className="mt-2 text-[11px] leading-relaxed text-muted-foreground">
          Google {action} needs <code className="text-foreground">GOOGLE_CLIENT_ID</code> and{' '}
          <code className="text-foreground">GOOGLE_CLIENT_SECRET</code> on the backend.
        </p>
      )}
    </>
  )
}
