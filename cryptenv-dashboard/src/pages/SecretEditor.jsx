import { useEffect, useState } from 'react'
import { useNavigate, useParams, useLocation, useOutletContext } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { secretsAPI, environmentAPI, workspaceAPI } from '../lib/api'
import { toast } from 'sonner'
import { Button } from '../components/ui/button'
import { Input } from '../components/ui/input'
import { Label } from '../components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../components/ui/select'
import { ArrowLeft, Eye, EyeOff } from 'lucide-react'

export function SecretEditor() {
  const navigate = useNavigate()
  const location = useLocation()
  const { key } = useParams()
  const queryClient = useQueryClient()
  const { activeWorkspace } = useOutletContext() || {}
  const isEdit = !!key

  const [secretKey, setSecretKey] = useState('')
  const [secretValue, setSecretValue] = useState('')
  const [showValue, setShowValue] = useState(false)
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState('')
  const [selectedEnvironmentId, setSelectedEnvironmentId] = useState(
    location.state?.environmentId ? String(location.state.environmentId) : ''
  )
  const [description, setDescription] = useState('')

  const { data: workspaces } = useQuery({
    queryKey: ['workspaces'],
    queryFn: () => workspaceAPI.list().then((res) => res.data),
  })

  useEffect(() => {
    if (!selectedWorkspaceId && (activeWorkspace?.id || workspaces?.[0]?.id)) {
      setSelectedWorkspaceId(String(activeWorkspace?.id || workspaces[0].id))
    }
  }, [activeWorkspace, workspaces, selectedWorkspaceId])

  const { data: environments } = useQuery({
    queryKey: ['environments', selectedWorkspaceId],
    queryFn: () => environmentAPI.list(Number(selectedWorkspaceId)).then((res) => res.data),
    enabled: !!selectedWorkspaceId,
  })

  const { data: existingSecret } = useQuery({
    queryKey: ['secret', key],
    queryFn: () => secretsAPI.get(key).then((res) => res.data),
    enabled: isEdit,
  })

  useEffect(() => {
    if (existingSecret) {
      setSecretKey(existingSecret.key || '')
      setSecretValue(existingSecret.value || '')
      setDescription(existingSecret.description || '')
      if (existingSecret.environmentId) {
        setSelectedEnvironmentId(String(existingSecret.environmentId))
      }
    }
  }, [existingSecret])

  const createMutation = useMutation({
    mutationFn: (data) => secretsAPI.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['secrets'] })
      toast.success('Secret created (encrypted at rest)')
      navigate('/secrets')
    },
    onError: (error) => toast.error(error.response?.data?.message || 'Failed to create secret'),
  })

  const updateMutation = useMutation({
    mutationFn: (data) =>
      secretsAPI.update(Number(selectedEnvironmentId || existingSecret?.environmentId), key, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['secrets'] })
      queryClient.invalidateQueries({ queryKey: ['secret', key] })
      toast.success('Secret updated')
      navigate('/secrets')
    },
    onError: (error) => toast.error(error.response?.data?.message || 'Failed to update secret'),
  })

  const handleSubmit = (e) => {
    e.preventDefault()
    if (isEdit) {
      updateMutation.mutate({ value: secretValue, description })
      return
    }
    if (!selectedEnvironmentId) {
      toast.error('Select an environment')
      return
    }
    createMutation.mutate({
      key: secretKey,
      value: secretValue,
      environmentId: Number(selectedEnvironmentId),
      description,
      encrypted: true,
    })
  }

  return (
    <div className="space-y-6 max-w-2xl">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => navigate('/secrets')}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{isEdit ? 'Edit secret' : 'Add secret'}</h1>
          <p className="text-muted-foreground">Server encrypts with your workspace key before storage</p>
        </div>
      </div>

      <div className="glass-panel rounded-2xl p-6">
        <form onSubmit={handleSubmit} className="space-y-4">
          {!isEdit && (
            <>
              <div className="space-y-2">
                <Label>Workspace</Label>
                <Select
                  value={selectedWorkspaceId}
                  onValueChange={(val) => {
                    setSelectedWorkspaceId(val)
                    setSelectedEnvironmentId('')
                  }}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select workspace" />
                  </SelectTrigger>
                  <SelectContent>
                    {workspaces?.map((ws) => (
                      <SelectItem key={ws.id} value={String(ws.id)}>
                        {ws.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>Environment</Label>
                <Select
                  value={selectedEnvironmentId}
                  onValueChange={setSelectedEnvironmentId}
                  disabled={!selectedWorkspaceId}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select environment" />
                  </SelectTrigger>
                  <SelectContent>
                    {environments?.map((env) => (
                      <SelectItem key={env.id} value={String(env.id)}>
                        {env.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </>
          )}
          <div className="space-y-2">
            <Label>Key</Label>
            <Input
              value={secretKey}
              onChange={(e) => setSecretKey(e.target.value)}
              disabled={isEdit}
              required
              className="font-mono"
              placeholder="DATABASE_URL"
            />
          </div>
          <div className="space-y-2">
            <Label>Value</Label>
            <div className="relative">
              <Input
                type={showValue ? 'text' : 'password'}
                value={secretValue}
                onChange={(e) => setSecretValue(e.target.value)}
                required
                className="pr-10 font-mono"
              />
              <button
                type="button"
                className="absolute right-2 top-1/2 -translate-y-1/2 p-1 text-muted-foreground"
                onClick={() => setShowValue((v) => !v)}
              >
                {showValue ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
          </div>
          <div className="space-y-2">
            <Label>Description</Label>
            <Input value={description} onChange={(e) => setDescription(e.target.value)} />
          </div>
          <div className="flex gap-3 pt-2">
            <Button
              type="submit"
              className="brand-gradient border-0 text-white"
              disabled={createMutation.isPending || updateMutation.isPending}
            >
              {createMutation.isPending || updateMutation.isPending ? 'Saving…' : isEdit ? 'Update' : 'Create'}
            </Button>
            <Button type="button" variant="outline" onClick={() => navigate('/secrets')}>
              Cancel
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}
