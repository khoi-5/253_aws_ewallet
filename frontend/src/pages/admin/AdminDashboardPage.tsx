import axios from 'axios'
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  adminApi,
  type AdminDashboardResponse,
} from '../../apis/adminApi'
import { useAuthStore } from '../../store/authStore'

function AdminDashboardPage() {
  const setAccount = useAuthStore((state) => state.setAccount)
  const [dashboard, setDashboard] = useState<AdminDashboardResponse | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')

  const loadDashboard = useCallback(async () => {
    setIsLoading(true)
    setErrorMessage('')

    try {
      const data = await adminApi.getAdminDashboard()
      setDashboard(data)
      setAccount(data.admin)
    } catch (err) {
      console.error(err)
      if (axios.isAxiosError<{ message?: string }>(err)) {
        setErrorMessage(
          err.response?.data?.message ||
            err.message ||
            'Cannot load admin dashboard',
        )
      } else {
        setErrorMessage('Cannot load admin dashboard')
      }
    } finally {
      setIsLoading(false)
    }
  }, [setAccount])

  useEffect(() => {
    void Promise.resolve().then(() => loadDashboard())
  }, [loadDashboard])

  return (
    <main className="dashboard-page admin-page">
      <section className="dashboard-hero">
        <div>
          <span className="eyebrow">Admin Dashboard</span>
          <h1>Hello, {dashboard?.admin.fullName || dashboard?.admin.phone || 'Admin'}</h1>
          <p>Review account activity and manage regular user access.</p>
        </div>
        <div className="admin-hero-actions">
          <Link className="secondary-button" to="/admin/services">
            Manage services
          </Link>
          <Link className="secondary-button" to="/admin/transactions">
            View transactions
          </Link>
          <Link className="primary-button" to="/admin/users">
            Manage users
          </Link>
        </div>
      </section>

      {errorMessage && (
        <section className="dashboard-card">
          <div className="form-message error">{errorMessage}</div>
          <button
            className="secondary-button admin-inline-action"
            onClick={loadDashboard}
            disabled={isLoading}
          >
            {isLoading ? 'Loading...' : 'Retry'}
          </button>
        </section>
      )}

      {isLoading && !dashboard && (
        <section className="dashboard-card">
          <div className="transaction-state">Loading admin dashboard...</div>
        </section>
      )}

      {dashboard && (
        <>
          <section className="dashboard-card">
            <div>
              <span className="eyebrow">Admin Account</span>
              <h2>Current administrator details.</h2>
            </div>
            <div className="account-grid admin-account-grid">
              <div>
                <span>Full name</span>
                <strong>{dashboard.admin.fullName || 'Not provided'}</strong>
              </div>
              <div>
                <span>Phone</span>
                <strong>{dashboard.admin.phone}</strong>
              </div>
              <div>
                <span>Role</span>
                <strong>{dashboard.admin.role}</strong>
              </div>
              <div>
                <span>Status</span>
                <strong>{dashboard.admin.status}</strong>
              </div>
              <div>
                <span>Position</span>
                <strong>{dashboard.admin.position || 'N/A'}</strong>
              </div>
            </div>
          </section>

          <section className="dashboard-card">
            <div>
              <span className="eyebrow">System Summary</span>
              <h2>Current platform totals.</h2>
            </div>
            <div className="admin-summary-grid">
              <div>
                <span>Total users</span>
                <strong>{dashboard.summary.totalUsers}</strong>
              </div>
              <div>
                <span>Active users</span>
                <strong>{dashboard.summary.activeUsers}</strong>
              </div>
              <div>
                <span>Blocked users</span>
                <strong>{dashboard.summary.blockedUsers}</strong>
              </div>
              <div>
                <span>Total transactions</span>
                <strong>{dashboard.summary.totalTransactions}</strong>
              </div>
            </div>
          </section>
        </>
      )}
    </main>
  )
}

export default AdminDashboardPage
