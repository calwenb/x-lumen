import { expect, test } from '@playwright/test'

// 骨架冒烟：管理后台首页可访问且展示标题（用户可见语义，不依赖内部实现）
test('管理后台首页正常渲染', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'xLumen 管理后台' })).toBeVisible()
})
