export interface UserMe {
  userId: number
  username: string
  roleName: string
  permissions: string[]
}

export interface DashboardAlerts {
  expiringLicenses: number
  overdueInvoices: number
  maintenanceDue: number
  pendingAssignments: number
}

export interface TokenResponse {
  access_token: string
  refresh_token?: string
  token_type: string
  expires_in: number
}
