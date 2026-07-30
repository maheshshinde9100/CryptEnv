import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Button } from '../components/ui/button'
import { ClipboardCheck, ShieldAlert, UserCheck } from 'lucide-react'

export function AccessReviews() {
  const reviews = [
    { id: 1, team: 'Engineering', reviewer: 'admin', dueDate: 'Tomorrow', status: 'pending', items: 24 },
    { id: 2, team: 'Marketing', reviewer: 'manager', dueDate: 'Next Week', status: 'pending', items: 8 },
    { id: 3, team: 'DevOps', reviewer: 'security', dueDate: 'Completed', status: 'completed', items: 12 },
  ]

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Access Reviews</h1>
        <p className="text-muted-foreground">Periodically review user access to maintain least privilege</p>
      </div>

      <div className="grid gap-4 md:grid-cols-3 mb-6">
        <Card className="border-blue-500/30">
          <CardContent className="pt-6 flex items-center gap-4">
            <div className="p-3 rounded-full bg-blue-500/10">
              <ClipboardCheck className="h-5 w-5 text-blue-500" />
            </div>
            <div>
              <p className="text-sm text-muted-foreground">Active Reviews</p>
              <p className="text-2xl font-bold">2</p>
            </div>
          </CardContent>
        </Card>
        <Card className="border-orange-500/30">
          <CardContent className="pt-6 flex items-center gap-4">
            <div className="p-3 rounded-full bg-orange-500/10">
              <ShieldAlert className="h-5 w-5 text-orange-500" />
            </div>
            <div>
              <p className="text-sm text-muted-foreground">High Risk Access</p>
              <p className="text-2xl font-bold">5</p>
            </div>
          </CardContent>
        </Card>
        <Card className="border-green-500/30">
          <CardContent className="pt-6 flex items-center gap-4">
            <div className="p-3 rounded-full bg-green-500/10">
              <UserCheck className="h-5 w-5 text-green-500" />
            </div>
            <div>
              <p className="text-sm text-muted-foreground">Completed (30d)</p>
              <p className="text-2xl font-bold">1</p>
            </div>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Campaigns</CardTitle>
          <CardDescription>Scheduled and ongoing access review campaigns</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            {reviews.map(review => (
              <div key={review.id} className="flex flex-col sm:flex-row sm:items-center justify-between p-4 border rounded-lg hover:bg-accent/30 transition-colors gap-4">
                <div>
                  <h3 className="font-semibold">{review.team} Q3 Access Review</h3>
                  <div className="flex gap-4 mt-2 text-sm text-muted-foreground">
                    <span>Reviewer: <span className="font-medium text-foreground">{review.reviewer}</span></span>
                    <span>Due: <span className="font-medium text-foreground">{review.dueDate}</span></span>
                    <span>Items: <span className="font-medium text-foreground">{review.items}</span></span>
                  </div>
                </div>
                {review.status === 'pending' ? (
                  <Button>Start Review</Button>
                ) : (
                  <span className="text-sm font-semibold px-3 py-1 rounded-full bg-green-500/10 text-green-600 self-start sm:self-auto">
                    Completed
                  </span>
                )}
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
