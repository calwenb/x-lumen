<script setup lang="ts">
// 审核中心（B12，F-0902/F-0904）：审核列表（状态筛选）+ 详情（AI 审校问题 + 通过/驳回/发布）。
// 关键状态：加载骨架、空态、失败重试、409 冲突恢复、429 提示（PROTOTYPE §11）。
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { createRelease } from '@/modules/publishing/api/release'
import {
  approveReview,
  fetchReview,
  fetchReviews,
  parseReviewIssues,
  rejectReview,
} from '@/modules/publishing/api/review'
import Pagination from '@/modules/publishing/components/Pagination.vue'

import type { ReviewVO } from '@/modules/publishing/api/review'

const PAGE_SIZE = 10

const STATUS_OPTIONS: ReadonlyArray<{ value: string; label: string }> = [
  { value: '', label: '全部状态' },
  { value: 'PENDING', label: '待审核' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
]

const STATUS_LABELS: Record<string, string> = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
}

const SEVERITY_LABELS: Record<string, string> = {
  error: '错误',
  warning: '警告',
  info: '提示',
}

const filterStatus = ref('')
const reviews = ref<ReviewVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const loading = ref(true)
const loadError = ref(false)

const selectedId = ref<string | null>(null)
const selected = ref<ReviewVO | null>(null)
const detailLoading = ref(false)
const detailError = ref(false)

const rejectReason = ref('')
const rejectPosition = ref('')
const rejectExpectation = ref('')
const actionError = ref('')
const actionMessage = ref('')
const acting = ref(false)

const issues = computed(() =>
  selected.value ? parseReviewIssues(selected.value.aiResultJson) : [],
)

function formatTime(iso: string): string {
  return iso.slice(0, 16).replace('T', ' ')
}

function isConflict(error: unknown): boolean {
  return error instanceof Error && (error.message.includes('冲突') || error.message.includes('409'))
}

function isRateLimited(error: unknown): boolean {
  return error instanceof Error && error.message.includes('429')
}

async function load(targetPage: number): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    const page = await fetchReviews({
      ...(filterStatus.value
        ? { status: filterStatus.value as 'PENDING' | 'APPROVED' | 'REJECTED' }
        : {}),
      pageNo: targetPage,
      pageSize: PAGE_SIZE,
    })
    reviews.value = page.records
    total.value = page.total
    pageNo.value = targetPage
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function applyFilters(): void {
  void load(1)
}

async function select(id: string): Promise<void> {
  if (selectedId.value === id) {
    closeDetail()
    return
  }
  selectedId.value = id
  detailLoading.value = true
  detailError.value = false
  actionError.value = ''
  actionMessage.value = ''
  rejectReason.value = ''
  rejectPosition.value = ''
  rejectExpectation.value = ''
  try {
    selected.value = await fetchReview(id)
  } catch {
    detailError.value = true
    selected.value = null
  } finally {
    detailLoading.value = false
  }
}

function closeDetail(): void {
  selectedId.value = null
  selected.value = null
}

