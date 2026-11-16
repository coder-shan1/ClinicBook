import { describe, it, expect } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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

  it('filters doctors by name search query', async () => {
    const user = userEvent.setup()
    renderWithProviders(<DoctorsPage />)

    expect(await screen.findByText(/Dr\. Ada Okonkwo/i)).toBeInTheDocument()

    const searchInput = screen.getByLabelText(/search by name or keyword/i)
    await user.type(searchInput, 'Mateo')
    await user.click(screen.getByRole('button', { name: /search/i }))

    await waitFor(() => {
      expect(screen.getByText(/Dr\. Mateo Silva/i)).toBeInTheDocument()
      expect(screen.queryByText(/Dr\. Ada Okonkwo/i)).not.toBeInTheDocument()
    })
  })

  it('filters doctors by specialty dropdown', async () => {
    const user = userEvent.setup()
    renderWithProviders(<DoctorsPage />)

    expect(await screen.findByText(/Dr\. Ada Okonkwo/i)).toBeInTheDocument()

    const specialtySelect = screen.getByLabelText(/specialty/i)
    // Select Dermatology (id 2)
    await user.selectOptions(specialtySelect, '2')

    await waitFor(() => {
      expect(screen.getByText(/Dr\. Mateo Silva/i)).toBeInTheDocument()
      expect(screen.queryByText(/Dr\. Priya Nair/i)).not.toBeInTheDocument()
    })
  })

  it('displays empty state when no doctors match the search', async () => {
    const user = userEvent.setup()
    renderWithProviders(<DoctorsPage />)

    expect(await screen.findByText(/Dr\. Ada Okonkwo/i)).toBeInTheDocument()

    const searchInput = screen.getByLabelText(/search by name or keyword/i)
    await user.type(searchInput, 'NonexistentNameXYZ')
    await user.click(screen.getByRole('button', { name: /search/i }))

    expect(
      await screen.findByText(/no doctors match your search criteria/i),
    ).toBeInTheDocument()
  })
})
