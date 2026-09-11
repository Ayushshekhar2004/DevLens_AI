import { useState } from 'react'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { HistoryPage } from './pages/HistoryPage'
import { clearAuthSession, loadAuthSession, saveAuthSession } from './services/authSession'
import type { AuthSession } from './types/auth'

type AuthPage = 'login' | 'register'
type AppPage = 'dashboard' | 'history'

export default function App() {
  const [session, setSession] = useState<AuthSession | null>(() => loadAuthSession())
  const [authPage, setAuthPage] = useState<AuthPage>('login')
  const [loginEmail, setLoginEmail] = useState('')
  const [notice, setNotice] = useState<string | undefined>()
  const [appPage, setAppPage] = useState<AppPage>('dashboard')

  function authenticate(nextSession: AuthSession) {
    saveAuthSession(nextSession)
    setSession(nextSession)
    setNotice(undefined)
    setAppPage('dashboard')
  }

  function logout(message?: string) {
    clearAuthSession()
    setSession(null)
    setAuthPage('login')
    setNotice(message)
  }

  if (session) {
    if (appPage === 'history') {
      return (
        <HistoryPage
          session={session}
          onDashboard={() => setAppPage('dashboard')}
          onLogout={() => logout('You have been logged out.')}
          onSessionExpired={() => logout('Your session expired. Log in again to continue.')}
        />
      )
    }
    return (
      <HomePage
        session={session}
        onHistory={() => setAppPage('history')}
        onLogout={() => logout('You have been logged out.')}
        onSessionExpired={() => logout('Your session expired. Log in again to continue.')}
      />
    )
  }

  if (authPage === 'register') {
    return (
      <RegisterPage
        onRegistered={(email) => {
          setLoginEmail(email)
          setNotice('Account created successfully. Log in to continue.')
          setAuthPage('login')
        }}
        onShowLogin={() => {
          setNotice(undefined)
          setAuthPage('login')
        }}
      />
    )
  }

  return (
    <LoginPage
      initialEmail={loginEmail}
      notice={notice}
      onAuthenticated={authenticate}
      onShowRegister={() => {
        setNotice(undefined)
        setAuthPage('register')
      }}
    />
  )
}
