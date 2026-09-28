import { api } from '@/api/client'
import type { FuelLoad } from '@/api/types'

export const fuelApi = {
  list: () => api.get<FuelLoad[]>('/fuel'),
  create: (load: Partial<FuelLoad>) => api.post<void>('/fuel', load),
  remove: (id: number) => api.delete<void>(`/fuel/${id}`),
}
