import { expect, test } from '@playwright/test'

// 冒烟：首页可访问且展示知识列表标题（用户可见语义，不依赖内部实现；M03 起首页为 B01 全部知识库）
test('博客首页正常渲染', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '全部知识库' })).toBeVisible()
})
