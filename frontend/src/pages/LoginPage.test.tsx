import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LoginPage } from './LoginPage'
import { session } from '../test/fixtures'

const fetchMock = vi.fn<typeof fetch>()
vi.stubGlobal('fetch', fetchMock)

describe('LoginPage', () => {
  beforeEach(() => fetchMock.mockReset())

  it('validates missing credentials without making a request', async () => {
    render(<LoginPage onAuthenticated={vi.fn()} onShowRegister={vi.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Enter both your email address and password.')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('shows loading state and completes login with the returned session', async () => {
    let resolveLogin!: (value: typeof session) => void
    fetchMock.mockReturnValue(new Promise((resolve) => {
      resolveLogin = (value) => resolve(new Response(JSON.stringify(value), { status: 200 }))
    }))
    const onAuthenticated = vi.fn()
    render(<LoginPage initialEmail="ada@example.com" onAuthenticated={onAuthenticated} onShowRegister={vi.fn()} />)

    await userEvent.type(screen.getByLabelText('Password'), 'strong-pass')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(screen.getByRole('button', { name: 'Logging in…' })).toBeDisabled()
    resolveLogin(session)
    expect(await screen.findByRole('button', { name: 'Log in' })).toBeEnabled()
    expect(onAuthenticated).toHaveBeenCalledWith(session)
  })

  it('shows a backend login error', async () => {
    fetchMock.mockResolvedValue(new Response(
      JSON.stringify({ message: 'Email or password is incorrect' }),
      { status: 401, headers: { 'Content-Type': 'application/json' } },
    ))
    render(<LoginPage initialEmail="ada@example.com" onAuthenticated={vi.fn()} onShowRegister={vi.fn()} />)

    await userEvent.type(screen.getByLabelText('Password'), 'wrong-pass')
    fireEvent.submit(screen.getByRole('button', { name: 'Log in' }).closest('form')!)

    expect(await screen.findByRole('alert')).toHaveTextContent('Email or password is incorrect')
  })
})
