import axios from 'axios'
import { type FormEvent, useCallback, useEffect, useState } from 'react'
import {
  serviceApi,
  type Service,
} from '../apis/serviceApi'
import { walletApi } from '../apis/walletApi'
import { authApi } from '../apis/authApi'
import ConfirmationModal from '../components/ConfirmationModal'
import TransactionHistory from '../components/TransactionHistory'
import { useToast } from '../hooks/useToast'
import { depositSchema, type DepositForm } from '../schema/walletSchema'
import { useAuthStore } from '../store/authStore'

export type WalletTab = 'wallet' | 'transfer' | 'deposit' | 'services' | 'history'

type DashboardPageProps = {
  activeTab: WalletTab
}

type TransferForm = {
  receiverPhone: string
  amount: string
  description: string
}

type DepositErrors = Partial<Record<keyof DepositForm, string>>
type RecipientLookupState =
  | { status: 'idle'; phone: ''; fullName: '' }
  | { status: 'invalid' | 'loading' | 'unavailable' | 'self' | 'error'; phone: string; fullName: '' }
  | { status: 'found'; phone: string; fullName: string }

function formatBalance(balance?: number) {
  if (balance === undefined) {
    return 'Loading...'
  }

  return Number(balance).toFixed(2)
}

function formatCoins(value: number) {
  return `${Number(value).toFixed(2)} USD`
}

