import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { workspaceAPI, environmentAPI } from '../lib/api'
import { toast } from 'sonner'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../components/ui/select'
import { Button } from '../components/ui/button'
import { Input } from '../components/ui/input'
import { Label } from '../components/ui/label'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog'
import { Skeleton } from '../components/ui/skeleton'
import { Plus, Trash2, FolderOpen, KeyRound, ShieldCheck } from 'lucide-react'

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
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)
  const [isEnvDialogOpen, setIsEnvDialogOpen] = useState(false)
  const [isKeyDialogOpen, setIsKeyDialogOpen] = useState(false)
  const [newWorkspaceName, setNewWorkspaceName] = useState('')
  const [newWorkspaceDesc, setNewWorkspaceDesc] = useState('')
  const [encryptionKey, setEncryptionKey] = useState('')
  const [newEnvName, setNewEnvName] = useState('DEVELOPMENT')
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState(null)
  const [keyTargetId, setKeyTargetId] = useState(null)

  const { data: workspaces, isLoading: workspacesLoading } = useQuery({
    queryKey: ['workspaces'],
    queryFn: () => workspaceAPI.list().then((res) => res.data),
  })

  const { data: environments, isLoading: envsLoading } = useQuery({
    queryKey: ['environments', selectedWorkspaceId],
    queryFn: () => environmentAPI.list(selectedWorkspaceId).then((res) => res.data),
    enabled: !!selectedWorkspaceId,
  })

  const createWorkspaceMutation = useMutation({
    mutationFn: (data) => workspaceAPI.create(data),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['workspaces'] })
      toast.success('Workspace created')
      setIsCreateDialogOpen(false)
      setNewWorkspaceName('')
      setNewWorkspaceDesc('')
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
      queryClient.invalidateQueries({ queryKey: ['workspaces'] })
      toast.success('Workspace deleted')
      setSelectedWorkspaceId(null)
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Delete failed'),
  })

  const createEnvMutation = useMutation({
    mutationFn: (data) => environmentAPI.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['environments', selectedWorkspaceId] })
      toast.success('Environment created')
      setIsEnvDialogOpen(false)
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Failed to create environment'),
  })

  const deleteEnvMutation = useMutation({
    mutationFn: (id) => environmentAPI.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['environments', selectedWorkspaceId] })
      toast.success('Environment deleted')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Delete failed'),
  })

  const setKeyMutation = useMutation({
    mutationFn: ({ id, key }) => workspaceAPI.setEncryptionKey(id, key),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workspaces'] })
      toast.success('Workspace encryption key updated')
      setIsKeyDialogOpen(false)
      setEncryptionKey('')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Failed to set encryption key'),
  })

  return (
    <div className="space-y-6 max-w-6xl">
      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Workspaces</h1>
          <p className="text-muted-foreground mt-1">
            Isolate projects with per-workspace AES encryption keys and environments
          </p>
        </div>
        <Button className="brand-gradient border-0 text-white" onClick={() => setIsCreateDialogOpen(true)}>
          <Plus className="mr-2 h-4 w-4" />
          New workspace
        </Button>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <div className="glass-panel rounded-2xl p-5">
          <h2 className="font-semibold mb-1">Your workspaces</h2>
          <p className="text-sm text-muted-foreground mb-4">Select one to manage environments</p>
          {workspacesLoading ? (
            <div className="space-y-2">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-14 w-full rounded-xl" />
              ))}
            </div>
          ) : workspaces?.length ? (
            <div className="space-y-2">
              {workspaces.map((workspace) => (
                <div
                  key={workspace.id}
                  className={`flex items-center justify-between rounded-xl border p-4 cursor-pointer transition-colors ${
                    selectedWorkspaceId === workspace.id
                      ? 'border-primary/50 bg-primary/5'
                      : 'border-border/70 hover:bg-muted/40'
                  }`}
                  onClick={() => {
                    setSelectedWorkspaceId(workspace.id)
                    localStorage.setItem('activeWorkspaceId', String(workspace.id))
                  }}
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <FolderOpen className="h-5 w-5 text-primary flex-shrink-0" />
                    <div className="min-w-0">
                      <p className="font-medium truncate">{workspace.name}</p>
                      <p className="text-xs text-muted-foreground flex items-center gap-1">
                        {workspace.hasEncryptionKey ? (
                          <>
                            <ShieldCheck className="h-3 w-3 text-emerald-400" /> Encryption key set
                          </>
                        ) : (
                          'No encryption key — set one before storing secrets'
                        )}
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-1" onClick={(e) => e.stopPropagation()}>
                    <Button
                      variant="ghost"
                      size="icon"
                      title="Set encryption key"
                      onClick={() => {
                        setKeyTargetId(workspace.id)
                        setEncryptionKey('')
                        setIsKeyDialogOpen(true)
                      }}
                    >
                      <KeyRound className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => {
                        if (confirm(`Delete workspace "${workspace.name}"?`)) {
                          deleteWorkspaceMutation.mutate(workspace.id)
                        }
                      }}
                    >
                      <Trash2 className="h-4 w-4 text-destructive" />
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-sm text-muted-foreground py-8 text-center">No workspaces yet</p>
          )}
        </div>

        <div className="glass-panel rounded-2xl p-5">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="font-semibold">Environments</h2>
              <p className="text-sm text-muted-foreground">DEVELOPMENT · STAGING · PRODUCTION</p>
            </div>
            <Button
              size="sm"
              disabled={!selectedWorkspaceId}
              onClick={() => setIsEnvDialogOpen(true)}
            >
              <Plus className="mr-1 h-4 w-4" />
              Add
            </Button>
          </div>
          {!selectedWorkspaceId ? (
            <p className="text-sm text-muted-foreground py-10 text-center">Select a workspace</p>
          ) : envsLoading ? (
            <Skeleton className="h-24 w-full rounded-xl" />
          ) : environments?.length ? (
            <div className="space-y-2">
              {environments.map((env) => (
                <div
                  key={env.id}
                  className="flex items-center justify-between rounded-xl border border-border/70 px-4 py-3"
                >
                  <div>
                    <p className="font-medium font-mono text-sm">{env.name}</p>
                    <p className="text-xs text-muted-foreground">{env.isActive ? 'Active' : 'Inactive'}</p>
                  </div>
                  <Button
                    variant="ghost"
                    size="icon"
                    onClick={() => {
                      if (confirm(`Delete environment ${env.name}?`)) deleteEnvMutation.mutate(env.id)
                    }}
                  >
                    <Trash2 className="h-4 w-4 text-destructive" />
                  </Button>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-sm text-muted-foreground py-10 text-center">No environments — create DEVELOPMENT to start</p>
          )}
        </div>
      </div>

      <Dialog open={isCreateDialogOpen} onOpenChange={setIsCreateDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create workspace</DialogTitle>
            <DialogDescription>
              Provide a workspace encryption key (min 16 chars). Store it safely — it encrypts all secrets in this workspace.
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
                name: newWorkspaceName,
                description: newWorkspaceDesc || undefined,
                workspaceEncryptionKey: encryptionKey,
              })
            }}
            className="space-y-4"
          >
            <div className="space-y-2">
              <Label>Name</Label>
              <Input value={newWorkspaceName} onChange={(e) => setNewWorkspaceName(e.target.value)} required minLength={3} />
            </div>
            <div className="space-y-2">
              <Label>Description</Label>
              <Input value={newWorkspaceDesc} onChange={(e) => setNewWorkspaceDesc(e.target.value)} />
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
              <p className="text-[11px] text-muted-foreground">
                Also save this as CRYPTENV_WORKSPACE_ENCRYPTION_KEY for the Node SDK / CLI.
              </p>
            </div>
            <DialogFooter>
              <Button type="submit" disabled={createWorkspaceMutation.isPending} className="brand-gradient border-0 text-white">
                {createWorkspaceMutation.isPending ? 'Creating…' : 'Create'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={isEnvDialogOpen} onOpenChange={setIsEnvDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Add environment</DialogTitle>
          </DialogHeader>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              createEnvMutation.mutate({
                name: newEnvName,
                workspaceId: selectedWorkspaceId,
              })
            }}
            className="space-y-4"
          >
            <div className="space-y-2">
              <Label>Type</Label>
              <Select value={newEnvName} onValueChange={setNewEnvName}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {ENV_TYPES.map((t) => (
                    <SelectItem key={t} value={t}>
                      {t}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <DialogFooter>
              <Button type="submit" disabled={createEnvMutation.isPending}>
                Create environment
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={isKeyDialogOpen} onOpenChange={setIsKeyDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Set workspace encryption key</DialogTitle>
            <DialogDescription>
              Required after master-key rotation. Existing secrets encrypted with a different key will not decrypt until re-saved.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <div className="flex justify-between items-center">
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
              <Button
                className="brand-gradient border-0 text-white"
                disabled={setKeyMutation.isPending}
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
