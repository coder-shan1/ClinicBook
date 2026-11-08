import { setupWorker } from 'msw/browser'
import { handlers } from './handlers'

export const worker = setupWorker(...handlers)

/**
 * Start MSW in the browser unless mocking is explicitly disabled.
 * Defaults to enabled (Phase 0 has no backend yet). Set VITE_API_MOCKING=disabled
 * to hit the real backend.
 */
export async function enableMocking(): Promise<void> {
  if (import.meta.env.VITE_API_MOCKING === 'disabled') return
  await worker.start({
    onUnhandledRequest: 'bypass',
    serviceWorker: { url: '/mockServiceWorker.js' },
  })
}
