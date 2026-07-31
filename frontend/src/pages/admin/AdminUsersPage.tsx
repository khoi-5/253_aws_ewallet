import axios from 'axios'
import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  adminApi,
  type AdminUser,
  type AdminUserStatus,
} from '../../apis/adminApi'
import ConfirmationModal from '../../components/ConfirmationModal'
import { useToast } from '../../hooks/useToast'

type StatusFilter = 'all' | AdminUserStatus
type PendingAction = 'ban' | 'unban'

const moneyFormatter = new Intl.NumberFormat('vi-VN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const dateFormatter = new Intl.DateTimeFormat('vi-VN', {
  dateStyle: 'short',
  timeStyle: 'short',
})

function formatMoney(value: number | null) {
  if (value === null || Number.isNaN(Number(value))) {
    return 'N/A'
  }

  return `${moneyFormatter.format(Number(value))} USD`
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

function getStatusAction(user: AdminUser): {
  action: PendingAction
  nextStatus: AdminUserStatus
} {
  return user.status === 'active'
    ? { action: 'ban', nextStatus: 'blocked' }
    : { action: 'unban', nextStatus: 'active' }
}

function AdminUsersPage() {
  const { showToast } = useToast()
  const [users, setUsers] = useState<AdminUser[]>([])
  const [searchTerm, setSearchTerm] = useState('')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all')
  const [isLoading, setIsLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')
  const [updatingUserId, setUpdatingUserId] = useState<number | null>(null)
  const [selectedUser, setSelectedUser] = useState<AdminUser | null>(null)

  const loadUsers = useCallback(async () => {
    setIsLoading(true)
    setErrorMessage('')

    try {
      const data = await adminApi.getUsers()
      setUsers([...data.users].sort((left, right) => right.id - left.id))
    } catch (err) {
      console.error(err)
      if (axios.isAxiosError<{ message?: string }>(err)) {
        const message =
          err.response?.data?.message || err.message || 'Cannot load users'
        setErrorMessage(message)
        showToast(message, 'error')
      } else {
        setErrorMessage('Cannot load users')
        showToast('Cannot load users', 'error')
      }
    } finally {
      setIsLoading(false)
    }
  }, [showToast])

  useEffect(() => {
    void Promise.resolve().then(() => loadUsers())
  }, [loadUsers])

  const filteredUsers = useMemo(() => {
    const normalizedSearch = searchTerm.trim().toLowerCase()

    return users.filter((user) => {
      const matchesStatus =
        statusFilter === 'all' || user.status === statusFilter
      const matchesSearch =
        !normalizedSearch ||
        user.phone.toLowerCase().includes(normalizedSearch) ||
        (user.fullName || '').toLowerCase().includes(normalizedSearch)

      return matchesStatus && matchesSearch
    })
  }, [searchTerm, statusFilter, users])

  const requestConfirmation = (user: AdminUser) => {
    if (updatingUserId !== null) {
      return
    }

    setSelectedUser(user)
  }

  const cancelConfirmation = () => {
    if (updatingUserId === null) {
      setSelectedUser(null)
    }
  }

  const handleStatusChange = async () => {
    if (!selectedUser) {
      return
    }

    if (updatingUserId !== null) {
      return
    }

    const { action, nextStatus } = getStatusAction(selectedUser)

    setUpdatingUserId(selectedUser.id)

    try {
      const data = await adminApi.updateUserStatus(selectedUser.id, nextStatus)
      setUsers((currentUsers) =>
        currentUsers.map((currentUser) =>
          currentUser.id === selectedUser.id
            ? { ...currentUser, status: data.user.status }
            : currentUser,
        ),
      )
      setSelectedUser(null)
      showToast(
        action === 'ban'
          ? 'User banned successfully.'
          : 'User unbanned successfully.',
        'success',
      )
    } catch (err) {
      console.error(err)
      if (axios.isAxiosError<{ message?: string }>(err)) {
        showToast(
          err.response?.data?.message ||
            err.message ||
            'Unable to update user status.',
          'error',
        )
      } else {
        showToast('Unable to update user status.', 'error')
      }
    } finally {
      setUpdatingUserId(null)
    }
  }

  const renderAction = (user: AdminUser) => {
    const { action } = getStatusAction(user)

    return (
      <button
        type="button"
        className={action === 'ban' ? 'danger-button' : 'secondary-button'}
        onClick={() => requestConfirmation(user)}
        disabled={updatingUserId !== null}
      >
        {action === 'ban' ? 'Ban' : 'Unban'}
      </button>
    )
  }

  const selectedAction = selectedUser ? getStatusAction(selectedUser).action : null
  const selectedUserName = selectedUser?.fullName || selectedUser?.phone || ''

  return (
    <main className="dashboard-page admin-page">
      {selectedUser && selectedAction && (
        <ConfirmationModal
          title={`Confirm ${selectedAction}`}
          message={`Are you sure you want to ${selectedAction} ${selectedUserName}?`}
          confirmLabel={`Confirm ${selectedAction}`}
          confirmButtonClassName={
            selectedAction === 'ban' ? 'danger-button' : 'primary-button'
          }
          isConfirming={updatingUserId === selectedUser.id}
          onConfirm={handleStatusChange}
          onCancel={cancelConfirmation}
        />
      )}

      <section className="dashboard-hero">
        <div>
          <span className="eyebrow">User Management</span>
          <h1>Regular users</h1>
          <p>Search accounts and control whether users can access wallets.</p>
        </div>
        <button
          className="secondary-button"
          onClick={loadUsers}
          disabled={isLoading}
        >
          {isLoading ? 'Loading...' : 'Retry'}
        </button>
      </section>

      <section className="dashboard-card">
        <div className="admin-user-toolbar">
          <label>
            Search
            <input
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
              placeholder="Phone or full name"
            />
          </label>
          <label>
            Status
            <select
              value={statusFilter}
              onChange={(event) =>
                setStatusFilter(event.target.value as StatusFilter)
              }
            >
              <option value="all">All</option>
              <option value="active">Active</option>
              <option value="blocked">Blocked</option>
            </select>
          </label>
        </div>

        {errorMessage && (
          <div className="form-message error">
            {errorMessage}. Please try again.
          </div>
        )}

        {isLoading && !users.length && (
          <div className="transaction-state">Loading users...</div>
        )}

        {!isLoading && !errorMessage && !filteredUsers.length && (
          <div className="transaction-state">No users match the filters.</div>
        )}

        {filteredUsers.length > 0 && (
          <>
            <div className="admin-user-table-wrap">
              <table className="admin-user-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Phone</th>
                    <th>Full name</th>
                    <th>Status</th>
                    <th>Wallet balance</th>
                    <th>Created</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredUsers.map((user) => (
                    <tr key={user.id}>
                      <td>{user.id}</td>
                      <td>{user.phone}</td>
                      <td>{user.fullName || 'Not provided'}</td>
                      <td>
                        <span className={`admin-status-badge ${user.status}`}>
                          {user.status}
                        </span>
                      </td>
                      <td>{formatMoney(user.balance)}</td>
                      <td>{formatDate(user.createdAt)}</td>
                      <td>{renderAction(user)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="admin-user-card-list">
              {filteredUsers.map((user) => (
                <article className="admin-user-card" key={user.id}>
                  <div className="admin-user-card-top">
                    <div>
                      <strong>{user.fullName || 'Not provided'}</strong>
                      <span>{user.phone}</span>
                    </div>
                    <span className={`admin-status-badge ${user.status}`}>
                      {user.status}
                    </span>
                  </div>
                  <div className="transaction-meta-grid">
                    <div>
                      <span>User ID</span>
                      <strong>{user.id}</strong>
                    </div>
                    <div>
                      <span>Wallet balance</span>
                      <strong>{formatMoney(user.balance)}</strong>
                    </div>
                    <div>
                      <span>Created</span>
                      <strong>{formatDate(user.createdAt)}</strong>
                    </div>
                    <div>
                      <span>Wallet ID</span>
                      <strong>{user.walletId || 'N/A'}</strong>
                    </div>
                  </div>
                  {renderAction(user)}
                </article>
              ))}
            </div>
          </>
        )}
      </section>
    </main>
  )
}

export default AdminUsersPage
