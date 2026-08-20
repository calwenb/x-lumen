import { expect, test } from '@playwright/test'

const success = (data: unknown) => ({ code: 'SUCCESS', message: 'ok', data, requestId: 'e2e' })

test('新增知识可直接发布并在 AI 建议确认后继续', async ({ page }) => {
  let savedPayload: Record<string, unknown> | null = null
  let publishPayload: Record<string, unknown> | null = null
  const knowledge = {
    id: '2091000000000000001',
    title: '发布链路回归测试',
    content: '# 正文\n\n需要审核的内容',
    kbId: '2091000000000000100',
    directoryId: '2091000000000000200',
    tags: ['发布', '回归'],
    status: 2,
    version: '1',
    viewCount: '0',
    createdAt: '2026-08-20T17:00:00',
    updatedAt: '2026-08-20T17:00:00',
  }

  await page.addInitScript(() => {
    localStorage.setItem(
      'xlumen.session',
      JSON.stringify({
        snapshot: {
          userId: '2091000000000000000',
          username: 'qa_publish',
          workspaceId: '2091000000000000999',
          roles: ['OWNER'],
        },
        accessToken: 'e2e-access-token',
      }),
    )
  })

  await page.route('**/api/v1/knowledge-bases', async (route) => {
    await route.fulfill({
      json: success([
        {
          id: knowledge.kbId,
          workspaceId: '2091000000000000999',
          name: '发布测试库',
          intro: '',
          cover: '',
          visibility: 1,
          knowledgeCount: '0',
          createdAt: knowledge.createdAt,
          updatedAt: knowledge.updatedAt,
        },
      ]),
    })
  })
  await page.route(`**/api/v1/knowledge-bases/${knowledge.kbId}/directories`, async (route) => {
    await route.fulfill({
      json: success([
        {
          id: knowledge.directoryId,
          kbId: knowledge.kbId,
          parentId: '0',
          name: '发布目录',
          knowledgeCount: '0',
          children: [],
        },
      ]),
    })
  })
  await page.route('**/api/v1/knowledge**', async (route) => {
    if (route.request().url().includes('/knowledge-bases')) {
      await route.fallback()
      return
    }
    if (route.request().method() === 'POST' || route.request().method() === 'PUT') {
      savedPayload = route.request().postDataJSON() as Record<string, unknown>
      await route.fulfill({ json: success({ ...knowledge, ...savedPayload, id: knowledge.id }) })
      return
    }
    await route.fulfill({
      json: success({ total: '1', pageNo: '1', pageSize: '20', records: [knowledge] }),
    })
  })
  await page.route('**/api/v1/reviews/auto', async (route) => {
    await route.fulfill({
      json: success({
        id: '2091000000000000300',
        knowledgeId: knowledge.id,
        knowledgeTitle: knowledge.title,
        version: '1',
        status: 'APPROVED',
        aiTaskId: '2091000000000000400',
        aiResultJson: JSON.stringify([
          {
            severity: 'warning',
            position: '正文第 2 段',
            evidence: '表述略显含糊',
            suggestion: '补充具体适用范围',
          },
        ]),
        aiTaskStatus: 'SUCCEEDED',
        autoDecision: 'READY',
        aiErrorMessage: '',
        rejectReason: '',
        rejectPosition: '',
        rejectExpectation: '',
        createdAt: knowledge.createdAt,
        updatedAt: knowledge.updatedAt,
      }),
    })
  })
  await page.route('**/api/v1/reviews/*/publish', async (route) => {
    publishPayload = (route.request().postDataJSON() ?? {}) as Record<string, unknown>
    await route.fulfill({ json: success({ id: '2091000000000000500', status: 'PUBLISHED' }) })
  })

  await page.goto('/studio/knowledge/new')
  await expect(page.getByRole('heading', { name: '新建知识' })).toBeVisible()
  await page.getByLabel('知识标题').fill(knowledge.title)
  await page.getByRole('combobox', { name: '所属知识库' }).click({ force: true })
  await page.getByRole('option', { name: '发布测试库' }).click()
  await page.getByRole('combobox', { name: '所属目录' }).click({ force: true })
  await page.getByRole('option', { name: '发布目录' }).click()
  await page.getByLabel('标签').fill('发布, 回归')
  await page.getByLabel('正文编辑区').fill(knowledge.content)

  await page.getByRole('button', { name: '发布', exact: true }).click()
  const firstConfirm = page.getByRole('dialog', { name: '确认发布' })
  await expect(firstConfirm).toContainText('系统会先自动进行 AI 审核')
  await expect(firstConfirm).toContainText('立即发布并公开可见')
  await firstConfirm.getByRole('button', { name: '确认并开始审核' }).click()

  const adviceConfirm = page.getByRole('dialog', { name: '发布前提示' })
  await expect(adviceConfirm).toContainText('正文第 2 段')
  await expect(adviceConfirm).toContainText('表述略显含糊')
  await expect(adviceConfirm).toContainText('补充具体适用范围')
  await adviceConfirm.getByRole('button', { name: '确认发布' }).click()

  await expect(page).toHaveURL(/\/studio\/knowledge$/)
  expect(savedPayload).toMatchObject({
    title: knowledge.title,
    content: knowledge.content,
    kbId: knowledge.kbId,
    directoryId: knowledge.directoryId,
    tags: knowledge.tags,
  })
  expect(publishPayload).toEqual({})
})
