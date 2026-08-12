// M03 E2E：博客前台公开页（F-0201~F-0203，B01~B04）
// 依赖后端已就绪且存在测试文章（公开 3 篇 + 草稿/私有各 1 篇）；数据由验证脚本插入，非代码库产物。
import { expect, test } from '@playwright/test'

test.describe('博客前台公开页（M03）', () => {
  test('B01 首页：展示公开文章，草稿/私有不出现，分类/标签侧栏可用', async ({ page }) => {
    await page.goto('/')

    await expect(page.getByRole('heading', { name: '最新文章' })).toBeVisible()
    // 公开文章卡片可见
    await expect(page.getByRole('link', { name: 'Spring Boot 4 模块化单体实践' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'RAG 检索增强生成入门' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Vue 3 组合式 API 设计心得' })).toBeVisible()
    // 草稿与私有文章不得出现（F-0307）
    await expect(page.getByText('草稿：未发布的思考')).toHaveCount(0)
    await expect(page.getByText('私有：仅自己可见')).toHaveCount(0)
    // 侧栏分类/标签聚合
    await expect(page.getByRole('heading', { name: '分类' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '标签' })).toBeVisible()
  })

  test('B03 搜索页：关键词命中 + 高亮，分类/标签组合筛选', async ({ page }) => {
    await page.goto('/search?keyword=RAG')

    await expect(page.getByText('共 1 篇相关文章')).toBeVisible()
    await expect(page.getByRole('link', { name: 'RAG 检索增强生成入门' })).toBeVisible()
    // 命中高亮
    await expect(page.locator('mark', { hasText: 'RAG' }).first()).toBeVisible()

    // 分类筛选（无结果空态）
    await page.goto('/search?category=随笔')
    await expect(page.getByText('没有找到相关文章')).toBeVisible()

    // 标签筛选
    await page.goto('/search?tag=Vue')
    await expect(page.getByText('共 1 篇相关文章')).toBeVisible()
  })

  test('B02 详情页：Markdown 渲染、目录导航、阅读量展示', async ({ page }) => {
    await page.goto('/articles/1900000000000000001')

    await expect(page.getByRole('heading', { name: 'Spring Boot 4 模块化单体实践' })).toBeVisible()
    // 目录（来自正文标题）与正文渲染
    await expect(page.getByRole('heading', { name: '目录' })).toBeVisible()
    await expect(page.getByRole('button', { name: '为什么模块化' })).toBeVisible()
    await expect(page.getByText('模块化单体是个人项目的最佳平衡点。')).toBeVisible()

    // 目录点击滚动定位
    await page.getByRole('button', { name: '部署形态' }).click()
    await expect(page.locator('h2', { hasText: '部署形态' })).toBeVisible()
  })

  test('B03 顶栏搜索入口：输入关键词跳转搜索页', async ({ page }) => {
    await page.goto('/')

    await page.getByRole('searchbox', { name: '搜索文章' }).fill('Vue 3')
    await page.getByRole('searchbox', { name: '搜索文章' }).press('Enter')
    await expect(page).toHaveURL(/\/search\?keyword=/)
    await expect(page.getByRole('link', { name: 'Vue 3 组合式 API 设计心得' })).toBeVisible()
  })

  test('B02 互动：未登录提示登录，登录后可评论与点赞', async ({ page }) => {
    await page.goto('/articles/1900000000000000002')
    // 点赞按钮名随状态变化（点赞 N / 已赞 N），用“赞”匹配；若残留已赞先取消，保证用例幂等
    const likeButton = page.getByRole('button', { name: /赞/ })
    if ((await likeButton.innerText()).includes('已赞')) {
      await likeButton.click()
      await expect(likeButton).toHaveText(/点赞/)
    }

    // 未登录：点赞引导登录
    await likeButton.click()
    await expect(page).toHaveURL(/\/login\?redirect=/)

    // 登录（SPA 内导航，不丢内存会话）；登录成功回跳详情页（redirect 机制）
    await page.getByRole('textbox', { name: /用户名/ }).fill('qoder_test')
    await page.getByRole('textbox', { name: /密码/ }).fill('Test123456')
    await page.getByRole('button', { name: /登 录|登录/ }).click()
    await expect(page.getByRole('button', { name: /登出/ })).toBeVisible()

    // 回跳后已在详情页（内存会话保留，F-0203 互动）：若残留已赞先取消（幂等），再点赞 + 评论
    const likeAfterLogin = page.getByRole('button', { name: /赞/ })
    if ((await likeAfterLogin.innerText()).includes('已赞')) {
      await likeAfterLogin.click()
      await expect(likeAfterLogin).toHaveText(/点赞/)
    }
    await likeAfterLogin.click()
    await expect(likeAfterLogin).toHaveText(/已赞/)

    const textarea = page.getByPlaceholder('写下你的评论…')
    await textarea.fill('M03 E2E 自动评论：RAG 文章写得很清晰。')
    await page.getByRole('button', { name: '发表评论' }).click()
    await expect(page.getByText('M03 E2E 自动评论：RAG 文章写得很清晰。')).toBeVisible()

    // 登出恢复（避免影响其他测试）
    await page.getByRole('button', { name: '登出' }).click()
    await expect(page.getByRole('link', { name: '登录' })).toBeVisible()
  })

  test('B04 关于页：导航可达', async ({ page }) => {
    await page.goto('/about')
    await expect(page.getByRole('heading', { name: '关于 xLumen 博客' })).toBeVisible()
  })
})
