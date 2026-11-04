import { defineConfig } from '@playwright/test'

// The end-to-end test needs the backend running, with its database and Ollama.
// Playwright starts the frontend dev server itself, on its own port, and the
// dev server forwards API calls to BACKEND_URL (default http://localhost:8080).
const port = Number(process.env.E2E_PORT ?? 5180)

export default defineConfig({
  testDir: './e2e',
  // Generous: the test waits for a local language model to answer.
  timeout: 240_000,
  reporter: 'list',
  use: {
    baseURL: `http://localhost:${port}`,
    // Your installed Google Chrome, so Playwright doesn't download a browser.
    channel: 'chrome',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: `npm run dev -- --port ${port} --strictPort`,
    url: `http://localhost:${port}`,
    reuseExistingServer: false,
    timeout: 60_000,
  },
})
