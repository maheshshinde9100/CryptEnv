import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { workspaceAPI, environmentAPI } from '../lib/api'
import { toast } from 'sonner'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Button } from '../components/ui/button'
import { Input } from '../components/ui/input'
import { Label } from '../components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog'
import { Skeleton } from '../components/ui/skeleton'
import { Plus, Trash2, FolderOpen } from 'lucide-react'

export function Workspace() {
  const queryClient = useQueryClient()
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)
  const [isEnvDialogOpen, setIsEnvDialogOpen] = useState(false)
  const [newWorkspaceName, setNewWorkspaceName] = useState('')
  const [newEnvName, setNewEnvName] = useState('')
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState(null)

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
    onSuccess: () => {
      queryClient.invalidateQueries(['workspaces'])
      toast.success('Workspace created successfully')
      setIsCreateDialogOpen(false)
      setNewWorkspaceName('')
    },
    onError: () => {
      toast.error('Failed to create workspace')
    },
  })

  const deleteWorkspaceMutation = useMutation({
    mutationFn: (id) => workspaceAPI.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries(['workspaces'])
      toast.success('Workspace deleted successfully')
    },
    onError: () => {
      toast.error('Failed to delete workspace')
    },
  })

  const createEnvMutation = useMutation({
    mutationFn: (data) => environmentAPI.create(selectedWorkspaceId, data),
    onSuccess: () => {
      queryClient.invalidateQueries(['environments', selectedWorkspaceId])
      toast.success('Environment created successfully')
      setIsEnvDialogOpen(false)
      setNewEnvName('')
    },
    onError: () => {
      toast.error('Failed to create environment')
    },
  })

  const deleteEnvMutation = useMutation({
    mutationFn: (id) => environmentAPI.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries(['environments', selectedWorkspaceId])
      toast.success('Environment deleted successfully')
    },
    onError: () => {
      toast.error('Failed to delete environment')
    },
  })

  const handleCreateWorkspace = (e) => {
    e.preventDefault()
    createWorkspaceMutation.mutate({ name: newWorkspaceName })
  }

  const handleCreateEnv = (e) => {
    e.preventDefault()
    createEnvMutation.mutate({ name: newEnvName })
  }

  const handleDeleteWorkspace = (id) => {
    if (confirm('Are you sure you want to delete this workspace?')) {
      deleteWorkspaceMutation.mutate(id)
    }
  }

  const handleDeleteEnv = (id) => {
    if (confirm('Are you sure you want to delete this environment?')) {
      deleteEnvMutation.mutate(id)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Workspace</h1>
          <p className="text-muted-foreground">Manage your workspaces and environments</p>
        </div>
        <Button onClick={() => setIsCreateDialogOpen(true)}>
          <Plus className="mr-2 h-4 w-4" />
          New Workspace
        </Button>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Workspaces</CardTitle>
            <CardDescription>Your workspaces</CardDescription>
          </CardHeader>
          <CardContent>
            {workspacesLoading ? (
              <div className="space-y-2">
                {[1, 2, 3].map((i) => (
                  <Skeleton key={i} className="h-12 w-full" />
                ))}
              </div>
            ) : workspaces && workspaces.length > 0 ? (
              <div className="space-y-2">
                {workspaces.map((workspace) => (
                  <div
                    key={workspace.id}
                    className={`flex items-center justify-between rounded-lg border p-4 cursor-pointer transition-colors ${
                      selectedWorkspaceId === workspace.id ? 'bg-accent' : 'hover:bg-accent/50'
                    }`}
                    onClick={() => setSelectedWorkspaceId(workspace.id)}
                  >
                    <div className="flex items-center gap-3">
                      <FolderOpen className="h-5 w-5 text-muted-foreground" />
                      <div>
                        <p className="font-medium">{workspace.name}</p>
                        <p className="text-sm text-muted-foreground">
                          {workspace.environments?.length || 0} environments
                        </p>
                      </div>
                    </div>
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={(e) => {
                        e.stopPropagation()
                        handleDeleteWorkspace(workspace.id)
                      }}
                    >
                      <Trash2 className="h-4 w-4 text-destructive" />
                    </Button>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">No workspaces yet</p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Environments</CardTitle>
            <CardDescription>
              {selectedWorkspaceId ? 'Environments for selected workspace' : 'Select a workspace to view environments'}
            </CardDescription>
          </CardHeader>
          <CardContent>
            {selectedWorkspaceId ? (
              <>
                <div className="mb-4">
                  <Button
                    onClick={() => setIsEnvDialogOpen(true)}
                    disabled={!selectedWorkspaceId}
                    size="sm"
                  >
                    <Plus className="mr-2 h-4 w-4" />
                    Add Environment
                  </Button>
                </div>
                {envsLoading ? (
                  <div className="space-y-2">
                    {[1, 2, 3].map((i) => (
                      <Skeleton key={i} className="h-10 w-full" />
                    ))}
                  </div>
                ) : environments && environments.length > 0 ? (
                  <div className="space-y-2">
                    {environments.map((env) => (
                      <div
                        key={env.id}
                        className="flex items-center justify-between rounded-lg border p-3"
                      >
                        <span className="font-medium">{env.name}</span>
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => handleDeleteEnv(env.id)}
                        >
                          <Trash2 className="h-4 w-4 text-destructive" />
                        </Button>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-sm text-muted-foreground">No environments yet</p>
                )}
              </>
            ) : (
              <p className="text-sm text-muted-foreground">Select a workspace to view environments</p>
            )}
          </CardContent>
        </Card>
      </div>

      <Dialog open={isCreateDialogOpen} onOpenChange={setIsCreateDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create Workspace</DialogTitle>
            <DialogDescription>Enter a name for your new workspace</DialogDescription>
          </DialogHeader>
          <form onSubmit={handleCreateWorkspace} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="workspaceName">Workspace Name</Label>
              <Input
                id="workspaceName"
                value={newWorkspaceName}
                onChange={(e) => setNewWorkspaceName(e.target.value)}
                required
              />
            </div>
            <DialogFooter>
              <Button type="submit" disabled={createWorkspaceMutation.isPending}>
                {createWorkspaceMutation.isPending ? 'Creating...' : 'Create'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={isEnvDialogOpen} onOpenChange={setIsEnvDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Add Environment</DialogTitle>
            <DialogDescription>Enter a name for the new environment</DialogDescription>
          </DialogHeader>
          <form onSubmit={handleCreateEnv} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="envName">Environment Name</Label>
              <Input
                id="envName"
                value={newEnvName}
                onChange={(e) => setNewEnvName(e.target.value)}
                required
              />
            </div>
            <DialogFooter>
              <Button type="submit" disabled={createEnvMutation.isPending}>
                {createEnvMutation.isPending ? 'Creating...' : 'Create'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
