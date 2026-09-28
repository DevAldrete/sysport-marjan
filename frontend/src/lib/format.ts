const money = new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' })

export function formatMoney(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === '') {
    return ''
  }
  const amount = typeof value === 'string' ? Number(value) : value
  return Number.isFinite(amount) ? money.format(amount) : ''
}

/** `2026-01-31` or ISO date-time -> `31/01/2026`. */
export function formatDate(value: string | null | undefined): string {
  if (!value) {
    return ''
  }
  const [year, month, day] = value.slice(0, 10).split('-')
  return year && month && day ? `${day}/${month}/${year}` : value
}

/** ISO date-time -> `31/01/2026 08:30`. */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return ''
  }
  const time = value.slice(11, 16)
  return time ? `${formatDate(value)} ${time}` : formatDate(value)
}
