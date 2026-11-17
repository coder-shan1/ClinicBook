import { setupWorker } from 'msw/browser'
import { handlers } from './handlers'

export const worker = setupWorker(...handlers)

/**
 * Start MSW in the browser only when mocking is explicitly enabled.
 *
 * The backend implements auth, doctors, schedule and booking, so the default is to
 * talk to the real API (proxied by Vite). Set `VITE_API_MOCKING=enabled` to run the
 * UI without a backend — MSW then serves realistic data, including the endpoints
 * the backend has not built yet (reviews, admin dashboard).
 */
export async function enableMocking(): Promise<void> {
  if (import.meta.env.VITE_API_MOCKING !== 'enabled') return
  await worker.start({
    onUnhandledRequest: 'bypass',
    serviceWorker: { url: '/mockServiceWorker.js' },
  })
}
