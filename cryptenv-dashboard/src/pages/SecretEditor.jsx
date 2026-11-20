import { useEffect, useState } from 'react'
import { useNavigate, useParams, useLocation, useOutletContext } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { secretsAPI, environmentAPI, workspaceAPI } from '../lib/api'
import { toast } from 'sonner'
import { Button } from '../components/ui/button'
import { Input, Textarea } from '../components/ui/input'
import { Label } from '../components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../components/ui/select'
import { ArrowLeft, Eye, EyeOff, Lock } from 'lucide-react'

const SECRET_TYPES = ['TEXT', 'MULTILINE', 'JSON', 'CERTIFICATE', 'SSH_KEY', 'BINARY']

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
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState(
    activeWorkspace?.id ? String(activeWorkspace.id) : ''
  )
  const [selectedEnvironmentId, setSelectedEnvironmentId] = useState(
    location.state?.environmentId ? String(location.state.environmentId) : ''
  )
  const [description, setDescription] = useState('')
  const [secretType, setSecretType] = useState('TEXT')
  const [tags, setTags] = useState('')

  const { data: workspaces } = useQuery({
    queryKey: ['workspaces'],
    queryFn: () => workspaceAPI.list().then((res) => res.data),
  })

  useEffect(() => {
    if (!selectedWorkspaceId && workspaces?.[0]?.id) {
      setSelectedWorkspaceId(String(activeWorkspace?.id || workspaces[0].id))
    }
  }, [activeWorkspace, workspaces, selectedWorkspaceId])

  const { data: environments = [] } = useQuery({
    queryKey: ['environments', selectedWorkspaceId],
    queryFn: () => environmentAPI.list(Number(selectedWorkspaceId)).then((res) => res.data),
    enabled: !!selectedWorkspaceId,
  })

  const { data: existingSecret } = useQuery({
    queryKey: ['secret', key, location.state?.environmentId],
    queryFn: () =>
      location.state?.environmentId
        ? secretsAPI.getByEnvironment(location.state.environmentId, key).then((res) => res.data)
        : secretsAPI.get(key).then((res) => res.data),
    enabled: isEdit,
  })

  useEffect(() => {
    if (existingSecret) {
      setSecretKey(existingSecret.key || '')
      setSecretValue(existingSecret.value || '')
      setDescription(existingSecret.description || '')
      setSecretType(existingSecret.secretType || 'TEXT')
      setTags(existingSecret.tags || '')
      if (existingSecret.environmentId) setSelectedEnvironmentId(String(existingSecret.environmentId))
    }
  }, [existingSecret])

  const createMutation = useMutation({
    mutationFn: (data) => secretsAPI.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['secrets'] })
      toast.success('Secret created and encrypted')
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
      description: description || undefined,
      tags: tags || undefined,
      secretType,
      encrypted: true,
    })
  }

  return (
    <div className="max-w-2xl space-y-8">
      <header className="flex items-center gap-3">
        <Button variant="ghost" size="icon" onClick={() => navigate('/secrets')} aria-label="Back to secrets">
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div>
          <p className="eyebrow">{isEdit ? 'Edit secret' : 'New secret'}</p>
          <h1 className="mt-2 text-xl">{isEdit ? key : 'Add a secret'}</h1>
        </div>
      </header>

      <form onSubmit={handleSubmit} className="rounded-lg border border-border">
        <div className="flex items-center gap-2 border-b border-border px-5 py-4">
          <Lock className="h-4 w-4 text-muted-foreground" />
          <p className="text-sm text-muted-foreground">
            Values are encrypted server-side with AES-256-GCM before storage.
          </p>
        </div>

        <div className="space-y-5 p-5">
          {!isEdit && (
            <div className="grid gap-4 sm:grid-cols-2">
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
                    {(workspaces || []).map((ws) => (
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
                    {environments.map((env) => (
                      <SelectItem key={env.id} value={String(env.id)}>
                        {env.displayName || env.label || env.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
          )}

          <div className="space-y-2">
            <Label htmlFor="secret-key">Key</Label>
            <Input
              id="secret-key"
              value={secretKey}
              onChange={(e) => setSecretKey(e.target.value.toUpperCase().replace(/\s/g, '_'))}
              disabled={isEdit}
              required
              className="font-mono"
              placeholder="DATABASE_URL"
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="secret-value">Value</Label>
            <div className="relative">
              {secretType === 'MULTILINE' || secretType === 'CERTIFICATE' || secretType === 'SSH_KEY' ? (
                <Textarea
                  id="secret-value"
                  value={secretValue}
                  onChange={(e) => setSecretValue(e.target.value)}
                  required
                  rows={8}
                  className="pr-10 font-mono"
                />
              ) : (
                <Input
                  id="secret-value"
                  type={showValue ? 'text' : 'password'}
                  value={secretValue}
                  onChange={(e) => setSecretValue(e.target.value)}
                  required
                  className="pr-12 font-mono"
                />
              )}
              <button
                type="button"
                className="absolute right-2 top-3 p-1 text-muted-foreground transition-colors hover:text-foreground"
                onClick={() => setShowValue((v) => !v)}
                aria-label={showValue ? 'Hide value' : 'Show value'}
              >
                {showValue ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label>Type</Label>
              <select
                value={secretType}
                onChange={(e) => setSecretType(e.target.value)}
                disabled={isEdit}
                className="h-11 w-full rounded-md border border-border bg-surface px-3 text-sm outline-none focus:border-tertiary disabled:opacity-60"
              >
                {SECRET_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="secret-tags">Tags</Label>
              <Input
                id="secret-tags"
                value={tags}
                onChange={(e) => setTags(e.target.value)}
                disabled={isEdit}
                placeholder="database, payments"
              />
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="secret-description">Description</Label>
            <Input
              id="secret-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="What is this secret used for?"
            />
          </div>
        </div>

        <div className="flex gap-2 border-t border-border px-5 py-4">
          <Button type="submit" disabled={createMutation.isPending || updateMutation.isPending}>
            {createMutation.isPending || updateMutation.isPending ? 'Saving…' : isEdit ? 'Update secret' : 'Create secret'}
          </Button>
          <Button type="button" variant="outline" onClick={() => navigate('/secrets')}>
            Cancel
          </Button>
        </div>
      </form>
    </div>
  )
}
