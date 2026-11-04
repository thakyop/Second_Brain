import { expect, test } from '@playwright/test'
import { fileURLToPath } from 'node:url'

const notesFile = fileURLToPath(new URL('./fixtures/notes.md', import.meta.url))

// Runs the whole flow against the real backend. Each run signs up a new
// account, so it doesn't depend on earlier runs.
test('sign up, create a workspace, upload a document, watch it become ready and ask about it', async ({ page }) => {
  const email = `e2e-${Date.now()}@example.com`
  const password = 'e2e-password-123'

  await test.step('sign up', async () => {
    await page.goto('/register')
    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Password').fill(password)
    await page.getByRole('button', { name: 'Create account' }).click()

    await expect(page.getByRole('heading', { name: 'Workspaces' })).toBeVisible()
    await expect(page.getByText('No workspaces yet')).toBeVisible()
  })

  await test.step('create a workspace', async () => {
    await page.getByLabel('Workspace name').fill('E2E notes')
    await page.getByRole('button', { name: 'Create workspace' }).click()

    await expect(page.getByRole('heading', { name: 'E2E notes' })).toBeVisible()
    await expect(page.getByText('No documents yet').first()).toBeVisible()
  })

  await test.step('upload a Markdown file and wait until it is ready', async () => {
    await page.getByLabel('Choose files to upload').setInputFiles(notesFile)

    const row = page.getByRole('listitem').filter({ hasText: 'notes.md' })
    await expect(row).toBeVisible()
    // Extracting, chunking and embedding with Ollama takes a few seconds.
    await expect(row.getByText('Ready')).toBeVisible({ timeout: 60_000 })
    await expect(page.getByText('1 document · 1 ready')).toBeVisible()
  })

  await test.step('refuse an unsupported file before sending it', async () => {
    await page.getByLabel('Choose files to upload').setInputFiles({
      name: 'data.json',
      mimeType: 'application/json',
      buffer: Buffer.from('{}'),
    })

    await expect(page.getByRole('alert')).toContainText('data.json: Only PDF, .txt and .md files can be uploaded.')
  })

  await test.step('ask a question about the notes', async () => {
    await page.getByRole('link', { name: 'Chat' }).click()
    await page.getByLabel('Your question').fill('What is round-robin scheduling?')
    await page.getByLabel('Your question').press('Enter')
    await expect(page.getByText('Thinking…')).toBeVisible()

    // The model's wording varies, but its sources don't. A local model can
    // take a while, especially when it first loads.
    await expect(page.locator('.source-list').getByText('notes.md')).toBeVisible({ timeout: 150_000 })
    await expect(page.getByRole('complementary', { name: 'Your chats' })
      .getByText('What is round-robin scheduling?')).toBeVisible()
  })

  await test.step('signing up again with the same email shows the real error', async () => {
    await page.getByRole('button', { name: 'Log out' }).click()
    await expect(page.getByRole('button', { name: 'Log in' })).toBeVisible()

    await page.goto('/register')
    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Password').fill(password)
    await page.getByRole('button', { name: 'Create account' }).click()

    // 409 from the backend. Before the SecurityConfig fix this was a bare 401.
    await expect(page.getByRole('alert')).toContainText('That email is already registered.')
  })

  await test.step('a wrong password is rejected', async () => {
    await page.getByRole('link', { name: 'Log in instead' }).click()
    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Password').fill('not-the-password')
    await page.getByRole('button', { name: 'Log in' }).click()

    await expect(page.getByRole('alert')).toContainText('Wrong email or password.')
  })

  await test.step('logging in again shows the workspace and its document', async () => {
    await page.getByLabel('Password').fill(password)
    await page.getByRole('button', { name: 'Log in' }).click()

    await page.getByRole('link', { name: /E2E notes/ }).click()
    const row = page.getByRole('listitem').filter({ hasText: 'notes.md' })
    await expect(row.getByText('Ready')).toBeVisible()
  })
})
