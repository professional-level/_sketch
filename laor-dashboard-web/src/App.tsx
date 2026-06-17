import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  AlertTriangle,
  BarChart3,
  CalendarDays,
  Check,
  CircleDollarSign,
  Clock3,
  Database,
  ListOrdered,
  Loader2,
  Plus,
  RefreshCw,
  Save,
  Settings2,
} from 'lucide-react'
import {
  createPortfolio,
  fetchPortfolioDashboard,
  fetchPortfolios,
  type CreatePortfolioRequest,
  type Dashboard,
  type NextOrder,
  type Portfolio,
  type SplitCount,
  type StrategySymbol,
} from './api'
import {
  formatCurrency,
  formatDateTime,
  formatNumber,
  formatPercent,
  formatSignedCurrency,
  todayIsoDate,
} from './format'

type LoadState = 'idle' | 'loading' | 'error'

interface FormState {
  name: string
  symbol: StrategySymbol
  market: string
  startDate: string
  initialCash: string
  totalSplitCount: SplitCount
  firstBuyLimitPercentAbovePreviousClose: string
  autoRestart: boolean
  dividendReinvestment: boolean
  autoAdjust: boolean
  commissionRate: string
  slippageRate: string
}

const defaultForm: FormState = {
  name: 'TQQQ 30분할',
  symbol: 'TQQQ',
  market: 'US',
  startDate: '2024-01-02',
  initialCash: '10000',
  totalSplitCount: 30,
  firstBuyLimitPercentAbovePreviousClose: '12',
  autoRestart: true,
  dividendReinvestment: false,
  autoAdjust: false,
  commissionRate: '0.0005',
  slippageRate: '0',
}

const symbolOptions: StrategySymbol[] = ['TQQQ', 'SOXL']
const splitOptions: SplitCount[] = [20, 30, 40]

