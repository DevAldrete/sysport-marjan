import { api } from '@/api/client'
import type { AssignmentOptions, CargoPackage, ServiceRequest, Trip } from '@/api/types'

export interface RequestQuery {
  folio?: string
  clientId?: number
  status?: string
  from?: string
  to?: string
}

export interface PackageLine {
  id: number
  description: string
  quantity: number | null
  unit: string | null
  unitWeight: number | null
}

export interface RequestWrite {
  clientId: number
  routeId: number
  cargoDescription: string
  estimatedWeight: number | null
  pickupScheduled: string | null
  deliveryScheduled: string | null
  agreedRate: number | null
  requiresDocuments: boolean
  notes: string
  packages: PackageLine[]
}

function clean(query: RequestQuery): Record<string, unknown> {
  const result: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== '') {
      result[key] = value
    }
  }
  return result
}

export const requestsApi = {
  search: (query: RequestQuery) => api.get<ServiceRequest[]>('/requests', { query: clean(query) }),
  get: (id: number) => api.get<ServiceRequest>(`/requests/${id}`),
  packages: (id: number) => api.get<CargoPackage[]>(`/requests/${id}/packages`),
  trip: async (id: number): Promise<Trip | null> => {
    try {
      return await api.get<Trip>(`/requests/${id}/trip`)
    } catch (error) {
      if ((error as { response?: { status?: number } }).response?.status === 404) {
        return null
      }
      throw error
    }
  },
  create: (body: RequestWrite) => api.post<ServiceRequest>('/requests', body),
  update: (id: number, body: RequestWrite) => api.put<ServiceRequest>(`/requests/${id}`, body),
  authorize: (id: number, rate: number) => api.post<ServiceRequest>(`/requests/${id}/authorize`, { rate }),
  schedule: (id: number, pickup: string, delivery: string) =>
    api.post<ServiceRequest>(`/requests/${id}/schedule`, { pickup, delivery }),
  cancel: (id: number, reason: string) => api.post<ServiceRequest>(`/requests/${id}/cancel`, { reason }),
  close: (id: number) => api.post<void>(`/requests/${id}/close`),
  remove: (id: number) => api.delete<void>(`/requests/${id}`),
  pendingBilling: () => api.get<ServiceRequest[]>('/requests/pending-billing'),
  assignment: (id: number) => api.get<AssignmentOptions>(`/requests/${id}/assignment`),
  assign: (id: number, vehicleId: number, operatorId: number) =>
    api.post<Trip>(`/requests/${id}/assign`, { vehicleId, operatorId }),
}
