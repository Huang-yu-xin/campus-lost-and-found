import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  timeout: 45_000,
  expect: { timeout: 8_000 },
  fullyParallel: true,
  workers: 2,
  retries: 0,
  reporter: [
    ['list'],
    [
      'json',
      { outputFile: process.env.RESPONSIVE_REPORT_PATH || 'test-results/responsive-results.json' },
    ],
  ],
  outputDir: process.env.RESPONSIVE_OUTPUT_DIR || 'test-results/artifacts',
  use: {
    baseURL: 'http://localhost:4173',
    viewport: { width: 1440, height: 900 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    actionTimeout: 8_000,
    isMobile: process.env.RESPONSIVE_TOUCH === '1',
    hasTouch: process.env.RESPONSIVE_TOUCH === '1',
    launchOptions: process.env.PLAYWRIGHT_EXECUTABLE_PATH
      ? { executablePath: process.env.PLAYWRIGHT_EXECUTABLE_PATH }
      : undefined,
  },
  projects: [
    {
      name: `${process.env.PLAYWRIGHT_CHANNEL || 'chromium'}${process.env.RESPONSIVE_TOUCH === '1' ? '-touch' : ''}`,
      use: {
        browserName: 'chromium',
        channel: process.env.PLAYWRIGHT_CHANNEL || undefined,
      },
    },
  ],
  webServer: {
    command: 'npm run preview -- --host localhost --port 4173 --strictPort',
    url: 'http://localhost:4173',
    reuseExistingServer: !process.env.CI,
  },
})
