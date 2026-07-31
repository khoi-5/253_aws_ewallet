import axios from 'axios'
import { type FormEvent, useState } from 'react'
import { Link } from 'react-router-dom'
import { authApi } from '../apis/authApi'

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [message, setMessage] = useState('')
  const [success, setSuccess] = useState(false)
  const [loading, setLoading] = useState(false)
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setLoading(true); setMessage('')
    try { const data = await authApi.forgotPassword(email.trim().toLowerCase()); setSuccess(true); setMessage(data.message) }
    catch (error) { setSuccess(false); setMessage(axios.isAxiosError<{message?: string}>(error) ? error.response?.data?.message || 'Request failed.' : 'Request failed.') }
    finally { setLoading(false) }
  }
  return <main className="auth-page"><section className="auth-card">
    <div className="auth-heading"><span className="eyebrow">Password recovery</span><h1>Forgot password</h1><p>Enter the email linked to your wallet.</p></div>
    <form className="auth-form" onSubmit={submit}><label>Email<input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} /></label>
      {message && <div className={`form-message ${success ? 'success' : 'error'}`}>{message}</div>}
      <button className="primary-button full-width" disabled={loading}>{loading ? 'Submitting...' : 'Send reset link'}</button></form>
    <p className="auth-switch"><Link to="/login">Back to login</Link></p>
  </section></main>
}
