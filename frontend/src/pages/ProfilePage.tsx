import axios from 'axios'
import { type FormEvent, useCallback, useEffect, useMemo, useState } from 'react'
import {
  accountApi,
  accountToAuthUser,
  type CurrentAccountResponse,
} from '../apis/accountApi'
import { useToast } from '../hooks/useToast'
import {
  adminProfileSchema,
  type AdminProfileForm,
  type UserProfileForm,
  userProfileSchema,
} from '../schema/profileSchema'
import { useAuthStore } from '../store/authStore'

type ProfileForm = UserProfileForm

type ProfileErrors = Partial<Record<keyof UserProfileForm, string>>

const moneyFormatter = new Intl.NumberFormat('vi-VN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

function formatMoney(value: number | null | undefined) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return 'N/A'
  }

  return `${moneyFormatter.format(Number(value))} USD`
}

function getFormFromAccount(account: CurrentAccountResponse): ProfileForm {
  if (account.role === 'admin') {
    return {
      fullName: account.profile.fullName || '',
      dateOfBirth: '',
      address: '',
    }
  }

  return {
    fullName: account.profile.fullName || '',
    dateOfBirth: account.profile.dateOfBirth || '',
    address: account.profile.address || '',
  }
}

function normalizeForm(form: ProfileForm) {
  return {
    fullName: form.fullName.trim(),
    dateOfBirth: form.dateOfBirth.trim(),
    address: form.address.trim(),
  }
}

function isSameForm(left: ProfileForm, right: ProfileForm) {
  const normalizedLeft = normalizeForm(left)
  const normalizedRight = normalizeForm(right)

  return (
    normalizedLeft.fullName === normalizedRight.fullName &&
    normalizedLeft.dateOfBirth === normalizedRight.dateOfBirth &&
    normalizedLeft.address === normalizedRight.address
  )
}

