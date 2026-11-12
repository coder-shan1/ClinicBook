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
})
