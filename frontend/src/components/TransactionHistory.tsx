import axios from 'axios'
import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  type WalletTransaction,
  walletApi,
} from '../apis/walletApi'

type TransactionHistoryProps = {
  currentWalletId?: number
  refreshKey: number
}

type TransactionDirection = 'incoming' | 'outgoing' | 'neutral'

type TransactionDisplay = {
  direction: TransactionDirection
  label: string
  signedAmount: number
}

const moneyFormatter = new Intl.NumberFormat('vi-VN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const dateFormatter = new Intl.DateTimeFormat('vi-VN', {
  dateStyle: 'short',
  timeStyle: 'medium',
})

function getTransactionDisplay(
  transaction: WalletTransaction,
  currentWalletId?: number,
): TransactionDisplay {
  const amount = Number(transaction.amount || 0)

  if (currentWalletId !== undefined) {
    if (transaction.senderWalletId === currentWalletId) {
      return {
        direction: 'outgoing',
        label:
          transaction.type === 'payment' ? 'Service payment' : 'Money sent',
        signedAmount: -Math.abs(amount),
      }
    }

    if (transaction.receiverWalletId === currentWalletId) {
      return {
        direction: 'incoming',
        label:
          transaction.type === 'deposit' ? 'Money deposited' : 'Money received',
        signedAmount: Math.abs(amount),
      }
    }
  }

  if (transaction.type === 'deposit') {
    return {
      direction: 'incoming',
      label: 'Money deposited',
      signedAmount: Math.abs(amount),
    }
  }

  if (transaction.type === 'payment') {
    return {
      direction: 'outgoing',
      label: 'Service payment',
      signedAmount: -Math.abs(amount),
    }
  }

  return {
    direction: 'neutral',
    label: 'Transaction',
    signedAmount: amount,
  }
}

function formatMoney(value: number | null | undefined) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return 'N/A'
  }

  return `${moneyFormatter.format(Number(value))} USD`
}

function formatSignedMoney(value: number) {
  const sign = value > 0 ? '+' : value < 0 ? '-' : ''
  return `${sign}${formatMoney(Math.abs(value))}`
}

function formatDate(value: string | null) {
  if (!value) {
    return 'N/A'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return 'N/A'
  }

  return dateFormatter.format(date)
}

function displayDescription(transaction: WalletTransaction) {
  if (
    transaction.type === 'deposit' &&
    transaction.description?.trim().toLowerCase() === 'simulated deposit'
  ) {
    return 'Deposit'
  }
  return transaction.description
}

function sortTransactionsNewestFirst(transactions: WalletTransaction[]) {
  return [...transactions].sort((left, right) => {
    const leftTime = left.createdAt ? new Date(left.createdAt).getTime() : 0
    const rightTime = right.createdAt ? new Date(right.createdAt).getTime() : 0

    return rightTime - leftTime
  })
}

function TransactionMeta({ transaction }: { transaction: WalletTransaction }) {
  const isDeposit = transaction.type === 'deposit'
  return (
    <div className="transaction-meta-grid">
      <div>
        <span>Sender</span>
        <strong>{isDeposit ? 'Bank Card' : transaction.senderName || 'N/A'}</strong>
        {!isDeposit && <small>{transaction.senderPhone || 'N/A'}</small>}
      </div>
      <div>
        <span>Receiver</span>
        <strong>{transaction.receiverName || 'N/A'}</strong>
        <small>{transaction.receiverPhone || 'N/A'}</small>
      </div>
      <div>
        <span>Service</span>
        <strong>{transaction.serviceName || 'N/A'}</strong>
      </div>
      <div>
        <span>Balance</span>
        <strong>
          {formatMoney(transaction.balanceBefore)} →{' '}
          {formatMoney(transaction.balanceAfter)}
        </strong>
      </div>
    </div>
  )
}

