import { api } from '@/api/client'
import type { Maintenance, Vehicle } from '@/api/types'

export const vehiclesApi = {
  search: (term: string) => api.get<Vehicle[]>('/vehicles', { query: { term } }),
  create: (vehicle: Partial<Vehicle>) => api.post<Vehicle>('/vehicles', vehicle),
  update: (id: number, vehicle: Partial<Vehicle>) => api.put<Vehicle>(`/vehicles/${id}`, vehicle),
  setStatus: (id: number, status: string) => api.post<void>(`/vehicles/${id}/status`, { status }),
  remove: (id: number) => api.delete<void>(`/vehicles/${id}`),
  maintenance: (id: number) => api.get<Maintenance[]>(`/vehicles/${id}/maintenance`),
  addMaintenance: (id: number, record: Partial<Maintenance>) =>
    api.post<void>(`/vehicles/${id}/maintenance`, record),
  removeMaintenance: (maintenanceId: number) =>
    api.delete<void>(`/vehicles/maintenance/${maintenanceId}`),
}
