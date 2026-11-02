import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// When mocking is disabled, dev requests to /api can be proxied to the backend.
// const API_TARGET = process.env.VITE_API_URL ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // proxy: { '/api': { target: API_TARGET, changeOrigin: true } },
  },
})
