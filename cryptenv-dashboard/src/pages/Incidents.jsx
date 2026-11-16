import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { incidentsAPI } from '../lib/api'
import { toast } from 'sonner'
import { AlertTriangle, ShieldAlert } from 'lucide-react'
import { Button } from '../components/ui/button'
import { Input } from '../components/ui/input'
import { Label } from '../components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'

export function Incidents() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState({ title: '', description: '', workspaceId: '', environmentId: '' })
  const { data: incidents = [] } = useQuery({ queryKey: ['incidents'], queryFn: () => incidentsAPI.list().then((res) => res.data) })
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['incidents'] })
  const create = useMutation({ mutationFn: () => incidentsAPI.create({ ...form, workspaceId: form.workspaceId || undefined, environmentId: form.environmentId || undefined }), onSuccess: () => { setForm({ title: '', description: '', workspaceId: '', environmentId: '' }); invalidate(); toast.success('Incident created') }, onError: (e) => toast.error(e.response?.data?.message || 'Could not create incident') })
  const transition = useMutation({ mutationFn: ({ id, status }) => incidentsAPI.status(id, status), onSuccess: () => { invalidate(); toast.success('Incident updated') } })
  const quarantine = useMutation({ mutationFn: (id) => incidentsAPI.quarantine(id), onSuccess: () => { invalidate(); toast.success('Environment quarantined') }, onError: (e) => toast.error(e.response?.data?.message || 'Could not quarantine environment') })
  return <div className="space-y-6">
    <div><h1 className="text-3xl font-bold tracking-tight">Incident center</h1><p className="text-muted-foreground">Track, contain, and resolve security incidents.</p></div>
    <Card><CardHeader><CardTitle>Open incident</CardTitle><CardDescription>Attach an environment to enable controlled quarantine.</CardDescription></CardHeader><CardContent><form className="grid gap-3 md:grid-cols-2" onSubmit={(e) => { e.preventDefault(); create.mutate() }}>
      <div className="md:col-span-2"><Label>Title</Label><Input required value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /></div>
      <div><Label>Workspace ID</Label><Input inputMode="numeric" value={form.workspaceId} onChange={(e) => setForm({ ...form, workspaceId: e.target.value })} /></div>
      <div><Label>Environment ID</Label><Input inputMode="numeric" value={form.environmentId} onChange={(e) => setForm({ ...form, environmentId: e.target.value })} /></div>
      <div className="md:col-span-2"><Label>Description</Label><Input value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></div>
      <Button className="md:col-span-2" disabled={create.isPending}><AlertTriangle className="h-4 w-4 mr-2" />Create incident</Button>
    </form></CardContent></Card>
    <div className="space-y-3">{incidents.length === 0 ? <p className="text-sm text-muted-foreground">No incidents recorded.</p> : incidents.map((incident) => <Card key={incident.id}><CardContent className="pt-5 flex flex-col gap-3"><div className="flex justify-between gap-3"><div><p className="font-semibold">{incident.number} · {incident.title}</p><p className="text-sm text-muted-foreground">{incident.description}</p></div><span className="text-xs font-bold rounded-full bg-muted px-2 py-1 h-fit">{incident.status}</span></div><div className="flex flex-wrap gap-2"><Button size="sm" variant="outline" onClick={() => transition.mutate({ id: incident.id, status: incident.status === 'OPEN' ? 'INVESTIGATING' : 'RESOLVED' })}>{incident.status === 'OPEN' ? 'Investigate' : 'Resolve'}</Button>{incident.environmentId && incident.status !== 'CONTAINED' && <Button size="sm" variant="destructive" onClick={() => quarantine.mutate(incident.id)}><ShieldAlert className="h-3.5 w-3.5 mr-1" />Quarantine environment</Button>}</div></CardContent></Card>)}</div>
  </div>
}
