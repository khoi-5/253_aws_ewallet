import axios from 'axios'
import { type FormEvent, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { authApi } from '../apis/authApi'
import { resetPasswordSchema } from '../schema/authSchema'

export default function ResetPasswordPage() {
  const [params] = useSearchParams()
  const [form, setForm] = useState({ password: '', passwordConfirmation: '' })
  const [message, setMessage] = useState('')
  const [success, setSuccess] = useState(false)
  const [loading, setLoading] = useState(false)
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setMessage('')
    const result = resetPasswordSchema.safeParse(form)
    if (!result.success) { setMessage(result.error.issues[0]?.message || 'Invalid password'); return }
    setLoading(true)
    try { const data = await authApi.resetPassword(params.get('token') || '', result.data.password, result.data.passwordConfirmation); setSuccess(true); setMessage(data.message) }
    catch (error) { setSuccess(false); setMessage(axios.isAxiosError<{message?: string}>(error) ? error.response?.data?.message || 'Reset failed.' : 'Reset failed.') }
    finally { setLoading(false) }
  }
  return <main className="auth-page"><section className="auth-card">
    <div className="auth-heading"><span className="eyebrow">Password recovery</span><h1>Choose a new password</h1></div>
    <form className="auth-form" onSubmit={submit}>
      <label>New password<input type="password" value={form.password} onChange={(e) => setForm({...form, password: e.target.value})} /></label>
      <label>Confirm password<input type="password" value={form.passwordConfirmation} onChange={(e) => setForm({...form, passwordConfirmation: e.target.value})} /></label>
      {message && <div className={`form-message ${success ? 'success' : 'error'}`}>{message}</div>}
      <button className="primary-button full-width" disabled={loading || success}>{loading ? 'Resetting...' : 'Reset password'}</button>
    </form><p className="auth-switch"><Link to="/login">Back to login</Link></p>
  </section></main>
}
