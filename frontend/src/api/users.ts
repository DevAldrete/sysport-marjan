import { api } from '@/api/client'
import type { Role, UserAccount } from '@/api/types'

export interface UserWrite {
  username: string
  password?: string
  roleId: number
  employeeId: number | null
  status: string
}

export const usersApi = {
  list: () => api.get<UserAccount[]>('/users'),
  roles: () => api.get<Role[]>('/users/roles'),
  create: (body: UserWrite) => api.post<void>('/users', body),
  update: (id: number, body: UserWrite) => api.put<void>(`/users/${id}`, body),
  resetPassword: (id: number, password: string) => api.post<void>(`/users/${id}/password`, { password }),
  remove: (id: number) => api.delete<void>(`/users/${id}`),
}
