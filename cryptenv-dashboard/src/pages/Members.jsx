import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { workspaceAPI, memberAPI } from '../lib/api'
import { toast } from 'sonner'
import { Button } from '../components/ui/button'
import { Input } from '../components/ui/input'
import { Label } from '../components/ui/label'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog'
import { Skeleton } from '../components/ui/skeleton'
import { Search, Plus, Shield, User, Users } from 'lucide-react'

export function Members() {
  const queryClient = useQueryClient()
  const [isInviteDialogOpen, setIsInviteDialogOpen] = useState(false)
  const [inviteEmail, setInviteEmail] = useState('')
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState(null)

  const { data: workspaces, isLoading: workspacesLoading } = useQuery({
    queryKey: ['workspaces'],
    queryFn: () => workspaceAPI.list().then((res) => res.data),
  })

  const { data: selectedWorkspace } = useQuery({
    queryKey: ['workspace', selectedWorkspaceId],
    queryFn: () => workspaceAPI.get(selectedWorkspaceId).then((res) => res.data),
    enabled: !!selectedWorkspaceId,
  })

  const inviteMutation = useMutation({
    mutationFn: (email) => memberAPI.invite(selectedWorkspaceId, email),
    onSuccess: () => {
      queryClient.invalidateQueries(['workspace', selectedWorkspaceId])
      toast.success('Member invited successfully')
      setIsInviteDialogOpen(false)
      setInviteEmail('')
    },
    onError: (error) => {
      toast.error(error.response?.data?.message || 'Failed to invite member')
    },
  })

  const handleInvite = (e) => {
    e.preventDefault()
    inviteMutation.mutate(inviteEmail)
  }

  const members =
    selectedWorkspace?.memberUsernames?.map((username, index) => ({
      id: index,
      name: username,
      role: username === selectedWorkspace.ownerUsername ? 'Owner' : 'Member',
    })) || []

  const filteredMembers = members.filter((member) =>
    member.name.toLowerCase().includes(searchQuery.toLowerCase())
  )

  const roleClass = (role) =>
    role === 'Owner'
      ? 'bg-primary/15 text-primary border-primary/30'
      : 'bg-muted text-muted-foreground border-border'

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Members</h1>
          <p className="text-muted-foreground">Invite collaborators to a workspace</p>
        </div>
        <Button
          onClick={() => setIsInviteDialogOpen(true)}
          disabled={!selectedWorkspaceId}
          className="brand-gradient text-white border-0"
        >
          <Plus className="mr-2 h-4 w-4" />
          Invite Member
        </Button>
      </div>

      <section className="glass-panel rounded-2xl p-5 sm:p-6 space-y-4">
        <div className="flex items-center gap-2">
          <Users className="h-5 w-5 text-primary" />
          <div>
            <h2 className="font-semibold">Team</h2>
            <p className="text-sm text-muted-foreground">Owner and members for the selected workspace</p>
          </div>
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <div>
            <label className="text-sm font-medium mb-1.5 block">Workspace</label>
            {workspacesLoading ? (
              <Skeleton className="h-10 w-full" />
            ) : (
              <select
                value={selectedWorkspaceId || ''}
                onChange={(e) => setSelectedWorkspaceId(Number(e.target.value) || null)}
                className="flex h-10 w-full rounded-lg border border-border/70 bg-background/60 px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-primary/40"
              >
                <option value="">Select a workspace</option>
                {workspaces?.map((ws) => (
                  <option key={ws.id} value={ws.id}>
                    {ws.name}
                  </option>
                ))}
              </select>
            )}
          </div>
          <div>
            <label className="text-sm font-medium mb-1.5 block">Search</label>
            <div className="relative">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="Filter by username…"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-10 bg-background/60"
                disabled={!selectedWorkspaceId}
              />
            </div>
          </div>
        </div>

        {!selectedWorkspaceId ? (
          <p className="text-sm text-muted-foreground py-10 text-center">Select a workspace to view members</p>
        ) : filteredMembers.length === 0 ? (
          <p className="text-sm text-muted-foreground py-10 text-center">No members found</p>
        ) : (
          <ul className="divide-y divide-border/60 rounded-xl border border-border/60 overflow-hidden">
            {filteredMembers.map((member) => (
              <li key={member.id} className="flex items-center justify-between gap-3 px-4 py-3 bg-card/30 hover:bg-muted/30">
                <div className="flex items-center gap-3 min-w-0">
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary/15 text-primary">
                    <User className="h-5 w-5" />
                  </div>
                  <p className="font-medium truncate">{member.name}</p>
                </div>
                <span
                  className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium border ${roleClass(member.role)}`}
                >
                  <Shield className="h-3 w-3" />
                  {member.role}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <Dialog open={isInviteDialogOpen} onOpenChange={setIsInviteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Invite team member</DialogTitle>
            <DialogDescription>Invite by the user’s registered email address</DialogDescription>
          </DialogHeader>
          <form onSubmit={handleInvite} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                placeholder="colleague@example.com"
                value={inviteEmail}
                onChange={(e) => setInviteEmail(e.target.value)}
                required
              />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setIsInviteDialogOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={inviteMutation.isPending} className="brand-gradient text-white border-0">
                {inviteMutation.isPending ? 'Sending…' : 'Send invitation'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
