import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { leaksAPI } from '../lib/api'
import { Button } from '../components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'

export function Leaks() {
  const queryClient = useQueryClient()
  const { data: findings = [] } = useQuery({ queryKey: ['leaks'], queryFn: () => leaksAPI.list().then((res) => res.data) })
  const update = useMutation({ mutationFn: ({ id, status }) => leaksAPI.update(id, status), onSuccess: () => queryClient.invalidateQueries({ queryKey: ['leaks'] }) })
  return <div className="space-y-6"><div><h1 className="text-3xl font-bold tracking-tight">Leak findings</h1><p className="text-muted-foreground">Metadata-only findings. Potential secret values are never shown or stored.</p></div><Card><CardHeader><CardTitle>Detected findings</CardTitle><CardDescription>Use the CLI command <code>cryptenv scan &lt;path&gt;</code> to scan local source code.</CardDescription></CardHeader><CardContent className="space-y-3">{findings.length === 0 ? <p className="text-sm text-muted-foreground">No leak findings yet.</p> : findings.map((item) => <div key={item.id} className="border rounded-lg p-4 flex flex-col gap-3 sm:flex-row sm:justify-between"><div><p className="font-semibold text-sm">{item.ruleName}</p><p className="text-xs text-muted-foreground">{item.source}:{item.lineNumber} · SHA-256:{item.fingerprint} · {item.status}</p></div>{item.status === 'OPEN' && <div className="flex gap-2"><Button size="sm" variant="outline" onClick={() => update.mutate({ id: item.id, status: 'IGNORED' })}>Ignore</Button><Button size="sm" onClick={() => update.mutate({ id: item.id, status: 'ROTATION_SUGGESTED' })}>Suggest rotation</Button></div>}</div>)}</CardContent></Card></div>
}