function ProfilePage() {
  const { showToast } = useToast()
  const setAccount = useAuthStore((state) => state.setAccount)
  const setWalletData = useAuthStore((state) => state.setWalletData)
  const [account, setCurrentAccount] = useState<CurrentAccountResponse | null>(
    null,
  )
  const [form, setForm] = useState<ProfileForm>({
    fullName: '',
    dateOfBirth: '',
    address: '',
  })
  const [savedForm, setSavedForm] = useState<ProfileForm>({
    fullName: '',
    dateOfBirth: '',
    address: '',
  })
  const [errors, setErrors] = useState<ProfileErrors>({})
  const [errorMessage, setErrorMessage] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [isSaving, setIsSaving] = useState(false)

  const loadProfile = useCallback(async () => {
    setIsLoading(true)
    setErrorMessage('')

    try {
      const data = await accountApi.getCurrentAccount()
      const nextForm = getFormFromAccount(data)
      setCurrentAccount(data)
      setForm(nextForm)
      setSavedForm(nextForm)
      setErrors({})
      const authUser = accountToAuthUser(data)
      if (data.role === 'user' && data.wallet) {
        setWalletData(authUser, data.wallet)
      } else {
        setAccount(authUser)
      }
    } catch (err) {
      if (axios.isAxiosError<{ message?: string }>(err)) {
        const message =
          err.response?.data?.message || err.message || 'Cannot load profile'
        setErrorMessage(message)
        showToast(message, 'error')
      } else {
        setErrorMessage('Cannot load profile')
        showToast('Cannot load profile', 'error')
      }
    } finally {
      setIsLoading(false)
    }
  }, [setAccount, setWalletData, showToast])

  useEffect(() => {
    void Promise.resolve().then(() => loadProfile())
  }, [loadProfile])

  const hasChanges = useMemo(
    () => !isSameForm(form, savedForm),
    [form, savedForm],
  )

  const validateForm = (): ProfileForm | null => {
    if (!account) {
      return null
    }

    const normalizedForm = normalizeForm(form)

    if (account.role === 'admin') {
      const result = adminProfileSchema.safeParse({
        fullName: normalizedForm.fullName,
      } satisfies AdminProfileForm)

      if (!result.success) {
        setErrors({
          fullName: result.error.flatten().fieldErrors.fullName?.[0],
        })
        return null
      }

      return {
        fullName: result.data.fullName,
        dateOfBirth: '',
        address: '',
      }
    }

    const result = userProfileSchema.safeParse(normalizedForm)

    if (!result.success) {
      const fieldErrors = result.error.flatten().fieldErrors
      setErrors({
        fullName: fieldErrors.fullName?.[0],
        dateOfBirth: fieldErrors.dateOfBirth?.[0],
        address: fieldErrors.address?.[0],
      })
      return null
    }

    return result.data
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setErrorMessage('')

    if (!account || isSaving) {
      return
    }

    if (!hasChanges) {
      showToast('No profile changes to save.', 'info')
      return
    }

    const validForm = validateForm()
    if (!validForm) {
      return
    }

    setIsSaving(true)

    try {
      const data = await accountApi.updateCurrentAccountProfile(
        account.role === 'admin'
          ? { fullName: validForm.fullName }
          : {
              fullName: validForm.fullName,
              dateOfBirth: validForm.dateOfBirth || null,
              address: validForm.address || null,
            },
      )
      const nextForm = getFormFromAccount(data)
      const authUser = accountToAuthUser(data)
      setCurrentAccount(data)
      setForm(nextForm)
      setSavedForm(nextForm)
      setErrors({})
      if (data.role === 'user' && data.wallet) {
        setWalletData(authUser, data.wallet)
      } else {
        setAccount(authUser)
      }
      showToast('Profile updated successfully.', 'success')
    } catch (err) {
      if (axios.isAxiosError<{ message?: string }>(err)) {
        showToast(
          err.response?.data?.message || err.message || 'Unable to update profile.',
          'error',
        )
      } else {
        showToast('Unable to update profile.', 'error')
      }
    } finally {
      setIsSaving(false)
    }
  }

  const resetForm = () => {
    setForm(savedForm)
    setErrors({})
    setErrorMessage('')
  }

  return (
    <main className="dashboard-page profile-page">
      <section className="dashboard-hero">
        <div>
          <span className="eyebrow">Account Settings</span>
          <h1>{account?.profile.fullName || account?.phone || 'Profile'}</h1>
          <p>Review account details and update your editable profile fields.</p>
        </div>
        <button
          className="secondary-button"
          onClick={loadProfile}
          disabled={isLoading || isSaving}
        >
          {isLoading ? 'Loading...' : 'Retry'}
        </button>
      </section>

      {errorMessage && (
        <section className="dashboard-card">
          <div className="form-message error">{errorMessage}</div>
        </section>
      )}

      {isLoading && !account && (
        <section className="dashboard-card">
          <div className="transaction-state">Loading profile...</div>
        </section>
      )}

      {account && (
        <form className="dashboard-card profile-form" onSubmit={handleSubmit}>
          <div>
            <span className="eyebrow">Profile Details</span>
            <h2>{account.role === 'admin' ? 'Admin profile' : 'User profile'}</h2>
          </div>

          <div className="account-grid profile-readonly-grid">
            <div>
              <span>Phone</span>
              <strong>{account.phone}</strong>
            </div>
            <div>
              <span>Role</span>
              <strong>{account.role}</strong>
            </div>
            <div>
              <span>Status</span>
              <strong>{account.status}</strong>
            </div>
            {account.role === 'user' ? (
              <div>
                <span>Wallet balance</span>
                <strong>{formatMoney(account.wallet?.balance)}</strong>
              </div>
            ) : (
              <div>
                <span>Position</span>
                <strong>{account.profile.position || 'N/A'}</strong>
              </div>
            )}
          </div>

          <div className="profile-form-grid">
            <label>
              Full name
              <input
                value={form.fullName}
                onChange={(event) =>
                  setForm({ ...form, fullName: event.target.value })
                }
                maxLength={100}
              />
              {errors.fullName && (
                <span className="field-error">{errors.fullName}</span>
              )}
            </label>

            {account.role === 'user' && (
              <>
                <label>
                  Date of birth
                  <input
                    type="date"
                    value={form.dateOfBirth}
                    onChange={(event) =>
                      setForm({ ...form, dateOfBirth: event.target.value })
                    }
                  />
                  {errors.dateOfBirth && (
                    <span className="field-error">{errors.dateOfBirth}</span>
                  )}
                </label>

                <label className="profile-address-field">
                  Address
                  <textarea
                    rows={4}
                    value={form.address}
                    onChange={(event) =>
                      setForm({ ...form, address: event.target.value })
                    }
                    maxLength={255}
                  />
                  {errors.address && (
                    <span className="field-error">{errors.address}</span>
                  )}
                </label>
              </>
            )}
          </div>

          <div className="profile-actions">
            <button
              className="primary-button"
              disabled={isSaving || !hasChanges}
            >
              {isSaving ? 'Saving...' : 'Save changes'}
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={resetForm}
              disabled={isSaving || !hasChanges}
            >
              Reset changes
            </button>
          </div>
        </form>
      )}
    </main>
  )
}

export default ProfilePage
