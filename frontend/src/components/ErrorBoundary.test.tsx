import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../test/utils'
import ErrorBoundary from './ErrorBoundary'

function Boom(): never {
  throw new Error('exploding component')
}

describe('ErrorBoundary', () => {
  it('renders its children when nothing throws', () => {
    renderWithProviders(
      <ErrorBoundary>
        <p>Everything is fine</p>
      </ErrorBoundary>,
    )

    expect(screen.getByText('Everything is fine')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('shows a friendly fallback and a way home when a child crashes', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    try {
      renderWithProviders(
        <ErrorBoundary>
          <Boom />
        </ErrorBoundary>,
      )

      expect(screen.getByRole('alert')).toHaveTextContent(/something went wrong/i)
      expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument()
      expect(screen.getByRole('link', { name: /go home/i })).toHaveAttribute('href', '/')
    } finally {
      consoleError.mockRestore()
    }
  })
})
