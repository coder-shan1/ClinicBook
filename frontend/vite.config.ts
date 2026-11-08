import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Backend defaults (backend/src/main/resources/application.yml): port 8080, no
// servlet context-path. API endpoints live under /api/v1 (ARCHITECTURE.md §7), so
// the proxy forwards /api to the backend without rewriting the path.
// Override with the BACKEND_URL env var (used by the dev server only).
const BACKEND_URL = process.env.BACKEND_URL ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: BACKEND_URL,
        changeOrigin: true,
      },
    },
  },
})
