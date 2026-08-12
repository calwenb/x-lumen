import { expect, test } from '@playwright/test'

// M02 认证冒烟（F-0101）：注册 → 登录后回到首页并显示用户名（用户可见语义）。
// 使用随机用户名避免与既有数据冲突；注册成功即建空间（决策 D9）。
// 注意：刷新令牌不持久化（FRONTEND.md §7），整页刷新后需重新登录，故守卫验证走 SPA 内导航。
test('注册后登录进入博客首页并显示用户名', async ({ page }) => {
  const username = `pw_${Date.now().toString(36)}`
  await page.goto('/login')

  // 切换到注册 tab
  await page.getByRole('tab', { name: '注册' }).click()
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('邮箱（可选）').fill(`${username}@test.local`)
  await page.getByLabel('密码').fill('Test123456')
  await page.getByRole('button', { name: '注册', exact: true }).click()

  // 注册成功即登录：回到首页（M03 起为 B01 最新文章）并显示用户名
  await expect(page.getByRole('heading', { name: '最新文章' })).toBeVisible()
  await expect(page.getByText(username, { exact: true })).toBeVisible()

  // 登出后回到登录入口
  await page.getByRole('button', { name: '登出' }).click()
  await expect(page.getByRole('link', { name: '登录' })).toBeVisible()

  // 未登录点击登录入口进入登录页
  await page.getByRole('link', { name: '登录' }).click()
  await expect(page.getByRole('heading', { name: '欢迎使用 xLumen' })).toBeVisible()
})
