import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Activity,
  AlertTriangle,
  ArrowLeft,
  ArrowRight,
  BarChart3,
  CalendarDays,
  Check,
  CircleDollarSign,
  Clock3,
  Database,
  Layers3,
  ListOrdered,
  Loader2,
  Plus,
  RefreshCw,
  Save,
  Settings2,
} from 'lucide-react'
import {
  createPortfolio,
  fetchPortfolioDailyFlow,
  fetchPortfolioDashboard,
  fetchPortfolios,
  fetchPortfolioTrades,
  type BacktestTrade,
  type CreatePortfolioRequest,
  type Dashboard,
  type DailyFlowItem,
  type NextOrder,
  type Portfolio,
  type PortfolioDailyFlowPage,
  type PortfolioTradesPage,
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
type DetailTab = 'overview' | 'trades' | 'flow'

const detailPageSize = 12

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
  const [activeTab, setActiveTab] = useState<DetailTab>('overview')
  const [tradesPage, setTradesPage] = useState<PortfolioTradesPage | null>(null)
  const [dailyFlowPage, setDailyFlowPage] = useState<PortfolioDailyFlowPage | null>(null)
  const [tradePageIndex, setTradePageIndex] = useState(0)
  const [flowPageIndex, setFlowPageIndex] = useState(0)
  const [portfolioLoadState, setPortfolioLoadState] = useState<LoadState>('idle')
  const [dashboardLoadState, setDashboardLoadState] = useState<LoadState>('idle')
  const [detailLoadState, setDetailLoadState] = useState<LoadState>('idle')
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

  const refreshTrades = useCallback(async () => {
    if (!selectedPortfolioId) return
    setDetailLoadState('loading')
    setMessage('')
    try {
      const response = await fetchPortfolioTrades(selectedPortfolioId, {
        asOfDate: asOfDate || undefined,
        page: tradePageIndex,
        size: detailPageSize,
        sort: 'DESC',
      })
      setTradesPage(response)
      setDetailLoadState('idle')
    } catch (error) {
      setDetailLoadState('error')
      setMessage(error instanceof Error ? error.message : '체결 목록을 불러오지 못했습니다.')
    }
  }, [asOfDate, selectedPortfolioId, tradePageIndex])

  const refreshDailyFlow = useCallback(async () => {
    if (!selectedPortfolioId) return
    setDetailLoadState('loading')
    setMessage('')
    try {
      const response = await fetchPortfolioDailyFlow(selectedPortfolioId, {
        asOfDate: asOfDate || undefined,
        page: flowPageIndex,
        size: detailPageSize,
        sort: 'ASC',
      })
      setDailyFlowPage(response)
      setDetailLoadState('idle')
    } catch (error) {
      setDetailLoadState('error')
      setMessage(error instanceof Error ? error.message : '일별 흐름을 불러오지 못했습니다.')
    }
  }, [asOfDate, flowPageIndex, selectedPortfolioId])

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

  useEffect(() => {
    setTradePageIndex(0)
    setFlowPageIndex(0)
    setTradesPage(null)
    setDailyFlowPage(null)
  }, [asOfDate, selectedPortfolioId])

  useEffect(() => {
    if (activeTab === 'trades' && selectedPortfolioId) {
      void refreshTrades()
    }
  }, [activeTab, refreshTrades, selectedPortfolioId])

  useEffect(() => {
    if (activeTab === 'flow' && selectedPortfolioId) {
      void refreshDailyFlow()
    }
  }, [activeTab, refreshDailyFlow, selectedPortfolioId])

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

        <DashboardView
          activeTab={activeTab}
          dashboard={dashboard}
          dailyFlowPage={dailyFlowPage}
          detailLoading={detailLoadState === 'loading'}
          flowPageIndex={flowPageIndex}
          loading={dashboardLoadState === 'loading'}
          onFlowPageChange={setFlowPageIndex}
          onRefreshDailyFlow={() => void refreshDailyFlow()}
          onRefreshTrades={() => void refreshTrades()}
          onTabChange={setActiveTab}
          onTradePageChange={setTradePageIndex}
          selectedPortfolio={selectedPortfolio}
          tradePageIndex={tradePageIndex}
          tradesPage={tradesPage}
        />
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
  activeTab,
  dashboard,
  dailyFlowPage,
  detailLoading,
  flowPageIndex,
  onFlowPageChange,
  onRefreshDailyFlow,
  onRefreshTrades,
  onTabChange,
  onTradePageChange,
  selectedPortfolio,
  tradePageIndex,
  tradesPage,
  loading,
}: {
  activeTab: DetailTab
  dashboard: Dashboard | null
  dailyFlowPage: PortfolioDailyFlowPage | null
  detailLoading: boolean
  flowPageIndex: number
  onFlowPageChange: (page: number) => void
  onRefreshDailyFlow: () => void
  onRefreshTrades: () => void
  onTabChange: (tab: DetailTab) => void
  onTradePageChange: (page: number) => void
  selectedPortfolio?: Portfolio
  tradePageIndex: number
  tradesPage: PortfolioTradesPage | null
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
          <h2>상세</h2>
          <div className="panel-actions">
            {activeTab === 'trades' ? (
              <button className="icon-button subtle" type="button" onClick={onRefreshTrades} title="체결 목록 새로고침">
                {detailLoading ? <Loader2 className="spin" size={16} aria-hidden="true" /> : <RefreshCw size={16} aria-hidden="true" />}
              </button>
            ) : null}
            {activeTab === 'flow' ? (
              <button className="icon-button subtle" type="button" onClick={onRefreshDailyFlow} title="일별 흐름 새로고침">
                {detailLoading ? <Loader2 className="spin" size={16} aria-hidden="true" /> : <RefreshCw size={16} aria-hidden="true" />}
              </button>
            ) : null}
            <Layers3 size={17} aria-hidden="true" />
          </div>
        </div>
        <DetailTabs activeTab={activeTab} onChange={onTabChange} />
        {activeTab === 'overview' ? (
          <OverviewDetails dashboard={dashboard} selectedPortfolio={selectedPortfolio} />
        ) : null}
        {activeTab === 'trades' ? (
          <TradesPanel
            loading={detailLoading}
            onPageChange={onTradePageChange}
            page={tradesPage}
            pageIndex={tradePageIndex}
          />
        ) : null}
        {activeTab === 'flow' ? (
          <DailyFlowPanel
            loading={detailLoading}
            onPageChange={onFlowPageChange}
            page={dailyFlowPage}
            pageIndex={flowPageIndex}
          />
        ) : null}
      </section>
    </div>
  )
}

