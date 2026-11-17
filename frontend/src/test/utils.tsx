import type { ReactElement } from 'react'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from '../features/auth/AuthContext'
import { setTokens } from '../api/client'
import type { UserResponse } from '../api/types'

export function renderWithProviders(
  ui: ReactElement,
  { initialEntries = ['/'], user }: { initialEntries?: string[]; user?: UserResponse | null } = {},
) {
  // `initialUser` skips the refresh bootstrap, but the API client still needs a
  // matching Bearer token so mock handlers can resolve the caller's role.
  if (user) setTokens(`mock-access-token-${user.id}`, `mock-refresh-token-${user.id}`)

  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={initialEntries}>
        <AuthProvider initialUser={user}>{ui}</AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}
