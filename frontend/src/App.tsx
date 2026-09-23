import { useEffect, useState } from 'react'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { HistoryPage } from './pages/HistoryPage'
import { AnalyticsPage } from './pages/AnalyticsPage'
import { RepositoriesPage } from './pages/RepositoriesPage'
import { clearAuthSession, loadAuthSession, saveAuthSession } from './services/authSession'
import type { AuthSession } from './types/auth'

type AuthPage = 'login' | 'register'
type AppPage = 'dashboard' | 'history' | 'analytics' | 'repositories'

export default function App() {
  const [session, setSession] = useState<AuthSession | null>(() => loadAuthSession())
  const [authPage, setAuthPage] = useState<AuthPage>('login')
  const [loginEmail, setLoginEmail] = useState('')
  const [notice, setNotice] = useState<string | undefined>()
  const [appPage, setAppPage] = useState<AppPage>('dashboard')

  useEffect(() => {
    const page = session
      ? { dashboard: 'New analysis', history: 'History', analytics: 'Analytics', repositories: 'Repositories' }[appPage]
      : authPage === 'register' ? 'Create account' : 'Log in'
    document.title = `${page} | DevLens AI`
  }, [appPage, authPage, session])

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
          onAnalytics={() => setAppPage('analytics')}
          onRepositories={() => setAppPage('repositories')}
          onLogout={() => logout('You have been logged out.')}
          onSessionExpired={() => logout('Your session expired. Log in again to continue.')}
        />
      )
    }
    if (appPage === 'analytics') {
      return (
        <AnalyticsPage
          session={session}
          onDashboard={() => setAppPage('dashboard')}
          onHistory={() => setAppPage('history')}
          onRepositories={() => setAppPage('repositories')}
          onLogout={() => logout('You have been logged out.')}
          onSessionExpired={() => logout('Your session expired. Log in again to continue.')}
        />
      )
    }
    if (appPage === 'repositories') {
      return <RepositoriesPage session={session} onDashboard={() => setAppPage('dashboard')} onHistory={() => setAppPage('history')} onAnalytics={() => setAppPage('analytics')} onLogout={() => logout('You have been logged out.')} onSessionExpired={() => logout('Your session expired. Log in again to continue.')} />
    }
    return (
      <HomePage
        session={session}
        onHistory={() => setAppPage('history')}
        onAnalytics={() => setAppPage('analytics')}
        onRepositories={() => setAppPage('repositories')}
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
