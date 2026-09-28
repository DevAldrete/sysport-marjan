import { api } from '@/api/client'
import type { Employee } from '@/api/types'

export const operatorsApi = {
  search: (term: string) => api.get<Employee[]>('/operators', { query: { term } }),
  get: (id: number) => api.get<Employee>(`/operators/${id}`),
  create: (employee: Partial<Employee>) => api.post<Employee>('/operators', employee),
  update: (id: number, employee: Partial<Employee>) => api.put<Employee>(`/operators/${id}`, employee),
  setStatus: (id: number, status: string) => api.post<void>(`/operators/${id}/status`, { status }),
  remove: (id: number) => api.delete<void>(`/operators/${id}`),
}
