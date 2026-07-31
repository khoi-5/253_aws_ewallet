import axios from 'axios'
import { useEffect, useRef, useState } from 'react'
import { Link, Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import './App.css'
import { accountApi, accountToAuthUser } from './apis/accountApi'
import { authApi } from './apis/authApi'
import { EMAIL_VERIFICATION_REQUIRED_EVENT } from './apis/axiosClient'
import { walletApi, type WalletTransaction } from './apis/walletApi'
import AdminRoute from './components/routes/AdminRoute'
import ProtectedRoute from './components/routes/ProtectedRoute'
import UserRoute from './components/routes/UserRoute'
import { ToastProvider } from './components/toast/ToastProvider'
import DashboardPage, { type WalletTab } from './pages/DashboardPage'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import ProfilePage from './pages/ProfilePage'
import RegisterPage from './pages/RegisterPage'
import VerifyEmailPage from './pages/VerifyEmailPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'
import ResetPasswordPage from './pages/ResetPasswordPage'
import AdminDashboardPage from './pages/admin/AdminDashboardPage'
import AdminUsersPage from './pages/admin/AdminUsersPage'
import AdminTransactionsPage from './pages/admin/AdminTransactionsPage'
import AdminServicesPage from './pages/admin/AdminServicesPage'
import { useAuthStore } from './store/authStore'

type AppHeaderProps = {
  activeTab: WalletTab
  setActiveTab: (tab: WalletTab) => void
}

type SessionErrorResponse = {
  code?: string
  message?: string
}

function formatBalance(balance?: number) {
  if (balance === undefined) {
    return 'Loading...'
  }

  return Number(balance).toFixed(2)
}

function SessionMonitor() {
  const navigate = useNavigate()
  const token = useAuthStore((state) => state.token)
  const logout = useAuthStore((state) => state.logout)
  const setAccount = useAuthStore((state) => state.setAccount)
  const intervalRef = useRef<number | null>(null)
  const isCheckingRef = useRef(false)

  useEffect(() => {
    if (intervalRef.current !== null) {
      window.clearInterval(intervalRef.current)
      intervalRef.current = null
    }

    if (!token) {
      return undefined
    }

    let isMounted = true

    const clearSession = (message: string) => {
      if (!isMounted) {
        return
      }

      if (intervalRef.current !== null) {
        window.clearInterval(intervalRef.current)
        intervalRef.current = null
      }

      sessionStorage.setItem('authMessage', message)
      logout()
      navigate('/login', { replace: true })
    }

    const checkAccount = async () => {
      if (isCheckingRef.current) {
        return
      }

      isCheckingRef.current = true

      try {
        const account = await accountApi.getCurrentAccount()
        if (isMounted) {
          setAccount(accountToAuthUser(account))
        }
      } catch (err) {
        if (axios.isAxiosError<SessionErrorResponse>(err)) {
          const code = err.response?.data?.code
          if (code === 'ACCOUNT_BLOCKED') {
            clearSession(
              err.response?.data?.message ||
              'Your account has been blocked by an administrator.',
            )
          } else if (code === 'UNAUTHORIZED') {
            clearSession('Your session has expired. Please log in again.')
          }
        }
      } finally {
        isCheckingRef.current = false
      }
    }

    void checkAccount()
    intervalRef.current = window.setInterval(checkAccount, 10000)

    return () => {
      isMounted = false
      if (intervalRef.current !== null) {
        window.clearInterval(intervalRef.current)
        intervalRef.current = null
      }
    }
  }, [logout, navigate, setAccount, token])

  return null
}

function EmailVerificationBanner() {
  const user = useAuthStore((state) => state.user)
  const [message, setMessage] = useState('')
  const [isSending, setIsSending] = useState(false)

  useEffect(() => {
    const handleRequired = (event: Event) => {
      const detail = (event as CustomEvent<{ message?: string }>).detail
      setMessage(
        detail?.message ||
        'Please verify your email before performing this action.',
      )
    }

    window.addEventListener(EMAIL_VERIFICATION_REQUIRED_EVENT, handleRequired)
    return () =>
      window.removeEventListener(
        EMAIL_VERIFICATION_REQUIRED_EVENT,
        handleRequired,
      )
  }, [])

  if (user?.role !== 'user' || user.emailVerified !== false) {
    return null
  }

  const resend = async () => {
    if (!user.email || isSending) return
    setIsSending(true)
    setMessage('')
    try {
      const response = await authApi.resendVerification(user.email)
      setMessage(response.message)
    } catch (error) {
      setMessage(
        axios.isAxiosError<SessionErrorResponse>(error)
          ? error.response?.data?.message ||
          'Unable to resend the verification email.'
          : 'Unable to resend the verification email.',
      )
    } finally {
      setIsSending(false)
    }
  }

  return (
    <aside className="email-verification-banner" role="status">
      <div>
        <strong>Email verification required</strong>
        <span>
          Verify your email to enable deposits, transfers, payments, and other
          wallet transactions.
        </span>
        {message && <span className="verification-banner-message">{message}</span>}
      </div>
      <button
        className="secondary-button"
        onClick={resend}
        disabled={!user.email || isSending}
      >
        {isSending ? 'Sending...' : 'Resend verification email'}
      </button>
    </aside>
  )
}

function AppHeader({ activeTab, setActiveTab }: AppHeaderProps) {
  const navigate = useNavigate()
  const token = useAuthStore((state) => state.token)
  const user = useAuthStore((state) => state.user)
  const wallet = useAuthStore((state) => state.wallet)
  const setWalletData = useAuthStore((state) => state.setWalletData)
  const logout = useAuthStore((state) => state.logout)
  const [isDropdownOpen, setIsDropdownOpen] = useState(false)
  const [isNotificationsOpen, setIsNotificationsOpen] = useState(false)
  const [notifications, setNotifications] = useState<WalletTransaction[]>([])
  const [hasNewNotifications, setHasNewNotifications] = useState(false)
  const prevBalanceRef = useRef(wallet?.balance)

  const balanceText = formatBalance(wallet?.balance)
  const isUser = user?.role === 'user'
  const isAdmin = user?.role === 'admin'

  useEffect(() => {
    if (
      wallet?.balance !== undefined &&
      prevBalanceRef.current !== undefined &&
      wallet.balance !== prevBalanceRef.current
    ) {
      setHasNewNotifications(true)
      walletApi.getMyTransactions().then(data => setNotifications(data.transactions || [])).catch(console.error)
    }
    prevBalanceRef.current = wallet?.balance
  }, [wallet?.balance])

  useEffect(() => {
    if (isUser) {
      walletApi.getMyTransactions().then(data => setNotifications(data.transactions || [])).catch(console.error)
    }
  }, [isUser])

  useEffect(() => {
    if (!token || user?.role !== 'user') {
      return
    }

    walletApi
      .getMyWallet()
      .then((data) => setWalletData(data.user, data.wallet))
      .catch((err) => console.error(err))
  }, [token, user?.role, setWalletData])

  const openDashboardTab = (tab: WalletTab) => {
    setActiveTab(tab)
    navigate('/dashboard')
  }

  const handleLogout = () => {
    logout()
    setIsDropdownOpen(false)
    navigate('/login')
  }

  const openProfile = () => {
    setIsDropdownOpen(false)
    navigate('/profile')
  }

  return (
    <>
      <header className="app-header">
        <div className="header-left">
          <Link className="brand" to="/">
            <span className="brand-mark" style={{ background: 'linear-gradient(135deg, #2563eb, #a855f7)', color: 'white', border: 'none' }}>E</span>
            <span style={{ color: '#0f172a', fontWeight: 800 }}>E-Wallet</span>
          </Link>
          <div className="search-bar" style={{ background: '#f1f5f9', border: 'none', borderRadius: '8px' }}>
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#64748b" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
            <input placeholder="Search transactions, phone number, services..." style={{ background: 'transparent' }} />
          </div>
        </div>

        <div className="header-right">
          <nav className="nav-menu" aria-label="Main navigation">
            <Link to="/">Home</Link>
            {isUser && (
              <>
                <button onClick={() => openDashboardTab('wallet')}>Wallet</button>
                <button onClick={() => openDashboardTab('history')}>
                  Transactions
                </button>
                <button onClick={() => openDashboardTab('deposit')}>
                  Deposit
                </button>
                <button onClick={() => openDashboardTab('services')}>
                  Services
                </button>
              </>
            )}
            {isAdmin && (
              <>
                <Link to="/admin">Admin Dashboard</Link>
                <Link to="/admin/users">Users</Link>
                <Link to="/admin/transactions">Transactions</Link>
                <Link to="/admin/services">Services</Link>
                <button onClick={handleLogout}>Logout</button>
              </>
            )}
          </nav>

          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          {token && isUser && (
            <div style={{ position: 'relative' }}>
              <button
                className="user-icon-button"
                onClick={() => {
                  setIsNotificationsOpen(!isNotificationsOpen)
                  setIsDropdownOpen(false)
                  setHasNewNotifications(false)
                  if (!isNotificationsOpen) {
                    walletApi.getMyTransactions().then(data => setNotifications(data.transactions || [])).catch(console.error)
                  }
                }}
                aria-label="Notifications"
              >
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
                  <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
                </svg>
                {hasNewNotifications && (
                  <div style={{ position: 'absolute', top: 10, right: 12, width: 8, height: 8, backgroundColor: '#e53e3e', borderRadius: '50%' }}></div>
                )}
              </button>

              {isNotificationsOpen && (
                <div className="notifications-dropdown">
                  <div style={{ padding: '12px 16px', borderBottom: '1px solid #e2e8f0', fontWeight: 'bold' }}>Thông Báo Mới Nhận</div>
                  {notifications.length === 0 ? (
                    <div style={{ padding: '16px', textAlign: 'center', color: '#718096' }}>Không có thông báo nào</div>
                  ) : (
                    notifications.map(tx => {
                      const isIncoming = tx.type === 'deposit' || (tx.type === 'transfer' && tx.receiverUserId === user?.id);
                      const amountColor = isIncoming ? '#38a169' : '#e53e3e';
                      const amountPrefix = isIncoming ? '+' : '-';

                      return (
                        <div key={tx.id} style={{ padding: '12px 16px', borderBottom: '1px solid #f7fafc', display: 'flex', gap: '12px' }}>
                          <div style={{
                            width: '40px', height: '40px', borderRadius: '50%', flexShrink: 0,
                            backgroundColor: tx.type === 'deposit' ? '#c6f6d5' : tx.type === 'payment' ? '#fed7d7' : '#bee3f8',
                            display: 'flex', alignItems: 'center', justifyContent: 'center', color: tx.type === 'deposit' ? '#276749' : tx.type === 'payment' ? '#9b2c2c' : '#2c5282'
                          }}>
                            {tx.type === 'deposit' ? (
                              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"></circle><polyline points="8 12 12 16 16 12"></polyline><line x1="12" y1="8" x2="12" y2="16"></line></svg>
                            ) : tx.type === 'payment' ? (
                              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"></path><line x1="3" y1="6" x2="21" y2="6"></line><path d="M16 10a4 4 0 0 1-8 0"></path></svg>
                            ) : (
                              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="22" y1="2" x2="11" y2="13"></line><polygon points="22 2 15 22 11 13 2 9 22 2"></polygon></svg>
                            )}
                          </div>
                          <div>
                            <div style={{ fontWeight: 'bold', fontSize: '0.9rem', marginBottom: '4px' }}>
                              {tx.type === 'deposit' ? 'Nạp tiền thành công' : tx.type === 'payment' ? 'Thanh toán dịch vụ' : 'Chuyển tiền'}
                            </div>
                            <div style={{ fontSize: '0.85rem', color: '#4a5568' }}>
                              {tx.type === 'payment' ? `Bạn đã thanh toán ` :
                                tx.type === 'deposit' ? `Tài khoản nhận thêm ` :
                                  `Bạn đã ${tx.senderUserId === user?.id ? 'chuyển' : 'nhận'} `}
                              <strong style={{ color: amountColor }}>{amountPrefix}{tx.amount} USD</strong>
                              {tx.type === 'payment' ? ` cho dịch vụ ${tx.serviceName}.` : '.'}
                            </div>
                            <div style={{ fontSize: '0.75rem', color: '#a0aec0', marginTop: '4px' }}>
                              {new Date(tx.createdAt || '').toLocaleString()}
                            </div>
                          </div>
                        </div>
                      )
                    })
                  )}
                </div>
              )}
            </div>
          )}

          <div className="account-menu">
            {token ? (
              <>
                <button
                  className="user-icon-button"
                  aria-label="Open account menu"
                  onClick={() => {
                    setIsDropdownOpen((value) => !value)
                    setIsNotificationsOpen(false)
                  }}
                >
                  <span aria-hidden="true" />
                </button>

                {isDropdownOpen && (
                  <div className="account-dropdown">
                    <div className="dropdown-heading">
                      <strong>{user?.fullName || user?.phone || 'Account'}</strong>
                      <span>Account details</span>
                    </div>
                    <div>
                      <span>Full name</span>
                      <strong>{user?.fullName || 'Not provided'}</strong>
                    </div>
                    <div>
                      <span>Phone</span>
                      <strong>{user?.phone || 'Unknown'}</strong>
                    </div>
                    <div>
                      <span>Role</span>
                      <strong>{user?.role || 'Unknown'}</strong>
                    </div>
                    <div>
                      <span>Status</span>
                      <strong>{user?.status || 'Unknown'}</strong>
                    </div>
                    {isAdmin && (
                      <div>
                        <span>Position</span>
                        <strong>{user?.position || 'N/A'}</strong>
                      </div>
                    )}
                    {isUser && (
                      <div>
                        <span>Current balance</span>
                        <strong>{balanceText}</strong>
                      </div>
                    )}
                    <button className="secondary-button" onClick={openProfile}>
                      Edit Profile
                    </button>
                    <button className="logout-button" onClick={handleLogout}>
                      Logout
                    </button>
                  </div>
                )}
              </>
            ) : (
              <Link className="account-button" to="/login">
                Account
              </Link>
            )}
          </div>
        </div>
      </div>
    </header>

      {isUser && (
        <div className="wallet-nav-bar">
          <div className="wallet-nav-inner">
            <button
              className={activeTab === 'wallet' ? 'active pill-tab' : 'pill-tab'}
              onClick={() => openDashboardTab('wallet')}
            >
              {activeTab === 'wallet' ? (
                <div style={{ background: 'white', borderRadius: '50%', width: '20px', height: '20px', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#2563eb' }}>
                  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg>
                </div>
              ) : (
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#2563eb" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg>
              )}
              Wallet Info
            </button>
            <button
              className={activeTab === 'transfer' ? 'active pill-tab' : 'pill-tab'}
              onClick={() => openDashboardTab('transfer')}
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={activeTab === 'transfer' ? 'white' : '#2563eb'} strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><line x1="22" y1="2" x2="11" y2="13"></line><polygon points="22 2 15 22 11 13 2 9 22 2"></polygon></svg>
              Transfer Money
            </button>
            <button
              className={activeTab === 'deposit' ? 'active pill-tab' : 'pill-tab'}
              onClick={() => openDashboardTab('deposit')}
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={activeTab === 'deposit' ? 'white' : '#a855f7'} strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M20 12V8H6a2 2 0 0 1-2-2c0-1.1.9-2 2-2h12v4"></path><path d="M4 6v12c0 1.1.9 2 2 2h14v-4"></path><path d="M18 12a2 2 0 0 0-2 2c0 1.1.9 2 2 2h4v-4h-4z"></path></svg>
              Deposit
            </button>
            <button
              className={activeTab === 'services' ? 'active pill-tab' : 'pill-tab'}
              onClick={() => openDashboardTab('services')}
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={activeTab === 'services' ? 'white' : '#ea580c'} strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="3" width="7" height="7"></rect><rect x="14" y="3" width="7" height="7"></rect><rect x="14" y="14" width="7" height="7"></rect><rect x="3" y="14" width="7" height="7"></rect></svg>
              Services
            </button>
            <button
              className={activeTab === 'history' ? 'active pill-tab' : 'pill-tab'}
              onClick={() => openDashboardTab('history')}
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={activeTab === 'history' ? 'white' : '#10b981'} strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
              Transaction History
            </button>
          </div>
        </div>
      )}
    </>
  )
}

function App() {
  const [activeTab, setActiveTab] = useState<WalletTab>('wallet')

  return (
    <ToastProvider>
      <div className="app-shell">
        <SessionMonitor />
        <AppHeader activeTab={activeTab} setActiveTab={setActiveTab} />
        <EmailVerificationBanner />
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/verify-email" element={<VerifyEmailPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route
            path="/profile"
            element={
              <ProtectedRoute>
                <ProfilePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/dashboard"
            element={
              <UserRoute>
                <DashboardPage key={activeTab} activeTab={activeTab} />
              </UserRoute>
            }
          />
          <Route
            path="/admin"
            element={
              <AdminRoute>
                <AdminDashboardPage />
              </AdminRoute>
            }
          />
          <Route
            path="/admin/users"
            element={
              <AdminRoute>
                <AdminUsersPage />
              </AdminRoute>
            }
          />
          <Route
            path="/admin/transactions"
            element={
              <AdminRoute>
                <AdminTransactionsPage />
              </AdminRoute>
            }
          />
          <Route path="/admin/services" element={<AdminRoute><AdminServicesPage /></AdminRoute>} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </div>
    </ToastProvider>
  )
}

export default App
