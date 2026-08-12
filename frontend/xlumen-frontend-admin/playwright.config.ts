import { defineConfig, devices } from '@playwright/test'

// 端到端测试（FRONTEND.md §14）：操作用户可见语义，不依赖 CSS 类名；自动拉起 dev server
export default defineConfig({
  testDir: './e2e',
  timeout: 30000,
  use: {
    baseURL: 'http://localhost:5174',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5174',
    reuseExistingServer: !process.env.CI,
    timeout: 120000,
  },
})