function DetailTabs({
  activeTab,
  onChange,
}: {
  activeTab: DetailTab
  onChange: (tab: DetailTab) => void
}) {
  return (
    <div className="detail-tabs" role="tablist" aria-label="상세 보기">
      <button className={activeTab === 'overview' ? 'active' : ''} type="button" onClick={() => onChange('overview')}>
        <Activity size={15} aria-hidden="true" />
        개요
      </button>
      <button className={activeTab === 'trades' ? 'active' : ''} type="button" onClick={() => onChange('trades')}>
        <ListOrdered size={15} aria-hidden="true" />
        체결
      </button>
      <button className={activeTab === 'flow' ? 'active' : ''} type="button" onClick={() => onChange('flow')}>
        <Layers3 size={15} aria-hidden="true" />
        일별 흐름
      </button>
    </div>
  )
}

function OverviewDetails({
  dashboard,
  selectedPortfolio,
}: {
  dashboard: Dashboard
  selectedPortfolio: Portfolio
}) {
  return (
    <>
      <div className="detail-grid">
        <Readout label="첫 주문 기준일" value={dashboard.startDate} />
        <Readout label="계산 기준일" value={dashboard.resolvedAsOfDate} />
        <Readout label="데이터 시작" value={dashboard.dataCoverage.from} />
        <Readout label="데이터 끝" value={dashboard.dataCoverage.to} />
        <Readout label="캔들" value={formatNumber(dashboard.dataCoverage.candleCount, 0)} />
        <Readout label="완료 Cycle" value={formatNumber(dashboard.cycleSummary.completedCycleCount, 0)} />
        <Readout label="현재 Cycle 시작" value={dashboard.cycleSummary.currentCycleStartedAt ?? '-'} />
        <Readout label="계산 시각" value={formatDateTime(selectedPortfolio.latestSnapshot?.calculatedAt)} />
      </div>
      <div className="coverage-row">
        <span>{dashboard.parameters.totalSplitCount}분할</span>
        <span>첫 매수 상한 {formatNumber(dashboard.parameters.firstBuyLimitPercentAbovePreviousClose, 2)}%</span>
        <span>수수료 {formatPercent(dashboard.parameters.commissionRate * 100)}</span>
        <span>슬리피지 {formatPercent(dashboard.parameters.slippageRate * 100)}</span>
        <span>{dashboard.parameters.autoRestart ? '자동 재시작' : '단일 Cycle'}</span>
      </div>
    </>
  )
}

