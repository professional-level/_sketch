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

export interface PortfolioTradesPage {
  portfolioId: string
  symbol: StrategySymbol
  market: string
  startDate: string
  requestedAsOfDate?: string
  resolvedAsOfDate: string
  page: number
  size: number
  total: number
  items: BacktestTrade[]
}

export interface PortfolioDailyFlowPage {
  portfolioId: string
  symbol: StrategySymbol
  market: string
  startDate: string
  requestedAsOfDate?: string
  resolvedAsOfDate: string
  page: number
  size: number
  total: number
  items: DailyFlowItem[]
}

export interface PortfolioIndexResponse {
  portfolioId: string
  symbol: StrategySymbol
  market: string
  startDate: string
  requestedAsOfDate?: string
  resolvedAsOfDate: string
  base: IndexBase
  series: ChartSeries[]
  points: IndexPoint[]
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

export interface BacktestTrade {
  side: 'BUY' | 'SELL'
  symbol: StrategySymbol
  date: string
  quantity: number
  price: number
  notional: number
  commission: number
  orderType?: string
  orderTag?: string
  cycleNo?: number
}

export interface DailyFlowItem {
  date: string
  referenceDate: string
  cycleNo: number
  previousClose: number
  close: number
  orders: NextOrder[]
  filledOrders: BacktestTrade[]
  before: DailyState
  after: DailyState
  dailyDividendIncome: number
  cycleClosed: boolean
  tradingCompleted: boolean
}

export interface DailyState {
  cycleNo: number
  mode: string
  progressRound: number
  cash: number
  holdingQuantity: number
  averagePurchasePrice: number
  realizedProfitLoss: number
  dividendIncome: number
}

export interface IndexBase {
  baseDate: string
  baseValue: number
  initialCash: number
  benchmarkSymbol: StrategySymbol
  benchmarkClose: number
}

export interface ChartSeries {
  key: 'laorIndex' | 'benchmarkIndex' | string
  label: string
  chartType: 'LineSeries' | string
  color: string
  data: ChartPoint[]
}

export interface ChartPoint {
  time: string
  value: number
}

export interface IndexPoint {
  date: string
  laorIndex: number
  benchmarkIndex: number
  netEquity: number
  cash: number
  holdingQuantity: number
  averagePurchasePrice: number
  realizedProfitLoss: number
  dividendIncome: number
  progressRound: number
  cycleNo: number
  mode: string
  close: number
  benchmarkClose: number
  basePoint: boolean
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

export async function fetchPortfolioTrades(
  portfolioId: string,
  params: { asOfDate?: string; page?: number; size?: number; sort?: 'ASC' | 'DESC' },
): Promise<PortfolioTradesPage> {
  return request<PortfolioTradesPage>(
    `/backtests/laor-v4/portfolios/${portfolioId}/trades${toQueryString(params)}`,
  )
}

export async function fetchPortfolioDailyFlow(
  portfolioId: string,
  params: { asOfDate?: string; page?: number; size?: number; sort?: 'ASC' | 'DESC' },
): Promise<PortfolioDailyFlowPage> {
  return request<PortfolioDailyFlowPage>(
    `/backtests/laor-v4/portfolios/${portfolioId}/daily-flow${toQueryString(params)}`,
  )
}

export async function fetchPortfolioIndex(
  portfolioId: string,
  params: { asOfDate?: string } = {},
): Promise<PortfolioIndexResponse> {
  return request<PortfolioIndexResponse>(
    `/backtests/laor-v4/portfolios/${portfolioId}/index${toQueryString(params)}`,
  )
}

function toQueryString(params: { asOfDate?: string; page?: number; size?: number; sort?: 'ASC' | 'DESC' }): string {
  const searchParams = new URLSearchParams()
  if (params.asOfDate) {
    searchParams.set('asOfDate', params.asOfDate)
  }
  if (params.page !== undefined) {
    searchParams.set('page', String(params.page))
  }
  if (params.size !== undefined) {
    searchParams.set('size', String(params.size))
  }
  if (params.sort) {
    searchParams.set('sort', params.sort)
  }
  const query = searchParams.toString()
  return query ? `?${query}` : ''
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
