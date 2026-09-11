import type { ReactNode } from 'react'

interface AuthLayoutProps {
  eyebrow: string
  title: string
  intro: string
  children: ReactNode
}

export function AuthLayout({ eyebrow, title, intro, children }: AuthLayoutProps) {
  return (
    <main className="auth-shell">
      <section className="auth-brand">
        <p className="eyebrow">Developer intelligence, focused</p>
        <h1>DevLens <span>AI</span></h1>
        <p className="intro">Secure, structured code reviews for your development workflow.</p>
      </section>

      <section className="auth-card">
        <p className="section-kicker">{eyebrow}</p>
        <h2>{title}</h2>
        <p className="auth-intro">{intro}</p>
        {children}
      </section>
    </main>
  )
}
