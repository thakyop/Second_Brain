/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The Spring Boot backend. The dev server forwards /api and /actuator to it,
// so the browser only ever talks to one origin and the backend needs no CORS
// setup. Set BACKEND_URL to use a backend on another port.
const backendUrl = process.env.BACKEND_URL ?? 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': backendUrl,
      '/actuator': backendUrl,
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // Only the unit tests; the Playwright tests in e2e/ run with `npm run test:e2e`.
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