function TransactionHistory({
  currentWalletId,
  refreshKey,
}: TransactionHistoryProps) {
  const [transactions, setTransactions] = useState<WalletTransaction[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')

  const loadTransactions = useCallback(async () => {
    setIsLoading(true)
    setErrorMessage('')

    try {
      const data = await walletApi.getMyTransactions()
      setTransactions(sortTransactionsNewestFirst(data.transactions))
    } catch (err) {
      console.error(err)
      if (axios.isAxiosError<{ message?: string }>(err)) {
        setErrorMessage(
          err.response?.data?.message ||
            err.message ||
            'Cannot load transaction history',
        )
      } else {
        setErrorMessage('Cannot load transaction history')
      }
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    void Promise.resolve().then(() => loadTransactions())
  }, [loadTransactions, refreshKey])

  const rows = useMemo(
    () =>
      transactions.map((transaction) => ({
        transaction,
        display: getTransactionDisplay(transaction, currentWalletId),
      })),
    [transactions, currentWalletId],
  )

  return (
    <section className="dashboard-card transaction-history-card">
      <div className="transaction-history-heading">
        <div>
          <span className="eyebrow">Transaction History</span>
          <h2>Your latest wallet activity.</h2>
        </div>
        <button
          className="secondary-button"
          onClick={loadTransactions}
          disabled={isLoading}
        >
          {isLoading ? 'Loading...' : 'Retry'}
        </button>
      </div>

      {errorMessage && (
        <div className="form-message error">
          {errorMessage}. Please try again.
        </div>
      )}

      {isLoading && !transactions.length && (
        <div className="transaction-state">Loading transaction history...</div>
      )}

      {!isLoading && !errorMessage && !transactions.length && (
        <div className="transaction-state">
          No transactions yet. Transfers, deposits, and service payments will
          appear here.
        </div>
      )}

      {rows.length > 0 && (
        <>
          <div className="transaction-table-wrap">
            <table className="transaction-table">
              <thead>
                <tr>
                  <th>Transaction</th>
                  <th>Type</th>
                  <th>Status</th>
                  <th>Amount</th>
                  <th>Date</th>
                  <th>Parties</th>
                  <th>Balance</th>
                </tr>
              </thead>
              <tbody>
                {rows.map(({ transaction, display }) => (
                  <tr key={transaction.id}>
                    <td>
                      <strong>{transaction.transactionCode}</strong>
                      <span>{displayDescription(transaction) || display.label}</span>
                    </td>
                    <td>
                      <span className="transaction-type-badge">
                        {transaction.type}
                      </span>
                    </td>
                    <td>
                      <span
                        className={`transaction-status-badge ${transaction.status}`}
                      >
                        {transaction.status}
                      </span>
                    </td>
                    <td>
                      <strong className={`amount-text ${display.direction}`}>
                        {formatSignedMoney(display.signedAmount)}
                      </strong>
                      <span>{display.label}</span>
                    </td>
                    <td>{formatDate(transaction.createdAt)}</td>
                    <td>
                      {transaction.type === 'deposit' ? (
                        <span>From: Bank Card</span>
                      ) : (
                        <span>
                          From: {transaction.senderName || 'N/A'} (
                          {transaction.senderPhone || 'N/A'})
                        </span>
                      )}
                      <span>
                        To: {transaction.receiverName || 'N/A'} (
                        {transaction.receiverPhone || 'N/A'})
                      </span>
                      {transaction.serviceName && (
                        <span>Service: {transaction.serviceName}</span>
                      )}
                    </td>
                    <td>
                      <span>
                        {formatMoney(transaction.balanceBefore)} →{' '}
                        {formatMoney(transaction.balanceAfter)}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="transaction-card-list">
            {rows.map(({ transaction, display }) => (
              <article className="transaction-card" key={transaction.id}>
                <div className="transaction-card-top">
                  <div>
                    <strong>{transaction.transactionCode}</strong>
                    <span>{formatDate(transaction.createdAt)}</span>
                  </div>
                  <strong className={`amount-text ${display.direction}`}>
                    {formatSignedMoney(display.signedAmount)}
                  </strong>
                </div>

                <div className="transaction-badge-row">
                  <span className="transaction-type-badge">
                    {transaction.type}
                  </span>
                  <span
                    className={`transaction-status-badge ${transaction.status}`}
                  >
                    {transaction.status}
                  </span>
                  <span className={`direction-badge ${display.direction}`}>
                    {display.label}
                  </span>
                </div>

                <p>{displayDescription(transaction) || 'No description'}</p>
                <TransactionMeta transaction={transaction} />
              </article>
            ))}
          </div>
        </>
      )}
    </section>
  )
}

export default TransactionHistory
