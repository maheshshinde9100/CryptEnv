import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '../lib/api'
import { Button } from '../components/ui/button'
import { Input } from '../components/ui/input'
import { ArrowLeft, Save } from 'lucide-react'
import { toast } from 'sonner'

export default function SecretEditor() {
  const { id } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [key, setKey] = useState('')
  const [value, setValue] = useState('')

  const { data: secret, isLoading } = useQuery({
    queryKey: ['secret', id],
    queryFn: async () => {
      if (id === 'new') return null
      const response = await api.get(`/secrets/${id}`)
      return response.data
    },
    enabled: id !== 'new',
    onSuccess: (data) => {
      if (data) {
        setKey(data.key)
        setValue(data.value)
      }
    },
  })

  const mutation = useMutation({
    mutationFn: async (data: { key: string; value: string }) => {
      if (id === 'new') {
        return await api.post('/secrets', data)
      }
      return await api.put(`/secrets/${id}`, data)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['secrets'] })
      toast.success(id === 'new' ? 'Secret created successfully' : 'Secret updated successfully')
      navigate('/secrets')
    },
    onError: () => {
      toast.error('Failed to save secret')
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!key || !value) {
      toast.error('Please fill in all fields')
      return
    }
    mutation.mutate({ key, value })
  }

  if (id !== 'new' && isLoading) {
    return (
      <div className="space-y-6">
        <div className="h-8 bg-muted animate-pulse rounded w-1/4" />
        <div className="space-y-4">
          <div className="h-12 bg-muted animate-pulse rounded" />
          <div className="h-32 bg-muted animate-pulse rounded" />
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center space-x-4">
        <Button variant="ghost" size="icon" onClick={() => navigate('/secrets')}>
          <ArrowLeft className="h-5 w-5" />
        </Button>
        <div>
          <h1 className="text-3xl font-bold">
            {id === 'new' ? 'Create Secret' : 'Edit Secret'}
          </h1>
          <p className="text-muted-foreground mt-2">
            {id === 'new' ? 'Add a new secret to your workspace' : 'Update secret value'}
          </p>
        </div>
      </div>

      <div className="bg-card border border-border rounded-lg p-6">
        <form onSubmit={handleSubmit} className="space-y-6">
          <div>
            <label htmlFor="key" className="block text-sm font-medium mb-2">
              Secret Key
            </label>
            <Input
              id="key"
              value={key}
              onChange={(e) => setKey(e.target.value)}
              placeholder="DATABASE_URL"
              disabled={id !== 'new'}
              required
            />
            {id !== 'new' && (
              <p className="text-sm text-muted-foreground mt-1">
                Secret keys cannot be changed after creation
              </p>
            )}
          </div>

          <div>
            <label htmlFor="value" className="block text-sm font-medium mb-2">
              Secret Value
            </label>
            <textarea
              id="value"
              value={value}
              onChange={(e) => setValue(e.target.value)}
              placeholder="Enter your secret value"
              className="flex min-h-[120px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 font-mono"
              required
            />
          </div>

          <div className="flex justify-end space-x-3">
            <Button
              type="button"
              variant="outline"
              onClick={() => navigate('/secrets')}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              <Save className="h-4 w-4 mr-2" />
              {mutation.isPending ? 'Saving...' : id === 'new' ? 'Create Secret' : 'Update Secret'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}
