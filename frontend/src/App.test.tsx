import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from './test/utils'
import App from './App'

describe('App', () => {
  it('renders the home page', () => {
    renderWithProviders(<App />)
    expect(
      screen.getByRole('heading', { name: /find and book the right doctor/i }),
    ).toBeInTheDocument()
  })

  it('sets a route-specific document title and exposes a skip link', () => {
    renderWithProviders(<App />, { initialEntries: ['/doctors'] })

    expect(screen.getByRole('heading', { name: /find a doctor/i })).toBeInTheDocument()
    expect(document.title).toMatch(/find a doctor/i)
    expect(screen.getByRole('link', { name: /skip to main content/i })).toHaveAttribute(
      'href',
      '#main-content',
    )
  })

  it('renders the not-found page for unknown routes', () => {
    renderWithProviders(<App />, { initialEntries: ['/does-not-exist'] })

    expect(screen.getByRole('heading', { name: /page not found/i })).toBeInTheDocument()
    expect(document.title).toMatch(/page not found/i)
    expect(screen.getByRole('link', { name: /go home/i })).toHaveAttribute('href', '/')
  })
})
