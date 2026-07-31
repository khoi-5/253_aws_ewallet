import axios from 'axios'
import { type FormEvent, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { authApi } from '../apis/authApi'
import { registerSchema, type RegisterForm } from '../schema/authSchema'

type RegisterErrors = Partial<Record<keyof RegisterForm, string>>

function RegisterPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState<RegisterForm>({
    phone: '',
    email: '',
    password: '',
    fullName: '',
  })
  const [errors, setErrors] = useState<RegisterErrors>({})
  const [message, setMessage] = useState('')
  const [isSuccess, setIsSuccess] = useState(false)
  const [isLoading, setIsLoading] = useState(false)
  const [showPassword, setShowPassword] = useState(false)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setMessage('')
    setIsSuccess(false)

    const result = registerSchema.safeParse(form)
    if (!result.success) {
      const fieldErrors = result.error.flatten().fieldErrors
      setErrors({
        phone: fieldErrors.phone?.[0],
        email: fieldErrors.email?.[0],
        password: fieldErrors.password?.[0],
        fullName: fieldErrors.fullName?.[0],
      })
      return
    }

    setErrors({})
    setIsLoading(true)

    try {
      const { phone, email, password, fullName } = result.data
      const data = await authApi.register({ phone, email, password, fullName })
      setMessage(data.message || 'Register successfully')
      setIsSuccess(true)
      setTimeout(() => navigate('/login'), 2500)
    } catch (err) {
      console.error(err)
      if (axios.isAxiosError<{ message?: string }>(err)) {
        setMessage(err.response?.data?.message || err.message || 'Register failed')
      } else {
        setMessage('Register failed')
      }
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card">
        <div className="auth-heading">
          <span className="eyebrow">Open your wallet</span>
          <h1>Create your account</h1>
          <p>Create your wallet account and get started.</p>
        </div>

        <form onSubmit={handleSubmit} className="auth-form">
          <label>
            Full name
            <input
              value={form.fullName}
              onChange={(event) =>
                setForm({ ...form, fullName: event.target.value })
              }
              placeholder="Your full name"
            />
            {errors.fullName && (
              <span className="field-error">{errors.fullName}</span>
            )}
          </label>

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
            Email
            <input
              value={form.email}
              onChange={(event) => setForm({ ...form, email: event.target.value })}
              placeholder="you@example.com"
              type="email"
            />
            {errors.email && <span className="field-error">{errors.email}</span>}
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

          {message && (
            <div className={`form-message ${isSuccess ? 'success' : 'error'}`}>
              {message}
            </div>
          )}

          <button className="primary-button full-width" disabled={isLoading}>
            {isLoading ? 'Creating...' : 'Create account'}
          </button>
        </form>

        <p className="auth-switch">
          Already registered? <Link to="/login">Login</Link>
        </p>
      </section>
    </main>
  )
}

export default RegisterPage
