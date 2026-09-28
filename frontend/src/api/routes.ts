import { api } from '@/api/client'
import type { Route } from '@/api/types'

export const routesApi = {
  search: (term: string) => api.get<Route[]>('/routes', { query: { term } }),
  all: () => api.get<Route[]>('/routes/all'),
  create: (route: Partial<Route>) => api.post<Route>('/routes', route),
  update: (id: number, route: Partial<Route>) => api.put<Route>(`/routes/${id}`, route),
  remove: (id: number) => api.delete<void>(`/routes/${id}`),
}
