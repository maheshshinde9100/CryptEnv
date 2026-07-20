import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { secretsAPI } from '../lib/api'
import { toast } from 'sonner'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Button } from '../components/ui/button'
import { Input } from '../components/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table'
import { Skeleton } from '../components/ui/skeleton'
import { Search, Plus, Eye, EyeOff, Trash2, Edit } from 'lucide-react'
import { Pagination } from '../components/ui/pagination'

export function Secrets() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [searchQuery, setSearchQuery] = useState('')
  const [visibleSecrets, setVisibleSecrets] = useState({})
  const [currentPage, setCurrentPage] = useState(1)
  const itemsPerPage = 10

  const { data: secrets, isLoading } = useQuery({
    queryKey: ['secrets'],
    queryFn: () => secretsAPI.list().then((res) => res.data),
  })

  const deleteMutation = useMutation({
    mutationFn: (key) => secretsAPI.delete(key),
    onSuccess: () => {
      queryClient.invalidateQueries(['secrets'])
      toast.success('Secret deleted successfully')
    },
    onError: () => {
      toast.error('Failed to delete secret')
    },
  })

  const filteredSecrets = secrets?.filter((secret) =>
    secret.key.toLowerCase().includes(searchQuery.toLowerCase())
  ) || []

  const totalPages = Math.ceil(filteredSecrets.length / itemsPerPage)
  const paginatedSecrets = filteredSecrets.slice(
    (currentPage - 1) * itemsPerPage,
    currentPage * itemsPerPage
  )

  const toggleVisibility = (key) => {
    setVisibleSecrets((prev) => ({
      ...prev,
      [key]: !prev[key],
    }))
  }

  const handleDelete = (key) => {
    if (confirm('Are you sure you want to delete this secret?')) {
      deleteMutation.mutate(key)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Secrets</h1>
          <p className="text-muted-foreground">Manage your application secrets</p>
        </div>
        <Button onClick={() => navigate('/secrets/new')}>
          <Plus className="mr-2 h-4 w-4" />
          Add Secret
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>All Secrets</CardTitle>
          <CardDescription>View and manage your secrets</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="mb-4">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="Search secrets..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-10"
              />
            </div>
          </div>

          {isLoading ? (
            <div className="space-y-2">
              {[1, 2, 3, 4, 5].map((i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : filteredSecrets.length > 0 ? (
            <>
              <div className="rounded-md border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Key</TableHead>
                      <TableHead>Value</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {paginatedSecrets.map((secret) => (
                      <TableRow key={secret.key}>
                        <TableCell className="font-medium">{secret.key}</TableCell>
                        <TableCell>
                          <div className="flex items-center gap-2">
                            <code className="rounded bg-muted px-2 py-1 text-sm">
                              {visibleSecrets[secret.key] ? secret.value : '•'.repeat(20)}
                            </code>
                            <Button
                              variant="ghost"
                              size="icon"
                              className="h-6 w-6"
                              onClick={() => toggleVisibility(secret.key)}
                            >
                              {visibleSecrets[secret.key] ? (
                                <EyeOff className="h-3 w-3" />
                              ) : (
                                <Eye className="h-3 w-3" />
                              )}
                            </Button>
                          </div>
                        </TableCell>
                        <TableCell className="text-right">
                          <div className="flex justify-end gap-2">
                            <Button
                              variant="ghost"
                              size="icon"
                              onClick={() => navigate(`/secrets/${secret.key}/edit`)}
                            >
                              <Edit className="h-4 w-4" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="icon"
                              onClick={() => handleDelete(secret.key)}
                              disabled={deleteMutation.isPending}
                            >
                              <Trash2 className="h-4 w-4 text-destructive" />
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              {totalPages > 1 && (
                <div className="mt-4">
                  <Pagination
                    currentPage={currentPage}
                    totalPages={totalPages}
                    onPageChange={setCurrentPage}
                  />
                </div>
              )}
            </>
          ) : (
            <div className="py-8 text-center text-muted-foreground">
              {searchQuery ? 'No secrets found matching your search' : 'No secrets yet. Add your first secret!'}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
