import { useState, type FormEvent } from 'react'
import { AuthLayout } from '../components/AuthLayout'
import { registerUser } from '../services/authApi'

interface RegisterPageProps {
  onRegistered: (email: string) => void
  onShowLogin: () => void
}

export function RegisterPage({ onRegistered, onShowLogin }: RegisterPageProps) {
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)

    if (!name.trim() || !email.trim() || !password) {
      setError('Complete all fields before creating your account.')
      return
    }
    if (password.length < 8) {
      setError('Password must contain at least 8 characters.')
      return
    }

    setIsLoading(true)
    try {
      await registerUser({ name: name.trim(), email: email.trim(), password })
      onRegistered(email.trim())
    } catch (requestError: unknown) {
      setError(requestError instanceof Error ? requestError.message : 'Unable to register.')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <AuthLayout eyebrow="Create account" title="Start reviewing securely" intro="Your analyses will be private to your account.">
      {error && <div className="auth-error" role="alert">{error}</div>}

      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <label htmlFor="register-name">Name</label>
        <input id="register-name" value={name} onChange={(event) => setName(event.target.value)} autoComplete="name" disabled={isLoading} />

        <label htmlFor="register-email">Email</label>
        <input id="register-email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" disabled={isLoading} />

        <label htmlFor="register-password">Password</label>
        <input id="register-password" type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="new-password" minLength={8} maxLength={72} disabled={isLoading} />
        <p className="field-hint">Use between 8 and 72 characters.</p>

        <button className="auth-submit" type="submit" disabled={isLoading}>
          {isLoading && <span className="button-spinner" aria-hidden="true" />}
          {isLoading ? 'Creating account…' : 'Create account'}
        </button>
      </form>

      <p className="auth-switch">Already registered? <button type="button" onClick={onShowLogin}>Log in</button></p>
    </AuthLayout>
  )
}
