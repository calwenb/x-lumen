import { expect, test } from '@playwright/test'

// 骨架冒烟：首页可访问且展示标题（用户可见语义，不依赖内部实现）
test('博客首页正常渲染', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'xLumen 博客' })).toBeVisible()
})
