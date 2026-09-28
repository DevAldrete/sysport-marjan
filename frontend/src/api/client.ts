import { ofetch } from 'ofetch'

import { useAuthStore } from '@/stores/auth'

export const apiBase = '/api'

/** Unauthenticated fetcher: login and refresh must not carry or renew a token. */
export const publicApi = ofetch.create({ baseURL: apiBase })

let unauthorizedHandler: (() => void) | null = null

/** Called once the session cannot be renewed, so the app can return to login. */
export function onUnauthorized(handler: () => void) {
  unauthorizedHandler = handler
}

export interface RequestOptions {
  headers?: HeadersInit
  query?: Record<string, unknown>
  responseType?: 'json' | 'text' | 'blob'
}

async function request<T>(
  url: string,
  method: string,
  body?: unknown,
  options: RequestOptions = {},
): Promise<T> {
  const auth = useAuthStore()

  const send = () => {
    const headers = new Headers(options.headers ?? {})
    if (auth.accessToken) {
      headers.set('Authorization', `Bearer ${auth.accessToken}`)
    }
    return ofetch<T>(url, {
      baseURL: apiBase,
      method,
      body: body as Record<string, unknown> | undefined,
      headers,
      query: options.query,
      responseType: (options.responseType ?? 'json') as 'json',
    })
  }

  try {
    return await send()
  } catch (error) {
    const status = (error as { response?: { status?: number } })?.response?.status
    if (status === 401 && auth.refreshToken) {
      try {
        await auth.refresh()
        return await send()
      } catch {
        auth.clear()
        unauthorizedHandler?.()
      }
    }
    throw error
  }
}

export const api = {
  get: <T>(url: string, options?: RequestOptions) => request<T>(url, 'GET', undefined, options),
  post: <T>(url: string, body?: unknown, options?: RequestOptions) =>
    request<T>(url, 'POST', body, options),
  put: <T>(url: string, body?: unknown, options?: RequestOptions) =>
    request<T>(url, 'PUT', body, options),
  delete: <T>(url: string, options?: RequestOptions) => request<T>(url, 'DELETE', undefined, options),
  text: (url: string, options?: RequestOptions) =>
    request<string>(url, 'GET', undefined, { ...options, responseType: 'text' }),
}
