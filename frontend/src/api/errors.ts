import type { FetchError } from 'ofetch'

/**
 * Turns an API failure into the list of human problems the backend returns
 * (422 `{problems:[...]}`, 401 `{message}`), so components show one message.
 */
export function problemsOf(error: unknown): string[] {
  const data = (error as FetchError)?.data as
    | { problems?: unknown; message?: unknown }
    | undefined
  if (data && Array.isArray(data.problems)) {
    return data.problems.map(String)
  }
  if (data && typeof data.message === 'string') {
    return [data.message]
  }
  return ['Ocurrio un error inesperado. Intente de nuevo.']
}
