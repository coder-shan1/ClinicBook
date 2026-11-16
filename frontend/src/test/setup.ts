import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { cleanup } from '@testing-library/react'
import { server } from '../mocks/node'
import { clearTokens } from '../api/client'

// MSW intercepts network requests during tests so components can exercise the
// API client without a real backend.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
  clearTokens()
})
afterAll(() => server.close())
