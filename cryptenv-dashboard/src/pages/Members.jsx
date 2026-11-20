import { useMemo, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useOutletContext } from 'react-router-dom'
import { workspaceAPI } from '../lib/api'
import { toast } from 'sonner'
import { Button } from '../components/ui/button'
import { Input } from '../components/ui/input'
import { Label } from '../components/ui/label'
import { Skeleton } from '../components/ui/skeleton'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../components/ui/select'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog'
import { Plus, Trash2, LogOut, ArrowRightLeft, Crown, Search } from 'lucide-react'

const ASSIGNABLE_ROLES = ['ADMIN', 'DEVELOPER', 'AUDITOR', 'VIEWER']

const roleLabel = (role) => (role ? role.charAt(0) + role.slice(1).toLowerCase() : 'Member')

export function Members() {
  const queryClient = useQueryClient()
  const { activeWorkspace } = useOutletContext() || {}
  const [inviteOpen, setInviteOpen] = useState(false)
  const [transferOpen, setTransferOpen] = useState(false)
  const [email, setEmail] = useState('')
  const [inviteRole, setInviteRole] = useState('DEVELOPER')
  const [newOwnerId, setNewOwnerId] = useState('')
  const [searchQuery, setSearchQuery] = useState('')

  const { data: workspace, isLoading } = useQuery({
    queryKey: ['workspace', activeWorkspace?.id],
    queryFn: () => workspaceAPI.get(activeWorkspace.id).then((res) => res.data),
    enabled: !!activeWorkspace?.id,
  })

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['workspace', activeWorkspace?.id] })
    queryClient.invalidateQueries({ queryKey: ['workspaces'] })
  }

  const inviteMutation = useMutation({
    mutationFn: () => workspaceAPI.inviteMember(activeWorkspace.id, email, inviteRole),
    onSuccess: () => {
      refresh()
      toast.success('Invitation sent')
      setInviteOpen(false)
      setEmail('')
      setInviteRole('DEVELOPER')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Failed to invite member'),
  })

  const roleMutation = useMutation({
    mutationFn: ({ userId, role }) => workspaceAPI.changeMemberRole(activeWorkspace.id, userId, role),
    onSuccess: () => {
      refresh()
      toast.success('Role updated')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Failed to update role'),
  })

  const removeMutation = useMutation({
    mutationFn: (userId) => workspaceAPI.removeMember(activeWorkspace.id, userId),
    onSuccess: () => {
      refresh()
      toast.success('Member removed')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Failed to remove member'),
  })

  const leaveMutation = useMutation({
    mutationFn: () => workspaceAPI.leave(activeWorkspace.id),
    onSuccess: () => {
      localStorage.removeItem('activeWorkspaceId')
      queryClient.invalidateQueries({ queryKey: ['workspaces'] })
      toast.success('You left the workspace')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Failed to leave workspace'),
  })

  const transferMutation = useMutation({
    mutationFn: () => workspaceAPI.transferOwnership(activeWorkspace.id, Number(newOwnerId)),
    onSuccess: () => {
      refresh()
      toast.success('Ownership transferred')
      setTransferOpen(false)
      setNewOwnerId('')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Failed to transfer ownership'),
  })

  const isOwner = workspace?.ownerId && workspace?.currentUserRole === 'OWNER'
  const isAdmin = isOwner || workspace?.currentUserRole === 'ADMIN'

  const members = useMemo(() => workspace?.members || [], [workspace])

  const rows = useMemo(() => {
    const list = members.filter((m) => m.userId !== workspace?.ownerId)
    return list
      .filter(
        (m) =>
          !searchQuery ||
          (m.username || '').toLowerCase().includes(searchQuery.toLowerCase()) ||
          (m.email || '').toLowerCase().includes(searchQuery.toLowerCase())
      )
      .sort((a, b) => (a.username || '').localeCompare(b.username || ''))
  }, [members, workspace?.ownerId, searchQuery])

  return (
    <div className="space-y-8">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="eyebrow">Access</p>
          <h1 className="page-title mt-3">Members</h1>
          <p className="page-subtitle">
            {activeWorkspace?.name || 'Workspace'} · roles decide who can read, write and approve
          </p>
        </div>
        <div className="flex gap-2">
          {isOwner && (
            <Button variant="outline" onClick={() => setTransferOpen(true)} disabled={rows.length === 0}>
              <ArrowRightLeft className="h-4 w-4" />
              Transfer ownership
            </Button>
          )}
          <Button onClick={() => setInviteOpen(true)} disabled={!isAdmin}>
            <Plus className="h-4 w-4" />
            Invite member
          </Button>
        </div>
      </header>

      <section className="rounded-lg border border-border">
        <div className="flex flex-col gap-3 border-b border-border p-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="relative w-full sm:max-w-sm">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Search members"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-10"
            />
          </div>
          <p className="text-xs text-muted-foreground">
            Your role: <span className="text-foreground">{roleLabel(workspace?.currentUserRole)}</span>
          </p>
        </div>

        {isLoading ? (
          <div className="space-y-px p-2">
            {[1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-14 w-full" />
            ))}
          </div>
        ) : (
          <ul className="divide-y divide-border/60">
            <li className="flex items-center justify-between gap-4 px-5 py-4">
              <div className="min-w-0">
                <p className="flex items-center gap-2 truncate text-sm">
                  <Crown className="h-3.5 w-3.5 text-tertiary" />
                  {workspace?.ownerUsername || '—'}
                </p>
                <p className="mt-1 text-xs text-muted-foreground">Owner · full control, can transfer ownership</p>
              </div>
              <span className="chip">Owner</span>
            </li>
            {rows.length === 0 ? (
              <li className="px-5 py-12 text-center text-sm text-muted-foreground">
                {searchQuery ? 'No members match your search.' : 'No additional members yet.'}
              </li>
            ) : (
              rows.map((member) => (
                <li key={member.userId} className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
                  <div className="min-w-0">
                    <p className="truncate text-sm">{member.username}</p>
                    <p className="mt-0.5 truncate text-xs text-muted-foreground">{member.email || '—'}</p>
                  </div>
                  <div className="flex items-center gap-2 self-end sm:self-center">
                    {isAdmin ? (
                      <Select
                        value={member.role}
                        onValueChange={(role) => roleMutation.mutate({ userId: member.userId, role })}
                      >
                        <SelectTrigger className="w-36">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {ASSIGNABLE_ROLES.map((role) => (
                            <SelectItem key={role} value={role}>
                              {roleLabel(role)}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    ) : (
                      <span className="chip">{roleLabel(member.role)}</span>
                    )}
                    {isAdmin && (
                      <Button
                        variant="ghost"
                        size="icon"
                        title="Remove member"
                        onClick={() => {
                          if (confirm(`Remove ${member.username} from this workspace?`)) {
                            removeMutation.mutate(member.userId)
                          }
                        }}
                      >
                        <Trash2 className="h-4 w-4 text-danger" />
                      </Button>
                    )}
                  </div>
                </li>
              ))
            )}
          </ul>
        )}
      </section>

      {!isOwner && workspace && (
        <div className="flex items-center justify-between rounded-lg border border-border p-4">
          <div>
            <p className="text-sm">Leave workspace</p>
            <p className="text-xs text-muted-foreground">You will lose access to its secrets and audit history.</p>
          </div>
          <Button
            variant="outline"
            onClick={() => {
              if (confirm('Leave this workspace?')) leaveMutation.mutate()
            }}
          >
            <LogOut className="h-4 w-4" />
            Leave
          </Button>
        </div>
      )}

      <Dialog open={inviteOpen} onOpenChange={setInviteOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Invite member</DialogTitle>
            <DialogDescription>Add an existing CryptEnv user to this workspace by email.</DialogDescription>
          </DialogHeader>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              inviteMutation.mutate()
            }}
            className="space-y-4"
          >
            <div className="space-y-2">
              <Label htmlFor="member-email">Email</Label>
              <Input
                id="member-email"
                type="email"
                placeholder="colleague@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>
            <div className="space-y-2">
              <Label>Role</Label>
              <Select value={inviteRole} onValueChange={setInviteRole}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {ASSIGNABLE_ROLES.map((role) => (
                    <SelectItem key={role} value={role}>
                      {roleLabel(role)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground">
                Viewers and auditors are read-only. Owners can transfer ownership after joining.
              </p>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setInviteOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={inviteMutation.isPending}>
                {inviteMutation.isPending ? 'Inviting…' : 'Send invitation'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={transferOpen} onOpenChange={setTransferOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Transfer ownership</DialogTitle>
            <DialogDescription>
              The new owner gets full control. You remain a member with an admin role afterwards.
            </DialogDescription>
          </DialogHeader>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              if (!newOwnerId) return
              transferMutation.mutate()
            }}
            className="space-y-4"
          >
            <div className="space-y-2">
              <Label>New owner</Label>
              <Select value={newOwnerId} onValueChange={setNewOwnerId}>
                <SelectTrigger>
                  <SelectValue placeholder="Select member" />
                </SelectTrigger>
                <SelectContent>
                  {rows.map((m) => (
                    <SelectItem key={m.userId} value={String(m.userId)}>
                      {m.username}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setTransferOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={!newOwnerId || transferMutation.isPending}>
                Transfer
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
