import { api } from '@/api/client'
import type { Invoice, Payment } from '@/api/types'

export const invoicesApi = {
  search: (status: string, clientId: string) =>
    api.get<Invoice[]>('/invoices', { query: { status, clientId } }),
  get: (id: number) => api.get<Invoice>(`/invoices/${id}`),
  payments: (id: number) => api.get<Payment[]>(`/invoices/${id}/payments`),
  create: (requestId: number, issueDate: string, amount: number | null) =>
    api.post<Invoice>('/invoices', { requestId, issueDate, amount }),
  registerPayment: (id: number, amount: number, date: string, method: string) =>
    api.post<void>(`/invoices/${id}/payments`, { amount, date, method }),
  deletePayment: (id: number) => api.delete<void>(`/invoices/payments/${id}`),
  remove: (id: number) => api.delete<void>(`/invoices/${id}`),
  cancel: (id: number) => api.post<void>(`/invoices/${id}/cancel`),
  refreshStatuses: () => api.post<{ updated: number }>('/invoices/refresh-statuses'),
}
