import axios from 'axios'
import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import {
  adminApi,
  type AdminTransaction,
  type AdminTransactionFilters,
  type AdminTransactionPagination,
} from '../../apis/adminApi'
import { useToast } from '../../hooks/useToast'

const initialFilters: AdminTransactionFilters = {
  page: 0, size: 20, type: 'all', status: 'all', search: '',
  dateFrom: '', dateTo: '', sortDirection: 'desc',
}
const money = new Intl.NumberFormat('vi-VN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const dateTime = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'medium' })

function display(value: string | number | null | undefined) {
  return value === null || value === undefined || value === '' ? '—' : String(value)
}
function formatMoney(value: number | null) {
  return value === null || Number.isNaN(Number(value)) ? '—' : `${money.format(Number(value))} USD`
}
function formatDate(value: string | null) {
  if (!value) return '—'
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? '—' : dateTime.format(parsed)
}
function description(transaction: AdminTransaction) {
  return transaction.type === 'deposit' &&
    transaction.description?.trim().toLowerCase() === 'simulated deposit'
    ? 'Deposit'
    : transaction.description
}
function party(name: string | null, phone: string | null, fallback = '—') {
  return <><strong>{name || fallback}</strong>{phone && <span>{phone}</span>}</>
}

function TransactionDetails({ transaction, onClose }: { transaction: AdminTransaction; onClose: () => void }) {
  useEffect(() => {
    const close = (event: KeyboardEvent) => event.key === 'Escape' && onClose()
    window.addEventListener('keydown', close)
    return () => window.removeEventListener('keydown', close)
  }, [onClose])
  const fields = [
    ['Transaction code', transaction.transactionCode], ['Type', transaction.type],
    ['Status', transaction.status], ['Amount', formatMoney(transaction.amount)],
    ['Description', description(transaction)], ['Created time', formatDate(transaction.createdAt)],
    ['Sender', transaction.type === 'deposit' ? 'Bank Card' : transaction.senderName],
    ['Sender phone', transaction.type === 'deposit' ? null : transaction.senderPhone],
    ['Sender wallet ID', transaction.senderWalletId], ['Receiver', transaction.receiverName],
    ['Receiver phone', transaction.receiverPhone], ['Receiver wallet ID', transaction.receiverWalletId],
    ['Service', transaction.serviceName], ['Service ID', transaction.serviceId],
    ['Created by', transaction.createdByName], ['Created-by phone', transaction.createdByPhone],
    ['Balance before', formatMoney(transaction.balanceBefore)], ['Balance after', formatMoney(transaction.balanceAfter)],
  ]
  return <div className="modal-backdrop" onMouseDown={onClose}>
    <div className="confirmation-modal transaction-detail-modal" role="dialog" aria-modal="true" aria-labelledby="transaction-detail-title" onMouseDown={(event) => event.stopPropagation()}>
      <div className="transaction-detail-heading"><div><span className="eyebrow">Read-only transaction</span><h2 id="transaction-detail-title">Transaction details</h2></div><button className="modal-close-button" onClick={onClose} aria-label="Close transaction details">×</button></div>
      <div className="transaction-detail-grid">{fields.map(([label, value]) => <div key={label}><span>{label}</span><strong>{display(value)}</strong></div>)}</div>
      <div className="confirmation-modal-actions"><button className="secondary-button" onClick={onClose}>Close</button></div>
    </div>
  </div>
}

export default function AdminTransactionsPage() {
  const { showToast } = useToast()
  const [draft, setDraft] = useState(initialFilters)
  const [filters, setFilters] = useState(initialFilters)
  const [transactions, setTransactions] = useState<AdminTransaction[]>([])
  const [pagination, setPagination] = useState<AdminTransactionPagination | null>(null)
  const [selected, setSelected] = useState<AdminTransaction | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const updateDraft = (key: keyof AdminTransactionFilters, value: string | number) =>
    setDraft((current) => ({ ...current, [key]: value }))
  const load = useCallback(async (signal?: AbortSignal) => {
    setLoading(true); setError('')
    try {
      const data = await adminApi.getTransactions(filters, signal)
      setTransactions(data.transactions); setPagination(data.pagination)
    } catch (cause) {
      if (axios.isCancel(cause)) return
      const message = axios.isAxiosError<{ message?: string }>(cause)
        ? cause.response?.data?.message || cause.message : 'Cannot load transactions'
      setError(message); showToast(message, 'error')
    } finally { if (!signal?.aborted) setLoading(false) }
  }, [filters, showToast])
  useEffect(() => {
    const controller = new AbortController()
    void Promise.resolve().then(() => load(controller.signal))
    return () => controller.abort()
  }, [load])

  const apply = (event: FormEvent) => {
    event.preventDefault()
    if (draft.dateFrom && draft.dateTo && draft.dateFrom > draft.dateTo) {
      showToast('Date from must not be after date to.', 'error'); return
    }
    setFilters({ ...draft, page: 0 })
  }
  const clear = () => { setDraft(initialFilters); setFilters(initialFilters) }
  const changePage = (page: number) => setFilters((current) => ({ ...current, page }))
  const destination = (transaction: AdminTransaction) => transaction.type === 'payment'
    ? party(transaction.serviceName, null, 'Unknown service')
    : party(transaction.receiverName, transaction.receiverPhone, transaction.type === 'deposit' ? 'Wallet recipient' : '—')

  return <main className="dashboard-page admin-page admin-transactions-page">
    {selected && <TransactionDetails transaction={selected} onClose={() => setSelected(null)} />}
    <section className="dashboard-hero"><div><span className="eyebrow">Transaction Management</span><h1>System transactions</h1><p>Search and review deposits, transfers, and service payments.</p></div><button className="secondary-button" onClick={() => void load()} disabled={loading}>{loading ? 'Loading...' : 'Refresh'}</button></section>
    <section className="dashboard-card">
      <form className="admin-transaction-filters" onSubmit={apply}>
        <label>Search<input value={draft.search} onChange={(e) => updateDraft('search', e.target.value)} placeholder="Code, phone, name, service..." /></label>
        <label>Type<select value={draft.type} onChange={(e) => updateDraft('type', e.target.value)}><option value="all">All</option><option value="deposit">Deposit</option><option value="transfer">Transfer</option><option value="payment">Payment</option></select></label>
        <label>Status<select value={draft.status} onChange={(e) => updateDraft('status', e.target.value)}><option value="all">All</option><option value="success">Success</option><option value="failed">Failed</option></select></label>
        <label>Date from<input type="date" value={draft.dateFrom} onChange={(e) => updateDraft('dateFrom', e.target.value)} /></label>
        <label>Date to<input type="date" value={draft.dateTo} onChange={(e) => updateDraft('dateTo', e.target.value)} /></label>
        <label>Order<select value={draft.sortDirection} onChange={(e) => updateDraft('sortDirection', e.target.value)}><option value="desc">Newest first</option><option value="asc">Oldest first</option></select></label>
        <div className="filter-actions"><button className="primary-button" disabled={loading}>Apply filters</button><button type="button" className="secondary-button" onClick={clear} disabled={loading}>Clear</button></div>
      </form>
      {error && <div className="transaction-state"><p>{error}</p><button className="secondary-button" onClick={() => void load()}>Retry</button></div>}
      {loading && !transactions.length && <div className="transaction-state">Loading transactions...</div>}
      {!loading && !error && !transactions.length && <div className="transaction-state">No transactions match these filters.</div>}
      {transactions.length > 0 && <>
        <div className={`admin-transaction-table-wrap ${loading ? 'is-loading' : ''}`}><table className="admin-transaction-table"><thead><tr><th>Code</th><th>Type</th><th>Status</th><th>Amount</th><th>Sender</th><th>Receiver / Service</th><th>Created by</th><th>Created</th><th>Action</th></tr></thead><tbody>{transactions.map((item) => <tr key={item.id}><td><strong>{item.transactionCode}</strong></td><td><span className="transaction-type-badge">{item.type}</span></td><td><span className={`transaction-status-badge ${item.status}`}>{item.status}</span></td><td><strong>{formatMoney(item.amount)}</strong></td><td>{item.type === 'deposit' ? party('Bank Card', null) : party(item.senderName, item.senderPhone)}</td><td>{destination(item)}</td><td>{party(item.createdByName, item.createdByPhone)}</td><td>{formatDate(item.createdAt)}</td><td><button className="secondary-button detail-button" onClick={() => setSelected(item)}>View</button></td></tr>)}</tbody></table></div>
        <div className="admin-transaction-card-list">{transactions.map((item) => <article className="transaction-card" key={item.id}><div className="transaction-card-top"><div><strong>{item.transactionCode}</strong><span>{formatDate(item.createdAt)}</span></div><strong>{formatMoney(item.amount)}</strong></div><div className="transaction-badge-row"><span className="transaction-type-badge">{item.type}</span><span className={`transaction-status-badge ${item.status}`}>{item.status}</span></div><div className="transaction-meta-grid"><div><span>Sender</span>{item.type === 'deposit' ? party('Bank Card', null) : party(item.senderName, item.senderPhone)}</div><div><span>Receiver / Service</span>{destination(item)}</div></div><button className="secondary-button" onClick={() => setSelected(item)}>View details</button></article>)}</div>
      </>}
      {pagination && <div className="admin-pagination"><span>Page {pagination.totalPages ? pagination.page + 1 : 0} of {pagination.totalPages} · {pagination.totalElements} records</span><label>Page size<select value={filters.size} onChange={(e) => { const size = Number(e.target.value) as 10 | 20 | 50; setDraft((current) => ({ ...current, size })); setFilters((current) => ({ ...current, size, page: 0 })) }}><option value="10">10</option><option value="20">20</option><option value="50">50</option></select></label><div><button className="secondary-button" disabled={!pagination.hasPrevious || loading} onClick={() => changePage(pagination.page - 1)}>Previous</button><button className="secondary-button" disabled={!pagination.hasNext || loading} onClick={() => changePage(pagination.page + 1)}>Next</button></div></div>}
    </section>
  </main>
}
