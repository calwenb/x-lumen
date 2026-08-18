import { expect, test } from '@playwright/test'

// IDEAS 批次验收（F-0212/F-0213/F-0214/F-0312）：创作中心主导航、知识赞/踩互斥与收藏、
// 个人收藏页、评论点赞、目录树右键菜单。走用户可见语义；互动目标取公开列表第一篇已发布知识。
test('互动与导航增强端到端验收', async ({ page }) => {
  test.setTimeout(90000)
  const username = `pw_enh_${Date.now().toString(36)}`

  // --- 注册即登录（F-0101），注册成功回到首页 ---
  await page.goto('/login')
  await page.getByRole('tab', { name: '注册' }).click()
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码').fill('Test123456')
  await page.getByRole('button', { name: '注册', exact: true }).click()
  await expect(page.getByRole('heading', { name: '全部知识库' })).toBeVisible()

  // --- F-0214：主导航出现「创作中心」，点击进入创作工作台 ---
  await page.getByRole('navigation').getByRole('link', { name: '创作中心' }).click()
  await expect(page).toHaveURL(/\/studio/)

  // --- F-0212：头像下拉「我的收藏」进入收藏页，初始为空态 ---
  await page.getByRole('button', { name: `${username} 账号菜单` }).click()
  await page.getByRole('menuitem', { name: '我的收藏' }).click()
  await expect(page).toHaveURL(/\/favorites/)
  await expect(page.getByText(/还没有收藏|去知识库逛逛|空/).first()).toBeVisible()

  // --- F-0212：公开知识详情页 赞/踩互斥 + 收藏 toggle ---
  const list = await page.request.get('/api/v1/public/knowledge?pageNo=1&pageSize=1')
  const listData = ((await list.json()) as { data: { records: { id: string; title: string }[] } })
    .data
  const knowledgeId = listData.records[0]!.id
  const knowledgeTitle = listData.records[0]!.title
  await page.goto(`/knowledge/${knowledgeId}`)

  const like = page.getByRole('button', { name: /赞 \d+/ })
  const dislike = page.getByRole('button', { name: /踩 \d+/ })
  const favorite = page.getByRole('button', { name: /收藏 \d+/ })
  await expect(like).toContainText('赞')
  await expect(dislike).toContainText('踩')
  await expect(favorite).toContainText('收藏')
  // 跨用户计数随多次运行累积，断言一律按「基线 + 增量」（列表首篇可能有历史赞踩/收藏）
  const num = (t: string): number => Number(t.match(/\d+/)?.[0] ?? '0')
  const baseLike = num(await like.innerText())
  const baseDislike = num(await dislike.innerText())
  const baseFav = num(await favorite.innerText())

  // 点赞 -> 计数 +1 且高亮；再点踩 -> 互斥切换（赞回落基线、踩 +1 且高亮）；再点踩 -> 取消
  await like.click()
  await expect(like).toHaveAttribute('aria-pressed', 'true')
  await expect(like).toHaveText(new RegExp(`赞 ${baseLike + 1}`))
  await dislike.click()
  await expect(like).not.toHaveAttribute('aria-pressed', 'true')
  await expect(like).toHaveText(new RegExp(`赞 ${baseLike}`))
  await expect(dislike).toHaveAttribute('aria-pressed', 'true')
  await expect(dislike).toHaveText(new RegExp(`踩 ${baseDislike + 1}`))
  await dislike.click()
  await expect(dislike).not.toHaveAttribute('aria-pressed', 'true')
  await expect(dislike).toHaveText(new RegExp(`踩 ${baseDislike}`))

  // 收藏 -> 计数 +1，收藏页出现该知识；取消收藏 -> 列表移除
  await favorite.click()
  await expect(favorite).toHaveText(new RegExp(`收藏 ${baseFav + 1}`))
  await page.getByRole('button', { name: `${username} 账号菜单` }).click()
  await page.getByRole('menuitem', { name: '我的收藏' }).click()
  await expect(page.getByRole('link', { name: knowledgeTitle }).first()).toBeVisible()
  await page.getByRole('button', { name: /取消收藏/ }).first().click()
  await expect(page.getByText(/还没有收藏|去知识库逛逛|空/).first()).toBeVisible()

  // --- F-0213：发表评论后点赞该评论 ---
  await page.goto(`/knowledge/${knowledgeId}`)
  const content = `e2e 评论 ${Date.now().toString(36)}`
  await page.getByPlaceholder('写下你的评论…').fill(content)
  await page.getByRole('button', { name: '发表评论' }).click()
  const commentItem = page.locator('.comment-item', { hasText: content })
  await expect(commentItem).toBeVisible()
  // 按钮内 emoji 为 aria-hidden，无障碍名只有计数，改用 aria-pressed 定位（第一条为点赞）
  const commentLike = commentItem.locator('button[aria-pressed]').first()
  await commentLike.click()
  await expect(commentLike).toHaveText(/[1-9]\d*/)
  await expect(commentLike).toHaveAttribute('aria-pressed', 'true')

  // --- F-0312：首页左栏目录树右键菜单（新用户需先有知识库；注册不自动建库，测试经 API 建一个）---
  const loginResp = await page.request.post('/api/v1/auth/login', {
    data: { username, password: 'Test123456' },
  })
  const token = ((await loginResp.json()) as { data: { accessToken: string } }).data.accessToken
  await page.request.post('/api/v1/knowledge-bases', {
    headers: { Authorization: `Bearer ${token}` },
    data: { name: `${username} 的库`, visibility: 1 },
  })
  await page.goto('/')
  // 库切换器（初始 scope 为全部知识库），切换到自己的库
  await page.getByRole('button', { name: '全部知识库' }).click()
  await page.getByRole('menuitem', { name: `${username} 的库` }).click()
  const dirCard = page.locator('section.side-card').filter({ hasText: '目录' })
  await expect(dirCard).toBeVisible()

  // 树根右键 -> 新增根目录（el-dialog：目录名 + 保存）
  const dirName = `e2e目录${Date.now().toString(36).slice(-5)}`
  await dirCard.click({ button: 'right' })
  await page.getByRole('button', { name: '新增根目录' }).click()
  await page.getByLabel('目录名').fill(dirName)
  await page.getByRole('button', { name: '保存' }).click()
  await expect(dirCard.getByRole('button', { name: dirName })).toBeVisible()

  // 节点右键 -> 重命名
  await dirCard.getByRole('button', { name: dirName }).click({ button: 'right' })
  await page.getByRole('button', { name: '重命名' }).click()
  const renamed = `${dirName}改`
  await page.getByLabel('目录名').fill(renamed)
  await page.getByRole('button', { name: '保存' }).click()
  await expect(dirCard.getByRole('button', { name: renamed })).toBeVisible()

  // 节点右键 -> 删除（先点菜单「删除」，再确认 ElMessageBox 的「删除」按钮）
  await dirCard.getByRole('button', { name: renamed }).click({ button: 'right' })
  await page.getByRole('button', { name: '删除', exact: true }).click()
  const confirmBox = page.locator('.el-message-box')
  await expect(confirmBox).toBeVisible()
  await confirmBox.getByRole('button', { name: '删除' }).click()
  await expect(dirCard.getByRole('button', { name: renamed })).toHaveCount(0)
})
