import axios from 'axios'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { authApi } from '../apis/authApi'

export default function VerifyEmailPage() {
  const [params] = useSearchParams()
  const [message, setMessage] = useState('Verifying your email...')
  const [success, setSuccess] = useState(false)

  useEffect(() => {
    const token = params.get('token') || ''
    authApi.verifyEmail(token).then((data) => {
      setSuccess(true); setMessage(data.message)
    }).catch((error) => setMessage(axios.isAxiosError<{message?: string}>(error)
      ? error.response?.data?.message || 'Unable to verify email.' : 'Unable to verify email.'))
  }, [params])

  return <main className="auth-page"><section className="auth-card">
    <div className="auth-heading"><span className="eyebrow">Email verification</span><h1>Verify your account</h1></div>
    <div className={`form-message ${success ? 'success' : 'error'}`}>{message}</div>
    <p className="auth-switch"><Link to="/login">Continue to login</Link></p>
  </section></main>
}
