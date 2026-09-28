import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { api, publicApi } from '@/api/client'
import type { TokenResponse, UserMe } from '@/api/types'

const REFRESH_KEY = 'sysport.refresh'

export const useAuthStore = defineStore('auth', () => {
  // The access token lives in memory only; the refresh token is persisted so a
  // reload can silently start a new session.
  const accessToken = ref<string | null>(null)
  const refreshToken = ref<string | null>(localStorage.getItem(REFRESH_KEY))
  const user = ref<UserMe | null>(null)

  const permissions = computed(() => new Set(user.value?.permissions ?? []))
  const isAuthenticated = computed(() => accessToken.value !== null && user.value !== null)

  function can(permission: string): boolean {
    return permissions.value.has(permission)
  }

  function storeTokens(tokens: TokenResponse) {
    accessToken.value = tokens.access_token
    if (tokens.refresh_token) {
      refreshToken.value = tokens.refresh_token
      localStorage.setItem(REFRESH_KEY, tokens.refresh_token)
    }
  }

  async function login(username: string, password: string) {
    const tokens = await publicApi<TokenResponse>('/auth/login', {
      method: 'POST',
      body: { username, password },
    })
    storeTokens(tokens)
    await loadMe()
  }

  async function refresh() {
    if (!refreshToken.value) {
      throw new Error('No hay sesion que renovar')
    }
    const tokens = await publicApi<TokenResponse>('/auth/refresh', {
      method: 'POST',
      body: { grant_type: 'refresh_token', refresh_token: refreshToken.value },
    })
    storeTokens(tokens)
  }

  async function loadMe() {
    user.value = await api.get<UserMe>('/auth/me')
  }

  async function changePassword(currentPassword: string, newPassword: string) {
    await api.post<void>('/auth/password', { currentPassword, newPassword })
  }

  /** Restores a session on page load from the stored refresh token. */
  async function bootstrap() {
    if (!refreshToken.value) {
      return
    }
    try {
      await refresh()
      await loadMe()
    } catch {
      clear()
    }
  }

  function clear() {
    accessToken.value = null
    refreshToken.value = null
    user.value = null
    localStorage.removeItem(REFRESH_KEY)
  }

  return {
    accessToken,
    refreshToken,
    user,
    permissions,
    isAuthenticated,
    can,
    login,
    refresh,
    loadMe,
    changePassword,
    bootstrap,
    clear,
  }
})
