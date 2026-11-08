import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/utils'
import DoctorsPage from '../../pages/DoctorsPage'

describe('DoctorsPage', () => {
  it('shows a loading state then renders doctors from the mock API', async () => {
    renderWithProviders(<DoctorsPage />)

    // Loading state renders first (TanStack Query is fetching).
    expect(screen.getByRole('status')).toBeInTheDocument()

    // findBy* waits for the mocked /doctors response to resolve.
    expect(await screen.findByText(/Dr\. Ada Okonkwo/i)).toBeInTheDocument()
    expect(screen.getByText(/Dr\. Mateo Silva/i)).toBeInTheDocument()
    expect(screen.getByText(/Dr\. Priya Nair/i)).toBeInTheDocument()
  })
})
