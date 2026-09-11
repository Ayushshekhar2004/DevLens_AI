import { useState, type FormEvent } from 'react'
import { AuthLayout } from '../components/AuthLayout'
import { loginUser } from '../services/authApi'
import type { AuthSession } from '../types/auth'

interface LoginPageProps {
  notice?: string
  initialEmail?: string
  onAuthenticated: (session: AuthSession) => void
  onShowRegister: () => void
}

export function LoginPage({ notice, initialEmail = '', onAuthenticated, onShowRegister }: LoginPageProps) {
  const [email, setEmail] = useState(initialEmail)
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)

    if (!email.trim() || !password) {
      setError('Enter both your email address and password.')
      return
    }

    setIsLoading(true)
    try {
      onAuthenticated(await loginUser({ email: email.trim(), password }))
    } catch (requestError: unknown) {
      setError(requestError instanceof Error ? requestError.message : 'Unable to log in.')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <AuthLayout eyebrow="Welcome back" title="Log in to DevLens" intro="Use your account to access your private analyses.">
      {notice && <div className="auth-notice" role="status">{notice}</div>}
      {error && <div className="auth-error" role="alert">{error}</div>}

      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <label htmlFor="login-email">Email</label>
        <input id="login-email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" disabled={isLoading} />

        <label htmlFor="login-password">Password</label>
        <input id="login-password" type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" disabled={isLoading} />

        <button className="auth-submit" type="submit" disabled={isLoading}>
          {isLoading && <span className="button-spinner" aria-hidden="true" />}
          {isLoading ? 'Logging in…' : 'Log in'}
        </button>
      </form>

      <p className="auth-switch">New to DevLens AI? <button type="button" onClick={onShowRegister}>Create an account</button></p>
    </AuthLayout>
  )
}
