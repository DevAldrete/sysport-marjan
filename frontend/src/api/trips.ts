import { api } from '@/api/client'
import type { Advance, AdvanceBalance, Delivery, Expense, FuelLoad, Incident, Trip } from '@/api/types'

export interface ExpenseWrite {
  tripId: number
  type: string
  amount: number
  expenseDate: string
  description: string
}

export interface AdvanceWrite {
  tripId: number
  employeeId: number
  amountGiven: number
  deliveredDate: string
}

export interface IncidentWrite {
  tripId: number
  incidentDate: string
  incidentTime: string | null
  location: string
  type: string
  description: string
  actionsTaken: string
}

export interface DeliveryWrite {
  tripId: number
  actualDatetime: string
  receivedBy: string
  evidenceReference: string
  status: string
}

export const tripsApi = {
  search: (term: string, status: string) => api.get<Trip[]>('/trips', { query: { term, status } }),
  get: (id: number) => api.get<Trip>(`/trips/${id}`),
  depart: (id: number) => api.post<Trip>(`/trips/${id}/depart`),
  arrive: (id: number, actualKm: number) => api.post<Trip>(`/trips/${id}/arrive`, { actualKm }),
  reassign: (id: number, vehicleId: number, operatorId: number) =>
    api.post<Trip>(`/trips/${id}/reassign`, { vehicleId, operatorId }),
  cancel: (id: number, reason: string) => api.post<Trip>(`/trips/${id}/cancel`, { reason }),
  remove: (id: number) => api.delete<void>(`/trips/${id}`),
}

export const expensesApi = {
  list: (tripId: number) => api.get<Expense[]>('/expenses', { query: { tripId } }),
  create: (body: ExpenseWrite) => api.post<void>('/expenses', body),
  remove: (id: number) => api.delete<void>(`/expenses/${id}`),
}

export const advancesApi = {
  list: (tripId: number) => api.get<Advance[]>('/advances', { query: { tripId } }),
  balance: (tripId: number) => api.get<AdvanceBalance>('/advances/balance', { query: { tripId } }),
  create: (body: AdvanceWrite) => api.post<void>('/advances', body),
  settle: (id: number) => api.post<void>(`/advances/${id}/settle`),
  remove: (id: number) => api.delete<void>(`/advances/${id}`),
}

export const incidentsApi = {
  list: (tripId: number) => api.get<Incident[]>('/incidents', { query: { tripId } }),
  create: (body: IncidentWrite) => api.post<void>('/incidents', body),
  remove: (id: number) => api.delete<void>(`/incidents/${id}`),
}

export const deliveriesApi = {
  byTrip: async (tripId: number): Promise<Delivery | null> => {
    try {
      return await api.get<Delivery>('/deliveries', { query: { tripId } })
    } catch (error) {
      if ((error as { response?: { status?: number } }).response?.status === 404) {
        return null
      }
      throw error
    }
  },
  save: (body: DeliveryWrite) => api.post<void>('/deliveries', body),
  remove: (id: number) => api.delete<void>(`/deliveries/${id}`),
}

export const tripFuelApi = {
  list: (tripId: number) => api.get<FuelLoad[]>(`/fuel/trip/${tripId}`),
}
