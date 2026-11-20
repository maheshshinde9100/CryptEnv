import { useMemo, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useOutletContext } from 'react-router-dom'
import { accessReviewsAPI } from '../lib/api'
import { toast } from 'sonner'
import { Button } from '../components/ui/button'
import { Input } from '../components/ui/input'
import { Label } from '../components/ui/label'
import { Skeleton } from '../components/ui/skeleton'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog'
import { ClipboardCheck, Plus } from 'lucide-react'

const statusLabel = (status) => (status ? status.charAt(0).toUpperCase() + status.slice(1) : 'Pending')

export function AccessReviews() {
  const queryClient = useQueryClient()
  const { activeWorkspace } = useOutletContext() || {}
  const workspaceId = activeWorkspace?.id
  const [createOpen, setCreateOpen] = useState(false)
  const [name, setName] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [activeReview, setActiveReview] = useState(null)

  const { data: reviews = [], isLoading } = useQuery({
    queryKey: ['access-reviews', workspaceId],
    queryFn: () => accessReviewsAPI.list(workspaceId).then((res) => res.data),
    enabled: !!workspaceId,
  })

  const { data: items = [], isLoading: itemsLoading } = useQuery({
    queryKey: ['access-review-items', activeReview?.id],
    queryFn: () => accessReviewsAPI.items(activeReview.id).then((res) => res.data),
    enabled: !!activeReview?.id,
  })

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['access-reviews', workspaceId] })
    queryClient.invalidateQueries({ queryKey: ['access-review-items', activeReview?.id] })
  }

  const createMutation = useMutation({
    mutationFn: () =>
      accessReviewsAPI.create({
        workspaceId,
        name: name || undefined,
        dueDate: dueDate ? `${dueDate}T00:00:00` : undefined,
      }),
    onSuccess: () => {
      refresh()
      toast.success('Access review created')
      setCreateOpen(false)
      setName('')
      setDueDate('')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Could not create review'),
  })

  const decisionMutation = useMutation({
    mutationFn: ({ itemId, status }) => accessReviewsAPI.reviewItem(itemId, status, undefined),
    onSuccess: () => {
      refresh()
      toast.success('Decision recorded')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Could not record decision'),
  })

  const completeMutation = useMutation({
    mutationFn: (id) => accessReviewsAPI.complete(id),
    onSuccess: () => {
      refresh()
      setActiveReview(null)
      toast.success('Review completed')
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Could not complete review'),
  })

  const counts = useMemo(() => {
    const open = reviews.filter((r) => r.status !== 'completed').length
    const completed = reviews.filter((r) => r.status === 'completed').length
    return { open, completed }
  }, [reviews])

  return (
    <div className="space-y-8">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="eyebrow">Governance</p>
          <h1 className="page-title mt-3">Access reviews</h1>
          <p className="page-subtitle">
            {activeWorkspace?.name || 'Workspace'} · certify member access and revoke what is no longer needed
          </p>
        </div>
        <Button onClick={() => setCreateOpen(true)} disabled={!workspaceId}>
          <Plus className="h-4 w-4" />
          New review
        </Button>
      </header>

      <section className="grid divide-y divide-border rounded-lg border border-border sm:grid-cols-2 sm:divide-y-0 sm:divide-x">
        <div className="p-5">
          <p className="text-xs text-muted-foreground">Open reviews</p>
          <p className="mt-3 text-2xl font-light tracking-[-0.02em]">{counts.open}</p>
        </div>
        <div className="p-5">
          <p className="text-xs text-muted-foreground">Completed</p>
          <p className="mt-3 text-2xl font-light tracking-[-0.02em]">{counts.completed}</p>
        </div>
      </section>

      <section className="rounded-lg border border-border">
        <div className="flex items-center gap-2 border-b border-border px-5 py-4">
          <ClipboardCheck className="h-4 w-4 text-muted-foreground" />
          <div>
            <h2 className="text-base">Campaigns</h2>
            <p className="text-xs text-muted-foreground">Owner and member access snapshots with review decisions</p>
          </div>
        </div>
        {isLoading ? (
          <div className="space-y-px p-2">
            {[1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-16 w-full" />
            ))}
          </div>
        ) : reviews.length === 0 ? (
          <p className="px-5 py-16 text-center text-sm text-muted-foreground">
            No reviews yet. Create one to snapshot who has access.
          </p>
        ) : (
          <ul className="divide-y divide-border/60">
            {reviews.map((review) => (
              <li key={review.id} className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="truncate text-sm">{review.name || `Access review #${review.id}`}</p>
                    <span className="chip">{statusLabel(review.status)}</span>
                  </div>
                  <p className="mt-1 text-xs text-muted-foreground">
                    Created {review.createdAt ? new Date(review.createdAt).toLocaleDateString() : '—'}
                    {review.dueDate ? ` · due ${new Date(review.dueDate).toLocaleDateString()}` : ''}
                  </p>
                </div>
                <Button size="sm" variant="outline" onClick={() => setActiveReview(review)}>
                  {review.status === 'completed' ? 'View' : 'Review'}
                </Button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <Dialog open={!!activeReview} onOpenChange={(open) => !open && setActiveReview(null)}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{activeReview?.name || 'Access review'}</DialogTitle>
            <DialogDescription>
              Approve to keep access, revoke to remove it after the campaign, or escalate for follow-up.
            </DialogDescription>
          </DialogHeader>
          <div className="max-h-96 space-y-2 overflow-y-auto">
            {itemsLoading ? (
              <Skeleton className="h-24 w-full" />
            ) : items.length === 0 ? (
              <p className="py-8 text-center text-sm text-muted-foreground">No items in this review.</p>
            ) : (
              items.map((item) => (
                <div key={item.id} className="rounded-md border border-border p-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div>
                      <p className="text-sm">User #{item.userId}</p>
                      <p className="text-xs text-muted-foreground">
                        Role {item.role || '—'} · {statusLabel(item.status)}
                        {item.comment ? ` · ${item.comment}` : ''}
                      </p>
                    </div>
                    {activeReview?.status !== 'completed' && item.status === 'pending' && (
                      <div className="flex gap-1.5">
                        <Button
                          size="sm"
                          onClick={() => decisionMutation.mutate({ itemId: item.id, status: 'approved' })}
                        >
                          Approve
                        </Button>
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => decisionMutation.mutate({ itemId: item.id, status: 'revoked' })}
                        >
                          Revoke
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => decisionMutation.mutate({ itemId: item.id, status: 'escalated' })}
                        >
                          Escalate
                        </Button>
                      </div>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setActiveReview(null)}>
              Close
            </Button>
            {activeReview?.status !== 'completed' && (
              <Button
                onClick={() => completeMutation.mutate(activeReview.id)}
                disabled={completeMutation.isPending}
              >
                {completeMutation.isPending ? 'Completing…' : 'Complete review'}
              </Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>New access review</DialogTitle>
            <DialogDescription>
              Snapshots the owner and all current members. Review period defaults to 90 days.
            </DialogDescription>
          </DialogHeader>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              createMutation.mutate()
            }}
            className="space-y-4"
          >
            <div className="space-y-2">
              <Label htmlFor="review-name">Name</Label>
              <Input
                id="review-name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Q4 access certification"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="review-due">Due date</Label>
              <Input id="review-due" type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setCreateOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={createMutation.isPending}>
                {createMutation.isPending ? 'Creating…' : 'Create review'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
