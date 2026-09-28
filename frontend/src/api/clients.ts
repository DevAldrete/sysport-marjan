import { api } from '@/api/client'
import type { Client, ClientRate } from '@/api/types'

export const clientsApi = {
  search: (term: string) => api.get<Client[]>('/clients', { query: { term } }),
  active: () => api.get<Client[]>('/clients/active'),
  get: (id: number) => api.get<Client>(`/clients/${id}`),
  create: (client: Partial<Client>) => api.post<Client>('/clients', client),
  update: (id: number, client: Partial<Client>) => api.put<Client>(`/clients/${id}`, client),
  setStatus: (id: number, status: string) => api.post<void>(`/clients/${id}/status`, { status }),
  remove: (id: number) => api.delete<void>(`/clients/${id}`),
  rates: (id: number) => api.get<ClientRate[]>(`/clients/${id}/rates`),
  saveRate: (id: number, rate: Partial<ClientRate>) => api.post<ClientRate>(`/clients/${id}/rates`, rate),
  removeRate: (rateId: number) => api.delete<void>(`/clients/rates/${rateId}`),
  suggestRate: (clientId: number, routeId: number, date: string) =>
    api.get<number>('/clients/suggest-rate', { query: { clientId, routeId, date } }),
}