function DashboardPage({ activeTab }: DashboardPageProps) {
  const { showToast } = useToast()
  const user = useAuthStore((state) => state.user)
  const wallet = useAuthStore((state) => state.wallet)
  const setWalletData = useAuthStore((state) => state.setWalletData)
  const [transferForm, setTransferForm] = useState<TransferForm>({
    receiverPhone: '',
    amount: '',
    description: '',
  })
  const [depositForm, setDepositForm] = useState<DepositForm>({
    amount: '',
    description: '',
  })
  const [depositErrors, setDepositErrors] = useState<DepositErrors>({})
  const [bankCardForm, setBankCardForm] = useState({
    cardType: 'visa',
    cardHolderName: '',
    cardNumber: '',
    expMonth: '',
    expYear: '',
    cvn: '',
  })
  const [bankCardErrors, setBankCardErrors] = useState<{ [key: string]: string }>({})
  const [walletMessage, setWalletMessage] = useState('')
  const [transferMessage, setTransferMessage] = useState('')
  const [isTransferSuccess, setIsTransferSuccess] = useState(false)
  const [isWalletLoading, setIsWalletLoading] = useState(false)
  const [isTransferLoading, setIsTransferLoading] = useState(false)
  const [recipientLookup, setRecipientLookup] = useState<RecipientLookupState>({
    status: 'idle',
    phone: '',
    fullName: '',
  })
  const [isDepositLoading, setIsDepositLoading] = useState(false)
  const [services, setServices] = useState<Service[]>([])
  const [selectedService, setSelectedService] = useState<Service | null>(null)
  const [isServicesLoading, setIsServicesLoading] = useState(false)
  const [servicesError, setServicesError] = useState('')
  const [payingServiceId, setPayingServiceId] = useState<number | null>(null)
  const [transactionRefreshKey, setTransactionRefreshKey] = useState(0)
  const [verificationMessage, setVerificationMessage] = useState('')
  const [showBalance, setShowBalance] = useState(true)
  const isEmailVerified = user?.emailVerified !== false

  const balanceText = formatBalance(wallet?.balance)
  const selectedServicePrice = Number(selectedService?.price || 0)
  const selectedRemainingBalance =
    wallet && selectedService
      ? Number(wallet.balance) - selectedServicePrice
      : undefined
  const selectedHasInsufficientBalance =
    selectedRemainingBalance !== undefined && selectedRemainingBalance < 0

  const clearSimulatedCard = useCallback(() => {
    setBankCardForm({
      cardType: 'visa',
      cardHolderName: '',
      cardNumber: '',
      expMonth: '',
      expYear: '',
      cvn: '',
    })
    setBankCardErrors({})
  }, [])

  const loadWallet = useCallback(async () => {
    setIsWalletLoading(true)
    setWalletMessage('')

    try {
      const data = await walletApi.getMyWallet()
      setWalletData(data.user, data.wallet)
    } catch (err) {
      console.error(err)
      if (axios.isAxiosError<{ message?: string }>(err)) {
        setWalletMessage(
          err.response?.data?.message || err.message || 'Cannot load wallet',
        )
      } else {
        setWalletMessage('Cannot load wallet')
      }
    } finally {
      setIsWalletLoading(false)
    }
  }, [setWalletData])

  useEffect(() => {
    void Promise.resolve().then(() => loadWallet())
  }, [loadWallet])

  const loadServices = useCallback(async () => {
    setIsServicesLoading(true)
    setServicesError('')

    try {
      const data = await serviceApi.getActiveServices()
      setServices(data.services)
    } catch (err) {
      console.error(err)
      if (axios.isAxiosError<{ message?: string }>(err)) {
        setServicesError(
          err.response?.data?.message ||
          err.message ||
          'Cannot load services',
        )
      } else {
        setServicesError('Cannot load services')
      }
    } finally {
      setIsServicesLoading(false)
    }
  }, [])

  useEffect(() => {
    if (activeTab === 'services') {
      void Promise.resolve().then(() => loadServices())
    }
  }, [activeTab, loadServices])

  useEffect(() => {
    const phone = transferForm.receiverPhone.trim()
    if (!/^0[0-9]{9}$/.test(phone)) return

    const controller = new AbortController()
    const timer = window.setTimeout(async () => {
      setRecipientLookup({ status: 'loading', phone, fullName: '' })
      try {
        const recipient = await walletApi.getRecipient(phone, controller.signal)
        setRecipientLookup({
          status: 'found',
          phone: recipient.phone,
          fullName: recipient.fullName,
        })
      } catch (err) {
        if (axios.isCancel(err)) return
        if (axios.isAxiosError<{ code?: string }>(err)) {
          const code = err.response?.data?.code
          setRecipientLookup({
            status: code === 'SELF_TRANSFER'
              ? 'self'
              : code === 'RECIPIENT_UNAVAILABLE'
                ? 'unavailable'
                : 'error',
            phone,
            fullName: '',
          })
        } else {
          setRecipientLookup({ status: 'error', phone, fullName: '' })
        }
      }
    }, 400)

    return () => {
      window.clearTimeout(timer)
      controller.abort()
    }
  }, [transferForm.receiverPhone])

  const handleTransferSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!isEmailVerified) { setTransferMessage('Verify your email before transferring money.'); return }
    const receiverPhone = transferForm.receiverPhone.trim()
    if (recipientLookup.status !== 'found' || recipientLookup.phone !== receiverPhone) {
      setTransferMessage('Confirm a valid receiver before transferring money.')
      setIsTransferSuccess(false)
      return
    }
    setTransferMessage('')
    setIsTransferSuccess(false)
    setIsTransferLoading(true)

    try {
      const data = await walletApi.transferMoney({
        receiverPhone,
        amount: Number(transferForm.amount),
        description: transferForm.description.trim(),
      })
      setTransferMessage(data.message)
      setIsTransferSuccess(true)
      setTransferForm({ receiverPhone: '', amount: '', description: '' })
      setRecipientLookup({ status: 'idle', phone: '', fullName: '' })
      await loadWallet()
      setTransactionRefreshKey((value) => value + 1)
    } catch (err) {
      console.error(err)
      if (axios.isAxiosError<{ message?: string }>(err)) {
        setTransferMessage(
          err.response?.data?.message || err.message || 'Transfer failed',
        )
      } else {
        setTransferMessage('Transfer failed')
      }
    } finally {
      setIsTransferLoading(false)
    }
  }

  const handleDepositSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!isEmailVerified) { showToast('Verify your email before depositing funds.', 'error'); return }

    const result = depositSchema.safeParse(depositForm)

    const currentYear = new Date().getFullYear()
    const currentMonth = new Date().getMonth() + 1
    let hasBankCardError = false
    const newBankCardErrors: { [key: string]: string } = {}

    if (!bankCardForm.cardHolderName || !bankCardForm.cardHolderName.trim()) { newBankCardErrors.cardHolderName = 'Card holder name is required'; hasBankCardError = true; }

    if (!bankCardForm.cardNumber) { newBankCardErrors.cardNumber = 'Card number is required'; hasBankCardError = true; }
    else if (!/^\d{12}$/.test(bankCardForm.cardNumber.replace(/\s+/g, ''))) { newBankCardErrors.cardNumber = 'Card number must be exactly 12 digits'; hasBankCardError = true; }

    if (!bankCardForm.expMonth) { newBankCardErrors.expMonth = 'Month is required'; hasBankCardError = true; }
    if (!bankCardForm.expYear) { newBankCardErrors.expYear = 'Year is required'; hasBankCardError = true; }

    if (bankCardForm.expMonth && bankCardForm.expYear) {
      const expY = parseInt(bankCardForm.expYear, 10)
      const expM = parseInt(bankCardForm.expMonth, 10)
      if (expY < currentYear || (expY === currentYear && expM < currentMonth)) {
        newBankCardErrors.expMonth = 'Expiration date must be in the future'
        hasBankCardError = true
      }
    }

    if (!bankCardForm.cvn) { newBankCardErrors.cvn = 'CVN is required'; hasBankCardError = true; }
    else if (!/^\d{3}$/.test(bankCardForm.cvn)) { newBankCardErrors.cvn = 'CVN must be exactly 3 digits'; hasBankCardError = true; }

    setBankCardErrors(newBankCardErrors)

    if (!result.success || hasBankCardError) {
      if (!result.success) {
        const fieldErrors = result.error.flatten().fieldErrors
        setDepositErrors({
          amount: fieldErrors.amount?.[0],
          description: fieldErrors.description?.[0],
        })
      } else {
        setDepositErrors({})
      }
      return
    }

    if (!user || !wallet || isDepositLoading) {
      return
    }

    setDepositErrors({})
    setIsDepositLoading(true)

    try {
      const data = await walletApi.depositMoney({
        amount: Number(result.data.amount),
        description: result.data.description,
      })
      setWalletData(user, { ...wallet, balance: data.balance })
      setDepositForm({ amount: '', description: '' })

      clearSimulatedCard()
      showToast('Deposit completed successfully.', 'success')
      await loadWallet()
      setTransactionRefreshKey((value) => value + 1)
    } catch (err) {
      if (axios.isAxiosError<{ message?: string }>(err)) {
        showToast(
          err.response?.data?.message ||
          err.message ||
          'Unable to complete deposit.',
          'error',
        )
      } else {
        showToast('Unable to complete deposit.', 'error')
      }
    } finally {
      setIsDepositLoading(false)
    }
  }

  const handlePayClick = (service: Service) => {
    if (!isEmailVerified) { showToast('Verify your email before paying for services.', 'error'); return }
    if (payingServiceId !== null) {
      return
    }

    setSelectedService(service)
  }

  const handlePaymentConfirm = async () => {
    if (
      !user ||
      !wallet ||
      !selectedService ||
      payingServiceId !== null ||
      !Number.isInteger(selectedService.id)
    ) {
      return
    }

    setPayingServiceId(selectedService.id)

    try {
      const data = await serviceApi.payService(
        selectedService.id,
        `Payment for ${selectedService.name}`,
      )
      setWalletData(user, { ...wallet, balance: data.balance })
      showToast('Payment completed successfully.', 'success')
      setSelectedService(null)
      await loadWallet()
      setTransactionRefreshKey((value) => value + 1)
    } catch (err) {
      if (axios.isAxiosError<{ message?: string }>(err)) {
        showToast(
          err.response?.data?.message ||
          err.message ||
          'Unable to complete payment.',
          'error',
        )
      } else {
        showToast('Unable to complete payment.', 'error')
      }
    } finally {
      setPayingServiceId(null)
    }
  }

  const paymentModalMessage = selectedService
    ? selectedHasInsufficientBalance
      ? `Pay ${formatCoins(selectedServicePrice)} for ${selectedService.name}? Current balance: ${balanceText} USD. Insufficient wallet balance.`
      : `Pay ${formatCoins(selectedServicePrice)} for ${selectedService.name}? Current balance: ${balanceText} USD. Remaining balance: ${formatCoins(selectedRemainingBalance || 0)}.`
    : ''

  return (
    <main className="dashboard-page">
      <section className="dashboard-hero">
        <div className="hero-content">
          <span className="eyebrow text-blue" style={{ fontSize: '12px', fontWeight: 'bold', letterSpacing: '0.05em' }}>MY E-WALLET</span>
          <h1 style={{ fontSize: '36px', marginTop: '12px', marginBottom: '12px' }}>Hello, <span className="highlight-text">{user?.fullName || user?.phone}</span></h1>
          <p style={{ fontSize: '15px', color: '#475569' }}>You are logged in and can manage your wallet.</p>
          {walletMessage && <div className="form-message error">{walletMessage}</div>}
        </div>
        <div className="balance-card">
          <div className="balance-header">
            <span style={{ fontSize: '12px', fontWeight: '600', letterSpacing: '0.05em' }}>CURRENT BALANCE</span>
            <button
              onClick={() => setShowBalance(!showBalance)}
              className="balance-toggle"
              title={showBalance ? "Hide balance" : "Show balance"}
            >
              {showBalance ? (
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path>
                  <line x1="1" y1="1" x2="23" y2="23"></line>
                </svg>
              ) : (
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                  <circle cx="12" cy="12" r="3"></circle>
                </svg>
              )}
            </button>
          </div>
          <div style={{ marginTop: '16px' }}>
            <strong className="balance-amount" style={{ fontSize: '40px', fontWeight: 'bold', lineHeight: 1 }}>{isWalletLoading ? '...' : (showBalance ? balanceText : '******')}</strong>
            <div className="balance-currency" style={{ fontSize: '14px', marginTop: '8px', fontWeight: '600', opacity: 0.9 }}>USD</div>
          </div>
          <svg className="wallet-watermark" xmlns="http://www.w3.org/2000/svg" width="90" height="90" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.3)" strokeWidth="1" strokeLinecap="round" strokeLinejoin="round" style={{ position: 'absolute', bottom: '20px', right: '20px' }}><path d="M20 12V8H6a2 2 0 0 1-2-2c0-1.1.9-2 2-2h12v4"></path><path d="M4 6v12c0 1.1.9 2 2 2h14v-4"></path><path d="M18 12a2 2 0 0 0-2 2c0 1.1.9 2 2 2h4v-4h-4z"></path></svg>
        </div>
      </section>

      {!isEmailVerified && (
        <section className="dashboard-card">
          <div className="form-message error">Your email is not verified. Wallet deposits, transfers, and payments are disabled.</div>
          <button className="secondary-button" disabled={!user?.email} onClick={async () => {
            if (!user?.email) return
            try { const data = await authApi.resendVerification(user.email); setVerificationMessage(data.message) }
            catch { setVerificationMessage('Unable to request another verification link.') }
          }}>Resend verification email</button>
          {verificationMessage && <div className="form-message success">{verificationMessage}</div>}
        </section>
      )}

      {activeTab === 'wallet' && (
        <section className="dashboard-card glass-panel">
          <div>
            <span className="eyebrow text-blue">WALLET INFO</span>
            <h2>This is your current e-wallet information.</h2>
          </div>

          <div className="info-cards-container">
            <div className="info-card">
              <div className="info-icon" style={{ color: '#a855f7', background: 'rgba(168, 85, 247, 0.1)' }}>
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle></svg>
              </div>
              <div className="info-text">
                <span>FULL NAME</span>
                <strong>{user?.fullName || 'Not provided'}</strong>
              </div>
            </div>

            <div className="info-card">
              <div className="info-icon" style={{ color: '#3b82f6', background: 'rgba(59, 130, 246, 0.1)' }}>
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"></path></svg>
              </div>
              <div className="info-text">
                <span>PHONE</span>
                <strong>{user?.phone}</strong>
              </div>
            </div>

            <div className="info-card">
              <div className="info-icon" style={{ color: '#14b8a6', background: 'rgba(20, 184, 166, 0.1)' }}>
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path></svg>
              </div>
              <div className="info-text">
                <span>ROLE</span>
                <strong>{user?.role}</strong>
              </div>
            </div>

            <div className="info-card">
              <div className="info-icon" style={{ color: '#22c55e', background: 'rgba(34, 197, 94, 0.1)' }}>
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg>
              </div>
              <div className="info-text">
                <span>STATUS</span>
                <strong>{user?.status}</strong>
              </div>
            </div>

            <div className="info-card">
              <div className="info-icon" style={{ color: '#f59e0b', background: 'rgba(245, 158, 11, 0.1)' }}>
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="2" y="5" width="20" height="14" rx="2"></rect><line x1="2" y1="10" x2="22" y2="10"></line></svg>
              </div>
              <div className="info-text">
                <span>CURRENT BALANCE</span>
                <strong>{isWalletLoading ? '...' : (showBalance ? balanceText : '******')}</strong>
              </div>
            </div>
          </div>
        </section>
      )}

      {activeTab === 'transfer' && (
        <section className="dashboard-card split-layout glass-panel">
          <div className="layout-left">
            <div>
              <span className="eyebrow text-blue">TRANSFER MONEY</span>
              <h2>Send money to another wallet account.</h2>
            </div>

            <form className="transfer-form" onSubmit={handleTransferSubmit}>
              <label>
                Receiver phone number
                <div className="input-with-icon">
                  <div className="input-icon">
                    <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#2563eb" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"></path></svg>
                  </div>
                  <input
                    inputMode="numeric"
                    maxLength={10}
                    placeholder="0XXXXXXXXX"
                    value={transferForm.receiverPhone}
                    onChange={(event) => {
                      const receiverPhone = event.target.value.replace(/\D/g, '').slice(0, 10)
                      setTransferForm({
                        ...transferForm,
                        receiverPhone,
                      })
                      setRecipientLookup(receiverPhone
                        ? { status: 'invalid', phone: receiverPhone, fullName: '' }
                        : { status: 'idle', phone: '', fullName: '' })
                    }}
                  />
                </div>
                <div className="recipient-lookup-status" aria-live="polite">
                  {recipientLookup.status === 'invalid' && (
                    <span className="recipient-error">Enter a valid 10-digit phone number beginning with 0.</span>
                  )}
                  {recipientLookup.status === 'loading' && (
                    <span>Checking recipient...</span>
                  )}
                  {recipientLookup.status === 'unavailable' && (
                    <span className="recipient-error">No active wallet account was found.</span>
                  )}
                  {recipientLookup.status === 'self' && (
                    <span className="recipient-error">You cannot transfer money to your own wallet.</span>
                  )}
                  {recipientLookup.status === 'error' && (
                    <span className="recipient-error">Unable to check the receiver. Please try again.</span>
                  )}
                </div>
              </label>
              {recipientLookup.status === 'found' &&
                recipientLookup.phone === transferForm.receiverPhone.trim() && (
                  <div className="receiver-preview">
                    <span className="receiver-preview-label">Receiver name</span>
                    <div
                      className="receiver-preview-field"
                      role="textbox"
                      aria-readonly="true"
                      aria-label="Resolved receiver name"
                    >
                      <span className="receiver-preview-icon" aria-hidden="true">
                        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                          <path d="M20 21a8 8 0 0 0-16 0" />
                          <circle cx="12" cy="7" r="4" />
                        </svg>
                      </span>
                      <span className="receiver-preview-value">
                        {recipientLookup.fullName}
                      </span>
                    </div>
                  </div>
                )}
              <label>
                Amount
                <div className="input-with-icon">
                  <div className="input-icon">
                    <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#2563eb" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="12" y1="1" x2="12" y2="23"></line><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"></path></svg>
                  </div>
                  <input

                    type="number"
                    min="0"
                    step="0.01"
                    value={transferForm.amount}
                    onChange={(event) =>
                      setTransferForm({
                        ...transferForm,
                        amount: event.target.value,
                      })
                    }
                  />
                </div>
              </label>
              <label>
                Note or description (optional)
                <div className="input-with-icon">
                  <div className="input-icon" style={{ alignItems: 'flex-start', paddingTop: '12px' }}>
                    <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#2563eb" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path></svg>
                  </div>
                  <textarea
                    placeholder="Optional transfer note"
                    rows={4}
                    value={transferForm.description}
                    onChange={(event) =>
                      setTransferForm({
                        ...transferForm,
                        description: event.target.value,
                      })
                    }
                  />
                </div>
              </label>
              {transferMessage && (
                <div
                  className={`form-message ${isTransferSuccess ? 'success' : 'error'
                    }`}
                >
                  {transferMessage}
                </div>
              )}
              <button
                className="primary-button gradient-button"
                disabled={
                  isTransferLoading ||
                  !isEmailVerified ||
                  recipientLookup.status !== 'found' ||
                  recipientLookup.phone !== transferForm.receiverPhone.trim()
                }
              >
                {isTransferLoading ? 'Transferring...' : 'Transfer'}
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={{ marginLeft: '8px' }}><line x1="22" y1="2" x2="11" y2="13"></line><polygon points="22 2 15 22 11 13 2 9 22 2"></polygon></svg>
              </button>
            </form>
          </div>
          <div className="layout-right illustration-container">
            <img src="/images/transfer-illustration.png" alt="Transfer Illustration" />
          </div>
        </section>
      )}

      {activeTab === 'deposit' && (
        <section className="dashboard-card">
          <div>
            <span className="eyebrow">Deposit</span>
            <h2>Add funds to your wallet.</h2>
          </div>

          <form className="transfer-form deposit-form" onSubmit={handleDepositSubmit}>
            <label>
              Amount
              <div className="input-with-icon">
                <div className="input-icon">
                  <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#2563eb" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="12" y1="1" x2="12" y2="23"></line><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"></path></svg>
                </div>
                <input

                  type="number"
                  min="1"
                  max="10000000"
                  step="0.01"
                  value={depositForm.amount}
                  onChange={(event) =>
                    setDepositForm({
                      ...depositForm,
                      amount: event.target.value,
                    })
                  }
                />
              </div>
              {depositErrors.amount && (
                <span className="field-error">{depositErrors.amount}</span>
              )}
            </label>

            <div className="simulated-card-layout">
              <div className="bank-card-details">
              <label>
                Card Type
                <div className="card-type-options">
                  <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
                    <input type="radio" name="cardType" value="visa" checked={bankCardForm.cardType === 'visa'} onChange={(e) => setBankCardForm({ ...bankCardForm, cardType: e.target.value })} style={{ width: 'auto', margin: 0, padding: 0 }} />
                    <div style={{ border: '1px solid #cbd5e1', padding: '2px 6px', backgroundColor: '#fff', borderRadius: '4px', display: 'flex', alignItems: 'center' }}>
                      <span style={{ color: '#1a1f71', fontWeight: 800, fontStyle: 'italic', fontSize: '14px', lineHeight: 1 }}>VISA</span>
                    </div> Visa
                  </label>
                  <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
                    <input type="radio" name="cardType" value="mastercard" checked={bankCardForm.cardType === 'mastercard'} onChange={(e) => setBankCardForm({ ...bankCardForm, cardType: e.target.value })} style={{ width: 'auto', margin: 0, padding: 0 }} />
                    <div style={{ border: '1px solid #cbd5e1', padding: '4px 6px', backgroundColor: '#fff', borderRadius: '4px', display: 'flex', alignItems: 'center', gap: '2px' }}>
                      <div style={{ display: 'flex' }}>
                        <div style={{ width: '12px', height: '12px', borderRadius: '50%', backgroundColor: '#eb001b', zIndex: 1 }}></div>
                        <div style={{ width: '12px', height: '12px', borderRadius: '50%', backgroundColor: '#f79e1b', marginLeft: '-5px' }}></div>
                      </div>
                    </div> Mastercard
                  </label>

                </div>
              </label>

              <label>
                Card Number
                <input type="text" inputMode="numeric" autoComplete="off" placeholder="For Example: 1111 2222 3333" maxLength={16} value={bankCardForm.cardNumber} onChange={(e) => setBankCardForm({ ...bankCardForm, cardNumber: e.target.value.replace(/[^\d ]/g, '') })} style={{ marginTop: '8px' }} />
                {bankCardErrors.cardNumber && <span className="field-error">{bankCardErrors.cardNumber}</span>}
              </label>

              <label>
                Card Holder Name
                <input type="text" autoComplete="off" placeholder="YOUR NAME" value={bankCardForm.cardHolderName} onChange={(e) => setBankCardForm({ ...bankCardForm, cardHolderName: e.target.value.toUpperCase() })} style={{ marginTop: '8px' }} />
                {bankCardErrors.cardHolderName && <span className="field-error">{bankCardErrors.cardHolderName}</span>}
              </label>

              <div className="card-expiration-grid">
                <label>
                  Expiration Month
                  <select value={bankCardForm.expMonth} onChange={(e) => setBankCardForm({ ...bankCardForm, expMonth: e.target.value })} style={{ cursor: 'pointer', marginTop: '8px' }}>
                    <option value="" disabled>Month</option>
                    {Array.from({ length: 12 }, (_, i) => i + 1).map(m => (
                      <option key={m} value={m}>{m.toString().padStart(2, '0')}</option>
                    ))}
                  </select>
                  {bankCardErrors.expMonth && <span className="field-error">{bankCardErrors.expMonth}</span>}
                </label>
                <label>
                  Expiration Year
                  <select value={bankCardForm.expYear} onChange={(e) => setBankCardForm({ ...bankCardForm, expYear: e.target.value })} style={{ cursor: 'pointer', marginTop: '8px' }}>
                    <option value="" disabled>Year</option>
                    {Array.from({ length: 10 }, (_, i) => new Date().getFullYear() + i).map(y => (
                      <option key={y} value={y}>{y}</option>
                    ))}
                  </select>
                  {bankCardErrors.expYear && <span className="field-error">{bankCardErrors.expYear}</span>}
                </label>
              </div>

              <label>
                CVV
                <div style={{ fontSize: '13px', color: '#64748b', fontWeight: 500, lineHeight: 1.4, marginTop: '4px', marginBottom: '8px' }}>
                  Enter the three-digit security code.
                </div>
                <div className="card-cvv-row">
                  <input type="password" inputMode="numeric" autoComplete="off" style={{ width: '90px' }} maxLength={3} placeholder="•••" value={bankCardForm.cvn} onChange={(e) => setBankCardForm({ ...bankCardForm, cvn: e.target.value.replace(/\D/g, '') })} />
                  <div style={{ display: 'flex', alignItems: 'center', position: 'relative', height: '32px' }}>
                    <svg width="48" height="32" viewBox="0 0 48 32" fill="none" xmlns="http://www.w3.org/2000/svg">
                      <rect x="0.5" y="0.5" width="41" height="25" rx="3.5" fill="#E2E8F0" stroke="#94A3B8" />
                      <rect x="0" y="4" width="42" height="5" fill="#0F172A" />
                      <rect x="18" y="14" width="20" height="9" fill="white" />
                      <text fill="#0F172A" xmlSpace="preserve" style={{ whiteSpace: 'pre' }} fontFamily="Inter, sans-serif" fontSize="7" fontStyle="italic" letterSpacing="0em"><tspan x="20" y="21.5">123</tspan></text>
                      <circle cx="34" cy="22" r="7" fill="white" stroke="#10B981" strokeWidth="1.5" />
                      <text fill="#10B981" xmlSpace="preserve" style={{ whiteSpace: 'pre' }} fontFamily="Inter, sans-serif" fontSize="6" fontWeight="bold" letterSpacing="0em"><tspan x="30" y="24">123</tspan></text>
                      <path d="M39.5 27.5L44 32" stroke="#64748B" strokeWidth="2.5" strokeLinecap="round" />
                    </svg>
                  </div>
                </div>
                {bankCardErrors.cvn && <span className="field-error">{bankCardErrors.cvn}</span>}
              </label>
              </div>

              <aside className={`simulated-card-preview ${bankCardForm.cardType}`}>
                <div className="simulated-card-preview-top">
                  <span>Cloud E-Wallet</span>
                  <strong>{bankCardForm.cardType.toUpperCase()}</strong>
                </div>
                <div className="simulated-card-chip" aria-hidden="true" />
                <strong className="simulated-card-number">
                  {bankCardForm.cardNumber
                    ? `•••• •••• ${bankCardForm.cardNumber.replace(/\s/g, '').slice(-4).padStart(4, '•')}`
                    : '•••• •••• ••••'}
                </strong>
                <div className="simulated-card-preview-bottom">
                  <span>{bankCardForm.cardHolderName || 'YOUR NAME'}</span>
                  <span>
                    {bankCardForm.expMonth || 'MM'}/{bankCardForm.expYear.slice(-2) || 'YY'}
                  </span>
                </div>
              </aside>
            </div>

            <label>
              Note or description (optional)
              <div className="input-with-icon">
                <div className="input-icon" style={{ alignItems: 'flex-start', paddingTop: '12px' }}>
                  <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#2563eb" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path></svg>
                </div>
                <textarea
                  placeholder="Optional deposit note"
                  rows={4}
                  maxLength={255}
                  value={depositForm.description}
                  onChange={(event) =>
                    setDepositForm({
                      ...depositForm,
                      description: event.target.value,
                    })
                  }
                />
              </div>
              {depositErrors.description && (
                <span className="field-error">
                  {depositErrors.description}
                </span>
              )}
            </label>
            <div className="deposit-actions">
              <button className="primary-button" disabled={isDepositLoading || !isEmailVerified}>
                {isDepositLoading ? 'Processing...' : 'Deposit'}
              </button>
              <button
                type="button"
                className="secondary-button"
                disabled={isDepositLoading}
                onClick={() => {
                  setDepositForm({ amount: '', description: '' })
                  setDepositErrors({})
                  clearSimulatedCard()
                }}
              >
                Clear
              </button>
            </div>
          </form>
        </section>
      )}

      {activeTab === 'services' && (
        <section className="dashboard-card glass-panel">
          <div>
            <span className="eyebrow text-blue">SERVICES</span>
            <h2>Pay services from your wallet.</h2>
          </div>

          {servicesError && (
            <div className="service-state">
              <strong>{servicesError}</strong>
              <button
                className="secondary-button"
                onClick={loadServices}
                disabled={isServicesLoading}
              >
                {isServicesLoading ? 'Loading...' : 'Retry'}
              </button>
            </div>
          )}

          {isServicesLoading && !services.length && (
            <div className="service-state" role="status">
              Loading services...
            </div>
          )}

          {!isServicesLoading && !servicesError && !services.length && (
            <div className="service-state">No active services are available.</div>
          )}

          {services.length > 0 && (
            <div className="service-grid">
              {services.map((service) => {
                const servicePrice = Number(service.price)
                const hasInsufficientBalance =
                  wallet !== null && Number(wallet.balance) < servicePrice
                const isPaying = payingServiceId === service.id

                return (
                  <article className="service-card" key={service.id}>
                    <div className="service-image">
                      <span>{service.name}</span>
                    </div>
                    <div className="service-description">
                      <p>
                        {service.description ||
                          'Wallet service payment.'}
                      </p>
                    </div>
                    <div className="service-footer">
                      <strong>{formatCoins(servicePrice)}</strong>
                      <button
                        className="secondary-button"
                        onClick={() => handlePayClick(service)}
                        disabled={isPaying || !isEmailVerified}
                        aria-label={`Pay ${formatCoins(servicePrice)} for ${service.name}`}
                      >
                        {isPaying ? 'Processing...' : 'Pay'}
                      </button>
                    </div>
                    {hasInsufficientBalance && (
                      <span className="service-warning">
                        Insufficient wallet balance.
                      </span>
                    )}
                  </article>
                )
              })}
            </div>
          )}

          {selectedService && (
            <ConfirmationModal
              title="Confirm payment"
              message={paymentModalMessage}
              confirmLabel="Confirm payment"
              isConfirming={payingServiceId === selectedService.id}
              isConfirmDisabled={selectedHasInsufficientBalance}
              onConfirm={handlePaymentConfirm}
              onCancel={() => setSelectedService(null)}
            />
          )}
        </section>
      )}

      {activeTab === 'history' && (
        <TransactionHistory
          currentWalletId={wallet?.id}
          refreshKey={transactionRefreshKey}
        />
      )}
    </main>
  )
}

export default DashboardPage
