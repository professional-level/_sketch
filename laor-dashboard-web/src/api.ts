export type StrategySymbol = 'TQQQ' | 'SOXL'
export type SplitCount = 20 | 30 | 40

export interface CreatePortfolioRequest {
  name?: string
  symbol: StrategySymbol
  market: string
  startDate: string
  initialCash: number
  totalSplitCount: SplitCount
  firstBuyLimitPercentAbovePreviousClose: number
  autoRestart: boolean
  dividendReinvestment: boolean
  autoAdjust: boolean
  commissionRate: number
  slippageRate: number
}

export interface Portfolio {
  portfolioId: string
  name?: string
  symbol: StrategySymbol
  market: string
  startDate: string
  initialCash: number
  totalSplitCount: SplitCount
  firstBuyLimitPercentAbovePreviousClose: number
  autoRestart: boolean
  dividendReinvestment: boolean
  autoAdjust: boolean
  commissionRate: number
  slippageRate: number
  latestSnapshot?: PortfolioSnapshot
  createdAt: string
  updatedAt: string
}

export interface PortfolioSnapshot {
  resolvedAsOfDate: string
  calculatedAt: string
  cycleNo: number
  mode: string
  progressRound: number
  cash: number
  holdingQuantity: number
  averagePurchasePrice: number
  realizedProfitLoss: number
  dividendIncome: number
  netEquity: number
  totalProfitLoss: number
  totalReturnPercent: number
}

export interface PortfolioDashboardResponse {
  portfolio: Portfolio
  dashboard: Dashboard
}

export interface Dashboard {
  symbol: StrategySymbol
  market: string
  startDate: string
  requestedAsOfDate?: string
  resolvedAsOfDate: string
  parameters: DashboardParameters
  current: CurrentState
  valuation: Valuation
  nextOrders: NextOrder[]
  nextOrderContext: NextOrderContext
  cycleSummary: CycleSummary
  dataCoverage: DataCoverage
}

export interface DashboardParameters {
  totalSplitCount: SplitCount
  firstBuyLimitPercentAbovePreviousClose: number
  autoRestart: boolean
  dividendReinvestment: boolean
  autoAdjust: boolean
  commissionRate: number
  slippageRate: number
}

export interface CurrentState {
  cycleNo: number
  mode: string
  progressRound: number
  cash: number
  holdingQuantity: number
  averagePurchasePrice: number
  realizedProfitLoss: number
  dividendIncome: number
}

export interface Valuation {
  close: number
  positionMarketValue: number
  grossEquity: number
  netEquity: number
  totalProfitLoss: number
  totalReturnPercent: number
  positionUnrealizedProfitLoss: number
  positionReturnPercent: number
}

export interface NextOrder {
  side: 'BUY' | 'SELL'
  orderType: 'LOC' | 'LIMIT' | 'MOC'
  price?: number
  quantity: number
  orderTag: string
  notional?: number
}

export interface NextOrderContext {
  orderSessionDate: string
  previousClose: number
  starPercent?: number
  starPrice?: number
  starBuyPrice?: number
  targetSellPrice?: number
  oneBuyBudget: number
  reverseStarPrice?: number
}

export interface CycleSummary {
  cycleCount: number
  completedCycleCount: number
  currentCycleStartedAt?: string
}

export interface DataCoverage {
  from: string
  to: string
  candleCount: number
}

export interface ApiError {
  message: string
}

const API_BASE_URL = import.meta.env.VITE_BACKTEST_API_BASE_URL ?? ''

export async function fetchPortfolios(): Promise<Portfolio[]> {
  return request<Portfolio[]>('/backtests/laor-v4/portfolios')
}

export async function createPortfolio(payload: CreatePortfolioRequest): Promise<Portfolio> {
  return request<Portfolio>('/backtests/laor-v4/portfolios', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function fetchPortfolioDashboard(
  portfolioId: string,
  asOfDate?: string,
): Promise<PortfolioDashboardResponse> {
  const params = new URLSearchParams()
  if (asOfDate) {
    params.set('asOfDate', asOfDate)
  }
  const query = params.toString()
  return request<PortfolioDashboardResponse>(
    `/backtests/laor-v4/portfolios/${portfolioId}/dashboard${query ? `?${query}` : ''}`,
  )
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
    ...init,
  })

  if (!response.ok) {
    const detail = await readError(response)
    throw new Error(detail.message)
  }

  return response.json() as Promise<T>
}

async function readError(response: Response): Promise<ApiError> {
  const text = await response.text()
  if (!text) {
    return { message: `${response.status} ${response.statusText}` }
  }

  try {
    const payload = JSON.parse(text) as { message?: string; error?: string }
    return {
      message: payload.message ?? payload.error ?? text,
    }
  } catch {
    return { message: text }
  }
}
