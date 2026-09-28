import { api } from '@/api/client'
import type { Report } from '@/api/types'

export type ReportKind =
  | 'revenue'
  | 'routes'
  | 'vehicles'
  | 'fuel'
  | 'profitability'
  | 'receivables'
  | 'licenses'
  | 'maintenance'

export const reportsApi = {
  run: (kind: ReportKind, from: string, to: string) =>
    api.get<Report>(`/reports/${kind}`, { query: { from, to } }),
  csv: (kind: ReportKind, from: string, to: string) =>
    api.text(`/reports/${kind}/csv`, { query: { from, to } }),
}
