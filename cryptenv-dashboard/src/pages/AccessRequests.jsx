import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Button } from '../components/ui/button'
import { KeyRound, CheckCircle2, XCircle, Clock } from 'lucide-react'

export function AccessRequests() {
  const requests = [
    { id: 1, user: 'alice@example.com', resource: 'Production Database', environment: 'Production', time: '10 mins ago', status: 'pending' },
    { id: 2, user: 'bob@example.com', resource: 'Staging API Keys', environment: 'Staging', time: '2 hours ago', status: 'approved' },
    { id: 3, user: 'charlie@example.com', resource: 'AWS Credentials', environment: 'Production', time: '1 day ago', status: 'rejected' },
  ]

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Access Requests</h1>
        <p className="text-muted-foreground">Review and manage requests for secret access</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <KeyRound className="h-5 w-5 text-primary" />
            Pending Requests
          </CardTitle>
          <CardDescription>Approve or deny requests to access sensitive environments</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            {requests.map(req => (
              <div key={req.id} className="flex items-center justify-between p-4 border rounded-lg hover:bg-accent/30 transition-colors">
                <div>
                  <p className="font-semibold text-sm">{req.user}</p>
                  <p className="text-xs text-muted-foreground mt-1">Requested access to <span className="font-medium text-foreground">{req.resource}</span> in {req.environment}</p>
                  <p className="text-xs text-muted-foreground mt-1 flex items-center gap-1"><Clock className="h-3 w-3" /> {req.time}</p>
                </div>
                {req.status === 'pending' ? (
                  <div className="flex gap-2">
                    <Button size="sm" variant="outline" className="text-red-500 hover:text-red-600 hover:bg-red-50">Deny</Button>
                    <Button size="sm" className="bg-green-500 hover:bg-green-600 text-white">Approve</Button>
                  </div>
                ) : (
                  <span className={`text-xs font-bold px-3 py-1 rounded-full flex items-center gap-1 ${req.status === 'approved' ? 'bg-green-500/10 text-green-600' : 'bg-red-500/10 text-red-600'}`}>
                    {req.status === 'approved' ? <CheckCircle2 className="h-3 w-3" /> : <XCircle className="h-3 w-3" />}
                    {req.status.charAt(0).toUpperCase() + req.status.slice(1)}
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
