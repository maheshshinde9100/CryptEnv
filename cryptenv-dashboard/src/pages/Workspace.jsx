import { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useOutletContext } from 'react-router-dom'
import { workspaceAPI, environmentAPI } from '../lib/api'
import { toast } from 'sonner'
import { Button } from '../components/ui/button'
import { Input } from '../components/ui/input'
import { Label } from '../components/ui/label'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog'
import { Skeleton } from '../components/ui/skeleton'
import { Plus, Trash2, KeyRound, ShieldCheck, ShieldOff, Pencil } from 'lucide-react'

const ENV_TYPES = ['DEVELOPMENT', 'STAGING', 'PRODUCTION']

function randomWorkspaceKey() {
  const bytes = new Uint8Array(32)
  crypto.getRandomValues(bytes)
  let bin = ''
  bytes.forEach((b) => {
    bin += String.fromCharCode(b)
  })
  return btoa(bin)
}

export function Workspace() {
  const queryClient = useQueryClient()
  const { activeWorkspace } = useOutletContext() || {}
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [envOpen, setEnvOpen] = useState(false)
  const [keyOpen, setKeyOpen] = useState(false)
  const [renameTarget, setRenameTarget] = useState(null)
  const [newLabel, setNewLabel] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [encryptionKey, setEncryptionKey] = useState('')
  const [newEnvName, setNewEnvName] = useState('DEVELOPMENT')
  const [keyTargetId, setKeyTargetId] = useState(null)

  useEffect(() => {
    if (!selectedWorkspaceId && activeWorkspace?.id) setSelectedWorkspaceId(activeWorkspace.id)
  }, [activeWorkspace?.id, selectedWorkspaceId])

  const { data: workspaces = [], isLoading: workspacesLoading } = useQuery({
    queryKey: ['workspaces'],
    queryFn: () => workspaceAPI.list().then((res) => res.data),
  })

  const { data: environments = [], isLoading: envsLoading } = useQuery({
    queryKey: ['environments', selectedWorkspaceId],
    queryFn: () => environmentAPI.list(selectedWorkspaceId).then((res) => res.data),
    enabled: !!selectedWorkspaceId,
  })

  const refreshWorkspace = () => {
    queryClient.invalidateQueries({ queryKey: ['workspaces'] })
    queryClient.invalidateQueries({ queryKey: ['environments', selectedWorkspaceId] })
  }

  const createWorkspaceMutation = useMutation({
    mutationFn: (data) => workspaceAPI.create(data),
    onSuccess: (res) => {
      refreshWorkspace()
      toast.success('Workspace created')
      setCreateOpen(false)
      setName('')
      setDescription('')
      setEncryptionKey('')
      if (res.data?.id) {
        localStorage.setItem('activeWorkspaceId', String(res.data.id))
        setSelectedWorkspaceId(res.data.id)
      }
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Failed to create workspace'),
  })

  const deleteWorkspaceMutation = useMutation({
    mutationFn: (id) => workspaceAPI.delete(id),
    onSuccess: () => {
      refreshWorkspace()
      toast.success('Workspace deleted')
      setSelectedWorkspaceId(null)
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Delete failed'),
  })

  const createEnvMutation = useMutation({
    mutationFn: (data) => environmentAPI.create(data),
    onSuccess: () => {
      refreshWorkspace()
      toast.success('Environment created')
      setEnvOpen(false)
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Failed to create environment'),
  })

  const updateEnvMutation = useMutation({
    mutationFn: ({ id, data }) => environmentAPI.update(id, data),
    onSuccess: () => {
      refreshWorkspace()
      toast.success('Environment updated')
      setRenameTarget(null)
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Update failed'),
  })

  const deleteEnvMutation = useMutation({
    mutationFn: (id) => environmentAPI.delete(id),
    onSuccess: () => {
      refreshWorkspace()
      toast.success('Environment deleted')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Delete failed'),
  })

  const setKeyMutation = useMutation({
    mutationFn: ({ id, key }) => workspaceAPI.setEncryptionKey(id, key),
    onSuccess: () => {
      refreshWorkspace()
      toast.success('Workspace encryption key updated')
      setKeyOpen(false)
      setEncryptionKey('')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Failed to set encryption key'),
  })

  const envLabel = (env) => env.displayName || env.label || env.name

  return (
    <div className="space-y-8">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="eyebrow">Workspaces</p>
          <h1 className="page-title mt-3">Projects and environments</h1>
          <p className="page-subtitle">Isolate projects with per-workspace encryption keys and scoped environments</p>
        </div>
        <Button onClick={() => setCreateOpen(true)}>
          <Plus className="h-4 w-4" />
          New workspace
        </Button>
      </header>

      <div className="grid gap-6 lg:grid-cols-2">
        <section className="rounded-lg border border-border">
          <div className="border-b border-border px-5 py-4">
            <h2 className="text-base">Your workspaces</h2>
            <p className="text-xs text-muted-foreground">Select one to manage its environments</p>
          </div>
          {workspacesLoading ? (
            <div className="space-y-px p-2">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-14 w-full" />
              ))}
            </div>
          ) : workspaces.length === 0 ? (
            <p className="px-5 py-12 text-center text-sm text-muted-foreground">No workspaces yet.</p>
          ) : (
            <ul className="divide-y divide-border/60">
              {workspaces.map((workspace) => {
                const selected = selectedWorkspaceId === workspace.id
                return (
                  <li key={workspace.id}>
                    <div
                      role="button"
                      tabIndex={0}
                      onClick={() => {
                        setSelectedWorkspaceId(workspace.id)
                        localStorage.setItem('activeWorkspaceId', String(workspace.id))
                      }}
                      onKeyDown={(e) => e.key === 'Enter' && setSelectedWorkspaceId(workspace.id)}
                      className={`flex cursor-pointer items-center justify-between gap-3 px-5 py-4 transition-colors ${
                        selected ? 'bg-white/[0.04]' : 'hover:bg-white/[0.02]'
                      }`}
                    >
                      <div className="min-w-0">
                        <p className="truncate text-sm">{workspace.name}</p>
                        <p className="mt-1 flex items-center gap-1.5 text-xs text-muted-foreground">
                          {workspace.hasEncryptionKey ? (
                            <>
                              <ShieldCheck className="h-3.5 w-3.5 text-tertiary" /> Encryption key configured
                            </>
                          ) : (
                            <>
                              <ShieldOff className="h-3.5 w-3.5 text-danger" /> No key — set one before storing secrets
                            </>
                          )}
                        </p>
                      </div>
                      <div className="flex shrink-0 items-center gap-1" onClick={(e) => e.stopPropagation()}>
                        <Button
                          variant="ghost"
                          size="icon"
                          title="Set encryption key"
                          onClick={() => {
                            setKeyTargetId(workspace.id)
                            setEncryptionKey('')
                            setKeyOpen(true)
                          }}
                        >
                          <KeyRound className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          title="Delete workspace"
                          onClick={() => {
                            if (confirm(`Delete workspace "${workspace.name}"?`)) {
                              deleteWorkspaceMutation.mutate(workspace.id)
                            }
                          }}
                        >
                          <Trash2 className="h-4 w-4 text-danger" />
                        </Button>
                      </div>
                    </div>
                  </li>
                )
              })}
            </ul>
          )}
        </section>

        <section className="rounded-lg border border-border">
          <div className="flex items-center justify-between border-b border-border px-5 py-4">
            <div>
              <h2 className="text-base">Environments</h2>
              <p className="text-xs text-muted-foreground">Development · Staging · Production</p>
            </div>
            <Button size="sm" disabled={!selectedWorkspaceId} onClick={() => setEnvOpen(true)}>
              <Plus className="h-4 w-4" />
              Add
            </Button>
          </div>
          {!selectedWorkspaceId ? (
            <p className="px-5 py-16 text-center text-sm text-muted-foreground">Select a workspace</p>
          ) : envsLoading ? (
            <div className="p-4">
              <Skeleton className="h-24 w-full" />
            </div>
          ) : environments.length === 0 ? (
            <p className="px-5 py-16 text-center text-sm text-muted-foreground">
              No environments — create Development to start.
            </p>
          ) : (
            <ul className="divide-y divide-border/60">
              {environments.map((env) => (
                <li key={env.id} className="flex items-center justify-between gap-3 px-5 py-3.5">
                  <div className="min-w-0">
                    <p className="truncate font-mono text-sm">{envLabel(env)}</p>
                    <p className="mt-0.5 text-xs text-muted-foreground">
                      {env.isActive === false ? 'Inactive' : 'Active'}
                      {env.isProtected && ' · Protected (owner/admin or JIT)'}
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center gap-1">
                    {env.isProtected && <span className="chip">Protected</span>}
                    <Button
                      variant="ghost"
                      size="icon"
                      title={env.isProtected ? 'Remove protection' : 'Protect environment'}
                      onClick={() => updateEnvMutation.mutate({ id: env.id, data: { isProtected: !env.isProtected } })}
                    >
                      {env.isProtected ? <ShieldOff className="h-4 w-4" /> : <ShieldCheck className="h-4 w-4" />}
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      title="Rename"
                      onClick={() => {
                        setRenameTarget(env)
                        setNewLabel(env.label || env.displayName || env.name)
                      }}
                    >
                      <Pencil className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      title="Delete"
                      onClick={() => {
                        if (confirm(`Delete environment ${env.name}?`)) deleteEnvMutation.mutate(env.id)
                      }}
                    >
                      <Trash2 className="h-4 w-4 text-danger" />
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create workspace</DialogTitle>
            <DialogDescription>
              The workspace encryption key wraps every secret. Store it safely — it is required to decrypt values.
            </DialogDescription>
          </DialogHeader>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              if (!encryptionKey || encryptionKey.length < 16) {
                toast.error('Encryption key must be at least 16 characters')
                return
              }
              createWorkspaceMutation.mutate({
                name,
                description: description || undefined,
                workspaceEncryptionKey: encryptionKey,
              })
            }}
            className="space-y-4"
          >
            <div className="space-y-2">
              <Label>Name</Label>
              <Input value={name} onChange={(e) => setName(e.target.value)} required minLength={3} />
            </div>
            <div className="space-y-2">
              <Label>Description</Label>
              <Input value={description} onChange={(e) => setDescription(e.target.value)} />
            </div>
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <Label>Workspace encryption key</Label>
                <Button type="button" variant="ghost" size="sm" onClick={() => setEncryptionKey(randomWorkspaceKey())}>
                  Generate
                </Button>
              </div>
              <Input
                value={encryptionKey}
                onChange={(e) => setEncryptionKey(e.target.value)}
                placeholder="Paste or generate a strong key"
                required
                minLength={16}
                className="font-mono text-xs"
              />
              <p className="text-xs text-muted-foreground">
                Also export this as CRYPTENV_WORKSPACE_ENCRYPTION_KEY for the SDK and CLI.
              </p>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setCreateOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={createWorkspaceMutation.isPending}>
                {createWorkspaceMutation.isPending ? 'Creating…' : 'Create workspace'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={envOpen} onOpenChange={setEnvOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Add environment</DialogTitle>
            <DialogDescription>Environment types are fixed to keep secret scoping predictable.</DialogDescription>
          </DialogHeader>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              createEnvMutation.mutate({ name: newEnvName, workspaceId: selectedWorkspaceId })
            }}
            className="space-y-4"
          >
            <div className="space-y-2">
              <Label>Type</Label>
              <select
                value={newEnvName}
                onChange={(e) => setNewEnvName(e.target.value)}
                className="h-11 w-full rounded-md border border-border bg-surface px-3 text-sm outline-none focus:border-tertiary"
              >
                {ENV_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setEnvOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={createEnvMutation.isPending}>
                Create environment
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={!!renameTarget} onOpenChange={(open) => !open && setRenameTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Rename environment</DialogTitle>
            <DialogDescription>Display labels are cosmetic; API scoping still uses the environment name.</DialogDescription>
          </DialogHeader>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              updateEnvMutation.mutate({ id: renameTarget.id, data: { label: newLabel } })
            }}
            className="space-y-4"
          >
            <div className="space-y-2">
              <Label>Label</Label>
              <Input value={newLabel} onChange={(e) => setNewLabel(e.target.value)} required />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setRenameTarget(null)}>
                Cancel
              </Button>
              <Button type="submit" disabled={updateEnvMutation.isPending}>
                Save label
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={keyOpen} onOpenChange={setKeyOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Set workspace encryption key</DialogTitle>
            <DialogDescription>
              Required after master-key rotation. Secrets encrypted with a different key will not decrypt until re-saved.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <Label>New key</Label>
                <Button type="button" variant="ghost" size="sm" onClick={() => setEncryptionKey(randomWorkspaceKey())}>
                  Generate
                </Button>
              </div>
              <Input
                className="font-mono text-xs"
                value={encryptionKey}
                onChange={(e) => setEncryptionKey(e.target.value)}
                minLength={16}
              />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setKeyOpen(false)}>
                Cancel
              </Button>
              <Button
                disabled={setKeyMutation.isPending || encryptionKey.length < 16}
                onClick={() => setKeyMutation.mutate({ id: keyTargetId, key: encryptionKey })}
              >
                Save key
              </Button>
            </DialogFooter>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}
