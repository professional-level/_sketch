export function formatCurrency(value?: number): string {
  if (!Number.isFinite(value)) return '-'
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 2,
  }).format(value ?? 0)
}

export function formatNumber(value?: number, maximumFractionDigits = 2): string {
  if (!Number.isFinite(value)) return '-'
  return new Intl.NumberFormat('ko-KR', {
    maximumFractionDigits,
  }).format(value ?? 0)
}

export function formatPercent(value?: number): string {
  if (!Number.isFinite(value)) return '-'
  return `${formatNumber(value, 4)}%`
}

export function formatSignedCurrency(value?: number): string {
  if (!Number.isFinite(value)) return '-'
  const formatted = formatCurrency(Math.abs(value ?? 0))
  return `${(value ?? 0) >= 0 ? '+' : '-'}${formatted}`
}

export function formatDateTime(value?: string): string {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

export function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10)
}