function TradesPanel({
  loading,
  onPageChange,
  page,
  pageIndex,
}: {
  loading: boolean
  onPageChange: (page: number) => void
  page: PortfolioTradesPage | null
  pageIndex: number
}) {
  if (loading && !page) {
    return <DetailLoading label="체결 목록 계산 중" />
  }

  if (!page || page.total === 0) {
    return <div className="empty-row">체결 없음</div>
  }

  return (
    <>
      <div className="table-wrap detail-table">
        <table>
          <thead>
            <tr>
              <th>일자</th>
              <th>Cycle</th>
              <th>Side</th>
              <th>Tag</th>
              <th>Price</th>
              <th>Qty</th>
              <th>Notional</th>
              <th>Cost</th>
            </tr>
          </thead>
          <tbody>
            {page.items.map((trade) => (
              <TradeRow key={`${trade.date}-${trade.side}-${trade.orderTag}-${trade.quantity}`} trade={trade} />
            ))}
          </tbody>
        </table>
      </div>
      <Pagination page={page.page} size={page.size} total={page.total} requestedPage={pageIndex} onChange={onPageChange} />
    </>
  )
}

function TradeRow({ trade }: { trade: BacktestTrade }) {
  return (
    <tr>
      <td>{trade.date}</td>
      <td>{trade.cycleNo ?? '-'}</td>
      <td><span className={`side-pill ${trade.side.toLowerCase()}`}>{trade.side}</span></td>
      <td>{trade.orderTag ?? '-'}</td>
      <td>{formatCurrency(trade.price)}</td>
      <td>{formatNumber(trade.quantity, 0)}</td>
      <td>{formatCurrency(trade.notional)}</td>
      <td>{formatCurrency(trade.commission)}</td>
    </tr>
  )
}

function DailyFlowPanel({
  loading,
  onPageChange,
  page,
  pageIndex,
}: {
  loading: boolean
  onPageChange: (page: number) => void
  page: PortfolioDailyFlowPage | null
  pageIndex: number
}) {
  if (loading && !page) {
    return <DetailLoading label="일별 흐름 계산 중" />
  }

  if (!page || page.total === 0) {
    return <div className="empty-row">흐름 없음</div>
  }

  return (
    <>
      <div className="table-wrap flow-table">
        <table>
          <thead>
            <tr>
              <th>일자</th>
              <th>종가</th>
              <th>T</th>
              <th>보유</th>
              <th>현금</th>
              <th>체결</th>
              <th>상태</th>
            </tr>
          </thead>
          <tbody>
            {page.items.map((item) => (
              <DailyFlowRow item={item} key={item.date} />
            ))}
          </tbody>
        </table>
      </div>
      <Pagination page={page.page} size={page.size} total={page.total} requestedPage={pageIndex} onChange={onPageChange} />
    </>
  )
}

function DailyFlowRow({ item }: { item: DailyFlowItem }) {
  return (
    <tr>
      <td>
        <div className="cell-stack">
          <strong>{item.date}</strong>
          <span>{item.referenceDate} 기준</span>
        </div>
      </td>
      <td>
        <div className="cell-stack">
          <strong>{formatCurrency(item.close)}</strong>
          <span>전일 {formatCurrency(item.previousClose)}</span>
        </div>
      </td>
      <td>{formatNumber(item.before.progressRound, 4)} → {formatNumber(item.after.progressRound, 4)}</td>
      <td>{formatNumber(item.before.holdingQuantity, 0)} → {formatNumber(item.after.holdingQuantity, 0)}</td>
      <td>{formatCurrency(item.after.cash)}</td>
      <td>
        {item.filledOrders.length === 0 ? (
          <span className="muted-text">-</span>
        ) : (
          <div className="fill-list">
            {item.filledOrders.map((trade) => (
              <span key={`${trade.side}-${trade.orderTag}-${trade.quantity}`}>
                {trade.side} {trade.quantity} · {trade.orderTag}
              </span>
            ))}
          </div>
        )}
      </td>
      <td>
        <div className="status-list">
          <span>{item.after.mode}</span>
          {item.cycleClosed ? <span className="status-pill">Cycle 종료</span> : null}
          {item.tradingCompleted ? <span className="status-pill">완료</span> : null}
        </div>
      </td>
    </tr>
  )
}

function DetailLoading({ label }: { label: string }) {
  return (
    <div className="empty-row">
      <Loader2 className="spin" size={16} aria-hidden="true" />
      {label}
    </div>
  )
}

function Pagination({
  onChange,
  page,
  requestedPage,
  size,
  total,
}: {
  onChange: (page: number) => void
  page: number
  requestedPage: number
  size: number
  total: number
}) {
  const totalPages = Math.max(1, Math.ceil(total / size))
  const currentPage = Math.min(page, totalPages - 1)
  return (
    <div className="pagination-row">
      <button type="button" disabled={requestedPage <= 0} onClick={() => onChange(Math.max(0, requestedPage - 1))}>
        <ArrowLeft size={15} aria-hidden="true" />
        이전
      </button>
      <span>{currentPage + 1} / {totalPages}</span>
      <button type="button" disabled={requestedPage >= totalPages - 1} onClick={() => onChange(requestedPage + 1)}>
        다음
        <ArrowRight size={15} aria-hidden="true" />
      </button>
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
