import axios from 'axios'
import { type FormEvent, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { authApi } from '../apis/authApi'
import { loginSchema, type LoginForm } from '../schema/authSchema'
import { useAuthStore } from '../store/authStore'

type LoginErrors = Partial<Record<keyof LoginForm, string>>

function consumeAuthMessage() {
  const authMessage = sessionStorage.getItem('authMessage') || ''
  if (authMessage) {
    sessionStorage.removeItem('authMessage')
  }
  return authMessage
}

function LoginPage() {
  const navigate = useNavigate()
  const setAuth = useAuthStore((state) => state.setAuth)
  const token = useAuthStore((state) => state.token)
  const user = useAuthStore((state) => state.user)
  const [form, setForm] = useState<LoginForm>({ phone: '', password: '' })
  const [errors, setErrors] = useState<LoginErrors>({})
  const [message, setMessage] = useState(consumeAuthMessage)
  const [isLoading, setIsLoading] = useState(false)
  const [showPassword, setShowPassword] = useState(false)

  useEffect(() => {
    if (!token || !user) {
      return
    }

    navigate(user.role === 'admin' ? '/admin' : '/dashboard', { replace: true })
  }, [navigate, token, user])

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setMessage('')

    const result = loginSchema.safeParse(form)
    if (!result.success) {
      const fieldErrors = result.error.flatten().fieldErrors
      setErrors({
        phone: fieldErrors.phone?.[0],
        password: fieldErrors.password?.[0],
      })
      return
    }

    setErrors({})
    setIsLoading(true)

    try {
      const data = await authApi.login(result.data)
      const accessToken = data.accessToken || data.token
      if (accessToken && data.user) {
        setAuth(accessToken, data.user, data.wallet)
        navigate(data.user.role === 'admin' ? '/admin' : '/dashboard')
        return
      }
      setMessage(data.message || 'Login failed')
    } catch (err) {
      console.error(err)
      if (axios.isAxiosError<{ message?: string }>(err)) {
        setMessage(err.response?.data?.message || err.message || 'Login failed')
      } else {
        setMessage('Login failed')
      }
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card">
        <div className="auth-heading">
          <span className="eyebrow">Welcome back</span>
          <h1>Login to your wallet</h1>
          <p>
            Log in to manage your balance, transfers, payments, and transaction
            history.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="auth-form">
          <label>
            Phone
            <input
              value={form.phone}
              onChange={(event) =>
                setForm({ ...form, phone: event.target.value })
              }
              placeholder="0911111111"
            />
            {errors.phone && <span className="field-error">{errors.phone}</span>}
          </label>

          <label>
            Password
            <div style={{ position: 'relative' }}>
              <input
                value={form.password}
                onChange={(event) =>
                  setForm({ ...form, password: event.target.value })
                }
                placeholder="123456"
                type={showPassword ? 'text' : 'password'}
                style={{ paddingRight: '40px' }}
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                style={{
                  position: 'absolute',
                  right: '12px',
                  top: '50%',
                  transform: 'translateY(-50%)',
                  background: 'none',
                  border: 'none',
                  cursor: 'pointer',
                  padding: 0,
                  display: 'flex',
                  alignItems: 'center',
                  color: '#64748b'
                }}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
              >
                {showPassword ? (
                  <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path><line x1="1" y1="1" x2="23" y2="23"></line></svg>
                ) : (
                  <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg>
                )}
              </button>
            </div>
            {errors.password && (
              <span className="field-error">{errors.password}</span>
            )}
          </label>

          {message && <div className="form-message error">{message}</div>}

          <button className="primary-button full-width" disabled={isLoading}>
            {isLoading ? 'Logging in...' : 'Login'}
          </button>
        </form>

        <p className="auth-switch">
          New customer? <Link to="/register">Create an account</Link>
        </p>
        <p className="auth-switch">
          <Link to="/forgot-password">Forgot your password?</Link>
        </p>
      </section>
    </main>
  )
}

export default LoginPage
