import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { secretsAPI } from '../lib/api'
import { toast } from 'sonner'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Button } from '../components/ui/button'
import { Input } from '../components/ui/input'
import { Label } from '../components/ui/label'
import { ArrowLeft } from 'lucide-react'

export function SecretEditor() {
  const navigate = useNavigate()
  const { key } = useParams()
  const queryClient = useQueryClient()
  const isEdit = !!key

  const [secretKey, setSecretKey] = useState('')
  const [secretValue, setSecretValue] = useState('')

  const { data: existingSecret } = useQuery({
    queryKey: ['secret', key],
    queryFn: () => secretsAPI.get(key).then((res) => res.data),
    enabled: isEdit,
    onSuccess: (data) => {
      setSecretKey(data.key)
      setSecretValue(data.value)
    },
  })

  const createMutation = useMutation({
    mutationFn: (data) => secretsAPI.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries(['secrets'])
      toast.success('Secret created successfully')
      navigate('/secrets')
    },
    onError: (error) => {
      toast.error(error.response?.data?.message || 'Failed to create secret')
    },
  })

  const updateMutation = useMutation({
    mutationFn: (data) => secretsAPI.update(key, data),
    onSuccess: () => {
      queryClient.invalidateQueries(['secrets'])
      queryClient.invalidateQueries(['secret', key])
      toast.success('Secret updated successfully')
      navigate('/secrets')
    },
    onError: (error) => {
      toast.error(error.response?.data?.message || 'Failed to update secret')
    },
  })

  const handleSubmit = (e) => {
    e.preventDefault()
    const data = { key: secretKey, value: secretValue }

    if (isEdit) {
      updateMutation.mutate(data)
    } else {
      createMutation.mutate(data)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => navigate('/secrets')}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div>
          <h1 className="text-3xl font-bold tracking-tight">
            {isEdit ? 'Edit Secret' : 'Add New Secret'}
          </h1>
          <p className="text-muted-foreground">
            {isEdit ? 'Update your secret value' : 'Add a new secret to your workspace'}
          </p>
        </div>
      </div>

      <Card className="max-w-2xl">
        <CardHeader>
          <CardTitle>{isEdit ? 'Edit Secret' : 'New Secret'}</CardTitle>
          <CardDescription>
            {isEdit ? 'Update the secret value' : 'Enter the key and value for your new secret'}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="key">Secret Key</Label>
              <Input
                id="key"
                placeholder="e.g., DATABASE_URL"
                value={secretKey}
                onChange={(e) => setSecretKey(e.target.value)}
                disabled={isEdit}
                required
              />
              {isEdit && (
                <p className="text-sm text-muted-foreground">
                  Secret keys cannot be changed after creation
                </p>
              )}
            </div>
            <div className="space-y-2">
              <Label htmlFor="value">Secret Value</Label>
              <Input
                id="value"
                type="password"
                placeholder="Enter your secret value"
                value={secretValue}
                onChange={(e) => setSecretValue(e.target.value)}
                required
              />
            </div>
            <div className="flex gap-4">
              <Button
                type="submit"
                disabled={createMutation.isPending || updateMutation.isPending}
              >
                {createMutation.isPending || updateMutation.isPending
                  ? 'Saving...'
                  : isEdit
                  ? 'Update Secret'
                  : 'Create Secret'}
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={() => navigate('/secrets')}
              >
                Cancel
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
