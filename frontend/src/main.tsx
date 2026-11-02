import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App.tsx'
import { enableMocking } from './mocks/browser'
import './index.css'

// Start MSW (mock data) before rendering so the first requests are intercepted.
// finally() keeps the app usable even if the service worker fails to start.
enableMocking().finally(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <App />
    </StrictMode>,
  )
})