export default function App() {
  const [portfolios, setPortfolios] = useState<Portfolio[]>([])
  const [selectedPortfolioId, setSelectedPortfolioId] = useState('')
  const [dashboard, setDashboard] = useState<Dashboard | null>(null)
  const [form, setForm] = useState<FormState>(defaultForm)
  const [asOfDate, setAsOfDate] = useState(todayIsoDate())
  const [portfolioLoadState, setPortfolioLoadState] = useState<LoadState>('idle')
  const [dashboardLoadState, setDashboardLoadState] = useState<LoadState>('idle')
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState('')

  const selectedPortfolio = useMemo(
    () => portfolios.find((portfolio) => portfolio.portfolioId === selectedPortfolioId),
    [portfolios, selectedPortfolioId],
  )

  const refreshPortfolios = useCallback(async () => {
    setPortfolioLoadState('loading')
    setMessage('')
    try {
      const items = await fetchPortfolios()
      setPortfolios(items)
      setSelectedPortfolioId((current) => current || items[0]?.portfolioId || '')
      setPortfolioLoadState('idle')
    } catch (error) {
      setPortfolioLoadState('error')
      setMessage(error instanceof Error ? error.message : '포트폴리오 목록을 불러오지 못했습니다.')
    }
  }, [])

  const refreshDashboard = useCallback(async () => {
    if (!selectedPortfolioId) return
    setDashboardLoadState('loading')
    setMessage('')
    try {
      const response = await fetchPortfolioDashboard(selectedPortfolioId, asOfDate || undefined)
      setDashboard(response.dashboard)
      setPortfolios((items) =>
        items.map((item) =>
          item.portfolioId === response.portfolio.portfolioId ? response.portfolio : item,
        ),
      )
      setDashboardLoadState('idle')
    } catch (error) {
      setDashboardLoadState('error')
      setMessage(error instanceof Error ? error.message : '대시보드를 계산하지 못했습니다.')
    }
  }, [asOfDate, selectedPortfolioId])

  useEffect(() => {
    void refreshPortfolios()
  }, [refreshPortfolios])

  useEffect(() => {
    if (selectedPortfolioId) {
      void refreshDashboard()
    } else {
      setDashboard(null)
    }
  }, [refreshDashboard, selectedPortfolioId])

  async function handleCreatePortfolio(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    setMessage('')
    try {
      const payload = toCreatePortfolioRequest(form)
      const created = await createPortfolio(payload)
      setPortfolios((items) => [created, ...items.filter((item) => item.portfolioId !== created.portfolioId)])
      setSelectedPortfolioId(created.portfolioId)
      setMessage('포트폴리오 설정을 저장했습니다.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '포트폴리오 저장에 실패했습니다.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <div className="brand-row">
          <div className="brand-mark">
            <BarChart3 size={22} aria-hidden="true" />
          </div>
          <div>
            <h1>LAOR V4</h1>
            <span>Portfolio Console</span>
          </div>
        </div>

        <section className="panel compact-panel">
          <div className="panel-title-row">
            <h2>포트폴리오</h2>
            <button className="icon-button" type="button" onClick={() => void refreshPortfolios()} title="새로고침">
              <RefreshCw size={16} aria-hidden="true" />
            </button>
          </div>
          <div className="portfolio-list">
            {portfolioLoadState === 'loading' ? (
              <div className="empty-row">
                <Loader2 className="spin" size={16} aria-hidden="true" />
                불러오는 중
              </div>
            ) : portfolios.length === 0 ? (
              <div className="empty-row">저장된 항목 없음</div>
            ) : (
              portfolios.map((portfolio) => (
                <button
                  className={`portfolio-item ${portfolio.portfolioId === selectedPortfolioId ? 'selected' : ''}`}
                  key={portfolio.portfolioId}
                  type="button"
                  onClick={() => setSelectedPortfolioId(portfolio.portfolioId)}
                >
                  <strong>{portfolio.name || `${portfolio.symbol} ${portfolio.totalSplitCount}`}</strong>
                  <span>
                    {portfolio.symbol} · {portfolio.totalSplitCount}분할 · 기준일 {portfolio.startDate}
                  </span>
                  {portfolio.latestSnapshot ? (
                    <small>{formatSignedCurrency(portfolio.latestSnapshot.totalProfitLoss)}</small>
                  ) : null}
                </button>
              ))
            )}
          </div>
        </section>

        <section className="panel">
          <div className="panel-title-row">
            <h2>설정 저장</h2>
            <Save size={17} aria-hidden="true" />
          </div>
          <PortfolioForm form={form} saving={saving} onChange={setForm} onSubmit={handleCreatePortfolio} />
        </section>
      </aside>

      <section className="workspace">
        <div className="toolbar">
          <div>
            <span className="eyebrow">기준일 계산</span>
            <h2>{selectedPortfolio ? selectedPortfolio.name || selectedPortfolio.symbol : '포트폴리오 선택'}</h2>
          </div>
          <div className="toolbar-actions">
            <label className="date-control">
              <CalendarDays size={16} aria-hidden="true" />
              <input type="date" value={asOfDate} onChange={(event) => setAsOfDate(event.target.value)} />
            </label>
            <button className="primary-button" type="button" disabled={!selectedPortfolioId} onClick={() => void refreshDashboard()}>
              {dashboardLoadState === 'loading' ? <Loader2 className="spin" size={16} aria-hidden="true" /> : <RefreshCw size={16} aria-hidden="true" />}
              계산
            </button>
          </div>
        </div>

        {message ? (
          <div className={portfolioLoadState === 'error' || dashboardLoadState === 'error' ? 'notice error' : 'notice'}>
            {portfolioLoadState === 'error' || dashboardLoadState === 'error' ? (
              <AlertTriangle size={16} aria-hidden="true" />
            ) : (
              <Check size={16} aria-hidden="true" />
            )}
            {message}
          </div>
        ) : null}

        <DashboardView dashboard={dashboard} selectedPortfolio={selectedPortfolio} loading={dashboardLoadState === 'loading'} />
      </section>
    </main>
  )
}

function PortfolioForm({
  form,
  saving,
  onChange,
  onSubmit,
}: {
  form: FormState
  saving: boolean
  onChange: (form: FormState) => void
  onSubmit: (event: React.FormEvent<HTMLFormElement>) => void
}) {
  return (
    <form className="portfolio-form" onSubmit={onSubmit}>
      <label>
        이름
        <input value={form.name} onChange={(event) => onChange({ ...form, name: event.target.value })} />
      </label>
      <div className="field-grid two">
        <label>
          종목
          <select
            value={form.symbol}
            onChange={(event) =>
              onChange({
                ...form,
                symbol: event.target.value as StrategySymbol,
                name: `${event.target.value} ${form.totalSplitCount}분할`,
              })
            }
          >
            {symbolOptions.map((symbol) => (
              <option key={symbol} value={symbol}>
                {symbol}
              </option>
            ))}
          </select>
        </label>
        <label>
          분할
          <select
            value={form.totalSplitCount}
            onChange={(event) =>
              onChange({
                ...form,
                totalSplitCount: Number(event.target.value) as SplitCount,
                name: `${form.symbol} ${event.target.value}분할`,
              })
            }
          >
            {splitOptions.map((split) => (
              <option key={split} value={split}>
                {split}
              </option>
            ))}
          </select>
        </label>
      </div>
      <label>
        첫 주문 기준일
        <input type="date" value={form.startDate} onChange={(event) => onChange({ ...form, startDate: event.target.value })} />
      </label>
      <div className="field-grid two">
        <label>
          초기 현금
          <input
            inputMode="decimal"
            value={form.initialCash}
            onChange={(event) => onChange({ ...form, initialCash: event.target.value })}
          />
        </label>
        <label>
          첫 매수 상한 %
          <input
            inputMode="decimal"
            value={form.firstBuyLimitPercentAbovePreviousClose}
            onChange={(event) =>
              onChange({ ...form, firstBuyLimitPercentAbovePreviousClose: event.target.value })
            }
          />
        </label>
      </div>
      <div className="field-grid two">
        <label>
          수수료율
          <input
            inputMode="decimal"
            value={form.commissionRate}
            onChange={(event) => onChange({ ...form, commissionRate: event.target.value })}
          />
        </label>
        <label>
          슬리피지율
          <input
            inputMode="decimal"
            value={form.slippageRate}
            onChange={(event) => onChange({ ...form, slippageRate: event.target.value })}
          />
        </label>
      </div>
      <div className="toggle-row">
        <label>
          <input
            type="checkbox"
            checked={form.autoRestart}
            onChange={(event) => onChange({ ...form, autoRestart: event.target.checked })}
          />
          자동 재시작
        </label>
        <label>
          <input
            type="checkbox"
            checked={form.dividendReinvestment}
            onChange={(event) => onChange({ ...form, dividendReinvestment: event.target.checked })}
          />
          배당 재투자
        </label>
        <label>
          <input
            type="checkbox"
            checked={form.autoAdjust}
            onChange={(event) => onChange({ ...form, autoAdjust: event.target.checked })}
          />
          수정주가
        </label>
      </div>
      <button className="primary-button full-width" type="submit" disabled={saving}>
        {saving ? <Loader2 className="spin" size={16} aria-hidden="true" /> : <Plus size={16} aria-hidden="true" />}
        저장
      </button>
    </form>
  )
}

function DashboardView({
  dashboard,
  selectedPortfolio,
  loading,
}: {
  dashboard: Dashboard | null
  selectedPortfolio?: Portfolio
  loading: boolean
}) {
  if (!selectedPortfolio) {
    return (
      <div className="blank-state">
        <Database size={28} aria-hidden="true" />
        <strong>포트폴리오를 저장하거나 선택하세요.</strong>
      </div>
    )
  }

  if (loading && !dashboard) {
    return (
      <div className="blank-state">
        <Loader2 className="spin" size={28} aria-hidden="true" />
        <strong>계산 중</strong>
      </div>
    )
  }

  if (!dashboard) {
    return (
      <div className="blank-state">
        <Clock3 size={28} aria-hidden="true" />
        <strong>아직 계산 결과가 없습니다.</strong>
      </div>
    )
  }

  return (
    <div className="dashboard-grid">
      <section className="metric-band">
        <Metric label="순자산" value={formatCurrency(dashboard.valuation.netEquity)} icon={<CircleDollarSign size={18} />} />
        <Metric label="총손익" value={formatSignedCurrency(dashboard.valuation.totalProfitLoss)} tone={dashboard.valuation.totalProfitLoss >= 0 ? 'positive' : 'negative'} />
        <Metric label="수익률" value={formatPercent(dashboard.valuation.totalReturnPercent)} tone={dashboard.valuation.totalReturnPercent >= 0 ? 'positive' : 'negative'} />
        <Metric label="기준 종가" value={formatCurrency(dashboard.valuation.close)} />
      </section>

      <section className="panel dashboard-panel">
        <div className="panel-title-row">
          <h2>현재 상태</h2>
          <Settings2 size={17} aria-hidden="true" />
        </div>
        <div className="state-grid">
          <Readout label="Cycle" value={`${dashboard.current.cycleNo}`} />
          <Readout label="Mode" value={dashboard.current.mode} />
          <Readout label="T" value={formatNumber(dashboard.current.progressRound, 4)} />
          <Readout label="현금" value={formatCurrency(dashboard.current.cash)} />
          <Readout label="보유수량" value={formatNumber(dashboard.current.holdingQuantity, 0)} />
          <Readout label="평단" value={formatCurrency(dashboard.current.averagePurchasePrice)} />
          <Readout label="실현손익" value={formatSignedCurrency(dashboard.current.realizedProfitLoss)} />
          <Readout label="배당" value={formatCurrency(dashboard.current.dividendIncome)} />
        </div>
      </section>

      <section className="panel dashboard-panel">
        <div className="panel-title-row">
          <h2>다음 주문</h2>
          <ListOrdered size={17} aria-hidden="true" />
        </div>
        <div className="order-context">
          <Readout label="주문일" value={dashboard.nextOrderContext.orderSessionDate} />
          <Readout label="기준 종가" value={formatCurrency(dashboard.nextOrderContext.previousClose)} />
          <Readout label="별 매도가" value={formatCurrency(dashboard.nextOrderContext.starPrice)} />
          <Readout label="1회 매수금" value={formatCurrency(dashboard.nextOrderContext.oneBuyBudget)} />
        </div>
        <NextOrdersTable orders={dashboard.nextOrders} />
      </section>

      <section className="panel dashboard-panel wide-panel">
        <div className="panel-title-row">
          <h2>API 윤곽</h2>
          <Database size={17} aria-hidden="true" />
        </div>
        <div className="api-grid">
          <ApiNeed status="ready" title="설정 저장" endpoint="POST /backtests/laor-v4/portfolios" />
          <ApiNeed status="ready" title="현재 상태 계산" endpoint="GET /backtests/laor-v4/portfolios/{id}/dashboard" />
          <ApiNeed status="missing" title="가상 체결 목록" endpoint="GET /backtests/laor-v4/portfolios/{id}/trades" />
          <ApiNeed status="missing" title="일별 상태 흐름" endpoint="GET /backtests/laor-v4/portfolios/{id}/daily-flow" />
        </div>
        <div className="coverage-row">
          <span>데이터 {dashboard.dataCoverage.from} ~ {dashboard.dataCoverage.to}</span>
          <span>{formatNumber(dashboard.dataCoverage.candleCount, 0)} candles</span>
          <span>계산 {formatDateTime(selectedPortfolio.latestSnapshot?.calculatedAt)}</span>
        </div>
      </section>
    </div>
  )
}

function Metric({
  label,
  value,
  tone,
  icon,
}: {
  label: string
  value: string
  tone?: 'positive' | 'negative'
  icon?: React.ReactNode
}) {
  return (
    <div className={`metric-card ${tone ?? ''}`}>
      <span>{icon}{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function Readout({ label, value }: { label: string; value: string }) {
  return (
    <div className="readout">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function NextOrdersTable({ orders }: { orders: NextOrder[] }) {
  if (orders.length === 0) {
    return <div className="empty-row">주문 없음</div>
  }

  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Side</th>
            <th>Type</th>
            <th>Tag</th>
            <th>Price</th>
            <th>Qty</th>
            <th>Notional</th>
          </tr>
        </thead>
        <tbody>
          {orders.map((order) => (
            <tr key={`${order.orderTag}-${order.side}-${order.quantity}`}>
              <td><span className={`side-pill ${order.side.toLowerCase()}`}>{order.side}</span></td>
              <td>{order.orderType}</td>
              <td>{order.orderTag}</td>
              <td>{formatCurrency(order.price)}</td>
              <td>{formatNumber(order.quantity, 0)}</td>
              <td>{formatCurrency(order.notional)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function ApiNeed({
  status,
  title,
  endpoint,
}: {
  status: 'ready' | 'missing'
  title: string
  endpoint: string
}) {
  return (
    <div className={`api-need ${status}`}>
      <span>{status === 'ready' ? '연동됨' : '필요'}</span>
      <strong>{title}</strong>
      <code>{endpoint}</code>
    </div>
  )
}

function toCreatePortfolioRequest(form: FormState): CreatePortfolioRequest {
  return {
    name: form.name.trim() || undefined,
    symbol: form.symbol,
    market: form.market.trim().toUpperCase() || 'US',
    startDate: form.startDate,
    initialCash: parseMoney(form.initialCash),
    totalSplitCount: form.totalSplitCount,
    firstBuyLimitPercentAbovePreviousClose: parseMoney(form.firstBuyLimitPercentAbovePreviousClose),
    autoRestart: form.autoRestart,
    dividendReinvestment: form.dividendReinvestment,
    autoAdjust: form.autoAdjust,
    commissionRate: parseMoney(form.commissionRate),
    slippageRate: parseMoney(form.slippageRate),
  }
}

function parseMoney(value: string): number {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}