async function approve(): Promise<void> {
  if (!selected.value || acting.value) return
  try {
    await ElMessageBox.confirm(`确认通过「${selected.value.knowledgeTitle}」的审核？`, '通过审核', {
      confirmButtonText: '通过',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    // 用户取消
    return
  }
  acting.value = true
  actionError.value = ''
  actionMessage.value = ''
  try {
    await approveReview(selected.value.id, selected.value.version)
    actionMessage.value = '已通过'
    ElMessage.success('已通过审核')
    await load(pageNo.value)
    closeDetail()
  } catch (error) {
    if (isConflict(error)) {
      actionError.value = '该审核已被处理（版本冲突），列表已刷新'
      await load(pageNo.value)
      closeDetail()
    } else {
      actionError.value = isRateLimited(error) ? '操作过于频繁，请稍后再试' : '操作失败，请稍后重试'
    }
  } finally {
    acting.value = false
  }
}

/** 发布已通过知识（BUG-007 补全 F-0904 流程入口）：立即发布，公开可见。 */
async function publish(): Promise<void> {
  if (!selected.value || acting.value) return
  try {
    await ElMessageBox.confirm(
      `确认发布「${selected.value.knowledgeTitle}」？发布后将在公开/私有库中可见。`,
      '发布知识',
      { confirmButtonText: '发布', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    // 用户取消
    return
  }
  acting.value = true
  actionError.value = ''
  actionMessage.value = ''
  try {
    await createRelease({
      knowledgeId: selected.value.knowledgeId,
      version: selected.value.version,
    })
    actionMessage.value = '已发布'
    ElMessage.success('已发布，公开可见')
    await load(pageNo.value)
    closeDetail()
  } catch (error) {
    if (isConflict(error)) {
      actionError.value = '发布冲突（该版本已发布或知识状态已变化），列表已刷新'
      await load(pageNo.value)
      closeDetail()
    } else {
      actionError.value = isRateLimited(error) ? '操作过于频繁，请稍后再试' : '操作失败，请稍后重试'
    }
  } finally {
    acting.value = false
  }
}

async function reject(): Promise<void> {
  if (!selected.value || acting.value) return
  const reason = rejectReason.value.trim()
  const position = rejectPosition.value.trim()
  const expectation = rejectExpectation.value.trim()
  if (!reason || !position || !expectation) {
    actionError.value = '驳回需填写原因、位置、期望修改三项内容'
    return
  }
  try {
    await ElMessageBox.confirm('确认驳回该审核？', '驳回审核', {
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    // 用户取消
    return
  }
  acting.value = true
  actionError.value = ''
  actionMessage.value = ''
  try {
    await rejectReview(selected.value.id, {
      version: selected.value.version,
      reason,
      position,
      expectation,
    })
    actionMessage.value = '已驳回'
    ElMessage.success('已驳回审核')
    await load(pageNo.value)
    closeDetail()
  } catch (error) {
    if (isConflict(error)) {
      actionError.value = '该审核已被处理（版本冲突），列表已刷新'
      await load(pageNo.value)
      closeDetail()
    } else {
      actionError.value = isRateLimited(error) ? '操作过于频繁，请稍后再试' : '操作失败，请稍后重试'
    }
  } finally {
    acting.value = false
  }
}

onMounted(() => {
  void load(1)
})
</script>

<template>
  <main class="review-center">
    <header class="review-center__header">
      <h1 class="review-center__title">审核中心</h1>
      <p class="review-center__intro">双闸门审核：先看「小光」的 AI 审校意见，再人工通过或驳回。</p>
    </header>

    <div class="review-center__filters">
      <el-select v-model="filterStatus" class="review-center__select" aria-label="状态筛选">
        <el-option
          v-for="option in STATUS_OPTIONS"
          :key="option.value"
          :value="option.value"
          :label="option.label"
        />
      </el-select>
      <el-button type="primary" plain @click="applyFilters">筛选</el-button>
    </div>

    <div v-if="loading" class="review-center__state">
      <el-skeleton :rows="4" animated />
    </div>
    <div v-else-if="loadError" class="review-center__state">
      <p>加载失败，请稍后重试</p>
      <el-button type="primary" plain size="small" @click="load(pageNo)">重试</el-button>
    </div>
    <div v-else-if="reviews.length === 0" class="review-center__state">暂无审核记录。</div>
    <template v-else>
      <ul class="review-center__list">
        <li v-for="review in reviews" :key="review.id" class="review-item">
          <button
            type="button"
            class="review-item__main"
            :class="{ 'review-item__main--active': selectedId === review.id }"
            @click="select(review.id)"
          >
            <span class="review-item__title">{{ review.knowledgeTitle }}</span>
            <span class="review-item__meta">
              <el-tag
                :type="
                  review.status === 'PENDING'
                    ? 'warning'
                    : review.status === 'APPROVED'
                      ? 'success'
                      : 'danger'
                "
                size="small"
                effect="light"
              >
                {{ STATUS_LABELS[review.status] ?? review.status }}
              </el-tag>
              <span>v{{ review.version }}</span>
              <span>{{ formatTime(review.updatedAt) }}</span>
            </span>
          </button>
        </li>
      </ul>
      <Pagination :page-no="pageNo" :page-size="PAGE_SIZE" :total="total" @change="load" />
    </template>

    <section v-if="selectedId" class="review-detail">
      <div v-if="detailLoading" class="review-detail__state">详情加载中…</div>
      <div v-else-if="detailError" class="review-detail__state">
        详情加载失败
        <button type="button" class="review-center__retry" @click="select(selectedId)">重试</button>
      </div>
      <template v-else-if="selected">
        <header class="review-detail__header">
          <h2 class="review-detail__title">{{ selected.knowledgeTitle }}</h2>
          <button type="button" class="review-detail__close" @click="closeDetail">收起</button>
        </header>

        <h3 class="review-detail__subtitle">AI 审校问题（{{ issues.length }}）</h3>
        <p v-if="issues.length === 0" class="review-detail__hint">暂无 AI 审校问题。</p>
        <ul v-else class="review-detail__issues">
          <li
            v-for="(issue, index) in issues"
            :key="index"
            class="review-issue"
            :class="`review-issue--${issue.severity}`"
          >
            <div class="review-issue__head">
              <span class="review-issue__severity">{{
                SEVERITY_LABELS[issue.severity] ?? issue.severity
              }}</span>
              <span v-if="issue.position" class="review-issue__position">{{ issue.position }}</span>
            </div>
            <p v-if="issue.evidence" class="review-issue__evidence">原文：{{ issue.evidence }}</p>
            <p v-if="issue.suggestion" class="review-issue__suggestion">
              建议：{{ issue.suggestion }}
            </p>
          </li>
        </ul>

        <div v-if="selected.status === 'REJECTED'" class="review-detail__reject-info">
          <p><strong>驳回原因：</strong>{{ selected.rejectReason }}</p>
          <p><strong>驳回位置：</strong>{{ selected.rejectPosition }}</p>
          <p><strong>期望修改：</strong>{{ selected.rejectExpectation }}</p>
        </div>

        <div v-if="selected.status === 'APPROVED'" class="review-detail__actions">
          <el-button type="success" :loading="acting" @click="publish">
            {{ acting ? '处理中' : '发布' }}
          </el-button>
          <p class="review-detail__hint">已通过审核，发布后知识在所属库中公开可见（F-0904）。</p>
        </div>

        <div v-if="selected.status === 'PENDING'" class="review-detail__actions">
          <el-button type="primary" :loading="acting" @click="approve">
            {{ acting ? '处理中' : '通过' }}
          </el-button>

          <form class="review-detail__reject-form" @submit.prevent="reject">
            <label class="review-detail__field">
              <span class="review-detail__label">驳回原因 *</span>
              <textarea
                v-model="rejectReason"
                class="review-detail__textarea"
                rows="2"
                placeholder="例如：结论与正文矛盾"
              />
            </label>
            <label class="review-detail__field">
              <span class="review-detail__label">位置 *</span>
              <input
                v-model="rejectPosition"
                class="review-detail__input"
                type="text"
                placeholder="例如：第二节「小结」段落"
              />
            </label>
            <label class="review-detail__field">
              <span class="review-detail__label">期望修改 *</span>
              <textarea
                v-model="rejectExpectation"
                class="review-detail__textarea"
                rows="2"
                placeholder="例如：补充数据来源并修正结论"
              />
            </label>
            <el-button type="danger" plain :loading="acting" native-type="submit">驳回</el-button>
          </form>
        </div>

        <p v-if="actionMessage" class="review-detail__message" role="status">{{ actionMessage }}</p>
        <p v-if="actionError" class="review-detail__action-error" role="alert">{{ actionError }}</p>
      </template>
    </section>
  </main>
</template>

<style scoped>
.review-center {
  max-width: 960px;
  margin: 0 auto;
  padding: 32px 20px 64px;
}

.review-center__header {
  margin-bottom: 20px;
}

.review-center__title {
  margin: 0;
  font-size: 24px;
}

.review-center__intro {
  margin: 8px 0 0;
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.review-center__filters {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.review-center__select {
  width: 140px;
}

.review-center__state {
  padding: 48px 0;
  text-align: center;
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.review-center__state :deep(.el-skeleton) {
  text-align: left;
}

.review-center__list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.review-item__main {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
  padding: 14px 18px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
  color: var(--xl-text-primary);
  text-align: left;
  cursor: pointer;
  transition:
    box-shadow var(--xl-transition),
    transform var(--xl-transition),
    border-color var(--xl-transition);
}

.review-item__main:hover {
  box-shadow: var(--xl-shadow-md);
  transform: translateY(-1px);
}

.review-item__main--active {
  border-color: var(--xl-color-primary);
}

.review-item__title {
  font-size: 15px;
  font-weight: 600;
}

.review-item__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  color: var(--xl-text-secondary);
  font-size: 12px;
}

.review-detail {
  margin-top: 24px;
  padding: 20px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
}

.review-detail__state {
  padding: 32px 0;
  text-align: center;
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.review-detail__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.review-detail__title {
  margin: 0;
  font-size: 18px;
}

.review-detail__close {
  border: none;
  background: none;
  color: var(--xl-text-secondary);
  font-size: 13px;
  cursor: pointer;
}

.review-detail__subtitle {
  margin: 0 0 10px;
  font-size: 15px;
}

.review-detail__hint {
  color: var(--xl-text-secondary);
  font-size: 13px;
}

.review-detail__issues {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 0 0 16px;
  padding: 0;
  list-style: none;
}

.review-issue {
  padding: 12px 14px;
  border: 1px solid var(--xl-border);
  border-left-width: 3px;
  border-radius: var(--xl-radius-sm, 6px);
}

.review-issue--error {
  border-left-color: var(--xl-color-danger, #d03050);
}

.review-issue--warning {
  border-left-color: var(--xl-color-warning, #e6a23c);
}

.review-issue--info {
  border-left-color: var(--xl-text-muted);
}

.review-issue__head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 4px;
}

.review-issue__severity {
  font-size: 12px;
  font-weight: 600;
}

.review-issue--error .review-issue__severity {
  color: var(--xl-color-danger, #d03050);
}

.review-issue--warning .review-issue__severity {
  color: var(--xl-color-warning, #e6a23c);
}

.review-issue--info .review-issue__severity {
  color: var(--xl-text-muted);
}

.review-issue__position {
  color: var(--xl-text-secondary);
  font-size: 12px;
}

.review-issue__evidence,
.review-issue__suggestion {
  margin: 4px 0 0;
  color: var(--xl-text-secondary);
  font-size: 13px;
  line-height: 1.6;
  overflow-wrap: break-word;
}

.review-issue__suggestion {
  color: var(--xl-text-primary);
}

.review-detail__reject-info {
  padding: 12px 14px;
  border-radius: var(--xl-radius-sm, 6px);
  background: var(--xl-bg-secondary);
  color: var(--xl-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.review-detail__reject-info p {
  margin: 0 0 6px;
}

.review-detail__reject-info p:last-child {
  margin-bottom: 0;
}

.review-detail__actions {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.review-detail__reject-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid var(--xl-border);
}

.review-detail__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.review-detail__label {
  color: var(--xl-text-secondary);
  font-size: 13px;
}

.review-detail__input,
.review-detail__textarea {
  box-sizing: border-box;
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-sm, 6px);
  background: var(--xl-bg-secondary);
  color: var(--xl-text-primary);
  font-family: inherit;
  font-size: 13px;
  outline: none;
}

.review-detail__textarea {
  resize: vertical;
}

.review-detail__input:focus,
.review-detail__textarea:focus {
  border-color: var(--xl-color-primary);
}

.review-detail__message {
  margin: 12px 0 0;
  color: var(--xl-color-ai);
  font-size: 13px;
}

.review-detail__action-error {
  margin: 12px 0 0;
  color: var(--xl-color-danger, #d03050);
  font-size: 13px;
}
</style>
