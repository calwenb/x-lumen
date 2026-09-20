<script setup lang="ts">
// 知识编辑页（B10，KB-4 适配决策 D16）：
// 单库单目录归属（知识库/目录选择器，无文章级可见性/分类），草稿自动保存（10s 节流 + 失焦触发）。
// 显式保存走乐观锁版本校验，409 冲突提供恢复入口；草稿态可触发自动 AI 审核发布。
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  autosaveDraft,
  createKnowledge,
  fetchKnowledge,
  KnowledgeConflictError,
  updateKnowledge,
  STATUS_LABELS,
} from '@/modules/content/api/knowledge'
import MarkdownEditor from '@/modules/content/components/MarkdownEditor.vue'
import { useAutoSave } from '@/modules/content/composables/useAutoSave'
import { fetchDirectoryTree, fetchKnowledgeBases } from '@/modules/knowledge/api/knowledgeBase'
import type { DirectoryNode, KnowledgeBase } from '@/modules/knowledge/api/knowledgeBase'
import { createAutoReview } from '@/modules/publishing/api/review'

const route = useRoute()
const router = useRouter()

/** 新建模式（无 :id 参数）。 */
const isNew = computed(() => !route.params.id)

const knowledgeId = ref<string | null>(null)
const version = ref('0')
const title = ref('')
const content = ref('')
const kbId = ref<string>('')
const directoryId = ref<string>('0')
const tagsInput = ref('')
const status = ref(2)
/** 已发布（状态 6）版本服务端不可修改：输入只读、保存/自动保存逻辑拦截并提示，不再静默吞 409。 */
const isPublished = computed(() => status.value === 6)

const loading = ref(true)
const loadError = ref(false)
const saving = ref(false)
const saveMessage = ref('')
const submitting = ref(false)
const publishAt = ref('')

/** 可选知识库列表（当前空间）。 */
const knowledgeBases = ref<KnowledgeBase[]>([])
/** 当前库的目录树（扁平化后供下拉选择，0=库根）。 */
const directoryOptions = ref<Array<{ id: string; label: string }>>([])
const directoriesLoading = ref(false)

/** 标签：逗号分隔输入 → 数组。 */
const tags = computed(() =>
  tagsInput.value
    .split(/[,，]/)
    .map((t) => t.trim())
    .filter(Boolean),
)

/** 内容是否有未保存变更（对比最后一次落库内容）。 */
const lastSaved = ref('')
const lastTitle = ref('')
const lastKbId = ref('')
const lastDirectoryId = ref('0')
const lastTagsInput = ref('')
const conflict = ref(false)
/** 冲突时服务端的原始提示，原样展示，不吞成泛化文案。 */
const conflictDetail = ref('')

interface EditorSnapshot {
  title: string
  content: string
  kbId: string
  directoryId: string
  tagsInput: string
  tags: string[]
}

function takeEditorSnapshot(): EditorSnapshot {
  return {
    title: title.value,
    content: content.value,
    kbId: kbId.value,
    directoryId: directoryId.value,
    tagsInput: tagsInput.value,
    tags: [...tags.value],
  }
}

function markSavedSnapshot(snapshot = takeEditorSnapshot()): void {
  lastSaved.value = snapshot.content
  lastTitle.value = snapshot.title
  lastKbId.value = snapshot.kbId
  lastDirectoryId.value = snapshot.directoryId
  lastTagsInput.value = snapshot.tagsInput
}

const isDirty = () =>
  content.value !== lastSaved.value ||
  title.value !== lastTitle.value ||
  kbId.value !== lastKbId.value ||
  directoryId.value !== lastDirectoryId.value ||
  tagsInput.value !== lastTagsInput.value

/**
 * 保存串行化：同一时刻至多一个在途保存请求。
 * 手动保存排队在在途自动保存之后，因此读取到的是自动保存已同步的最新版本号，
 * 消除单客户端内自动保存与手动保存互相触发 409 的自竞争。
 */
let saveQueue: Promise<unknown> = Promise.resolve()

function enqueueSave<T>(task: () => Promise<T>): Promise<T> {
  const run = saveQueue.then(task, task)
  saveQueue = run.then(
    () => undefined,
    () => undefined,
  )
  return run
}

/** 自动保存回调：新建草稿必须有归属库（决策 D16），未选库或正文为空时跳过。 */
async function doAutoSave(): Promise<{ id: string; version: string } | null> {
  // 正文校验只针对显式保存/发布：用户清空重写过程中自动保存静默跳过，避免报错刷屏。
  if (isPublished.value || !title.value.trim() || !content.value.trim() || !kbId.value) {
    return null
  }
  try {
    return await enqueueSave(async () => {
      const snapshot = takeEditorSnapshot()
      const firstSave = !knowledgeId.value
      const saved = await autosaveDraft({
        ...(knowledgeId.value ? { knowledgeId: knowledgeId.value, version: version.value } : {}),
        title: snapshot.title,
        content: snapshot.content,
        kbId: snapshot.kbId,
        directoryId: snapshot.directoryId,
        tags: snapshot.tags,
      })
      knowledgeId.value = saved.id
      version.value = saved.version
      markSavedSnapshot(snapshot)
      if (firstSave) {
        await router.replace({ name: 'knowledge-edit', params: { id: saved.id } })
      }
      return { id: saved.id, version: saved.version }
    })
  } catch (error) {
    // 自动保存遇真冲突：统一在页面冲突面板处理并保留本地内容，不让组合式函数再置 error 文案
    if (error instanceof KnowledgeConflictError) {
      conflict.value = true
      conflictDetail.value = error.serverMessage
      saveMessage.value = ''
      return null
    }
    throw error
  }
}

const autoSave = useAutoSave(isDirty, doAutoSave, () => {
  conflict.value = true
})

/**
 * 详情加载带入的目标目录：目录树异步返回，先记录目标值，
 * 目录 watch 触发时不再无条件复位为库根，避免「详情先到、目录树后到」把回显冲掉。
 */
let pendingDirectoryId: string | null = null

/** 切换知识库时重新加载目录树；新建时目录复位为库根。 */
watch(kbId, async (next) => {
  const restoredDirectoryId = pendingDirectoryId
  pendingDirectoryId = null
  if (!restoredDirectoryId) {
    directoryId.value = '0'
  }
  if (!next) {
    directoryOptions.value = []
    return
  }
  directoriesLoading.value = true
  try {
    const tree = await fetchDirectoryTree(next)
    directoryOptions.value = flattenDirectories(tree, 0)
  } catch {
    directoryOptions.value = []
  } finally {
    directoriesLoading.value = false
  }
})

/** 目录树扁平化（缩进展示层级，0=库根）。 */
function flattenDirectories(
  nodes: DirectoryNode[],
  depth: number,
): Array<{ id: string; label: string }> {
  const result: Array<{ id: string; label: string }> = []
  for (const node of nodes) {
    result.push({ id: node.id, label: `${'　'.repeat(depth)}${node.name}` })
    result.push(...flattenDirectories(node.children, depth + 1))
  }
  return result
}

/** 复制本地未保存内容：冲突时让用户可先自行备份，再决定是否加载服务端版本。 */
async function handleCopyLocal(): Promise<void> {
  const text = `${title.value}\n\n${content.value}`
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('本地内容已复制到剪贴板')
  } catch {
    ElMessage.warning('浏览器未授权剪贴板访问，请手动选中复制')
  }
}

/** 409 恢复入口：确认后加载服务端最新版本覆盖本地（不静默覆盖未保存内容）。 */
async function handleConflict(): Promise<void> {
  if (!knowledgeId.value) {
    conflict.value = false
    return
  }
  if (isDirty()) {
    try {
      await ElMessageBox.confirm(
        '加载服务端最新版本会替换当前编辑器内容，本地未保存的修改将不再显示（可先点「复制本地内容」备份）。是否继续？',
        '加载服务端最新版本',
        { confirmButtonText: '加载最新版本', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return
    }
  }
  await enqueueSave(async () => {
    const latest = await fetchKnowledge(knowledgeId.value as string)
    title.value = latest.title
    content.value = latest.content
    kbId.value = latest.kbId ?? ''
    directoryId.value = latest.directoryId ?? '0'
    tagsInput.value = latest.tags.join(', ')
    version.value = latest.version
    markSavedSnapshot()
  })
  conflict.value = false
  conflictDetail.value = ''
  saveMessage.value = '已加载服务端最新版本'
}

/** 显式保存（创建或更新）：与自动保存串行同一幂等通道。 */
async function handleSave(): Promise<boolean> {
  if (isPublished.value) {
    saveMessage.value = '已发布版本不可修改，如需调整请新建知识后重新发布'
    return false
  }
  if (!title.value.trim()) {
    saveMessage.value = '请先填写标题'
    return false
  }
  if (!content.value.trim()) {
    saveMessage.value = '请先填写正文'
    return false
  }
  if (!kbId.value) {
    saveMessage.value = '请选择知识库'
    return false
  }
  saving.value = true
  saveMessage.value = ''
  try {
    return await enqueueSave(async () => {
      const snapshot = takeEditorSnapshot()
      if (isNew.value && !knowledgeId.value) {
        const created = await createKnowledge({
          title: snapshot.title,
          content: snapshot.content,
          kbId: snapshot.kbId,
          directoryId: snapshot.directoryId,
          tags: snapshot.tags,
        })
        knowledgeId.value = created.id
        version.value = created.version
        status.value = created.status
        markSavedSnapshot(snapshot)
        await router.replace({ name: 'knowledge-edit', params: { id: created.id } })
      } else if (knowledgeId.value) {
        const updated = await updateKnowledge(knowledgeId.value, version.value, {
          title: snapshot.title,
          content: snapshot.content,
          kbId: snapshot.kbId,
          directoryId: snapshot.directoryId,
          tags: snapshot.tags,
        })
        version.value = updated.version
        status.value = updated.status
        markSavedSnapshot(snapshot)
      }
      conflict.value = false
      conflictDetail.value = ''
      saveMessage.value = '已保存'
      return true
    })
  } catch (error) {
    if (error instanceof KnowledgeConflictError) {
      conflict.value = true
      conflictDetail.value = error.serverMessage
      saveMessage.value = ''
    } else {
      saveMessage.value = error instanceof Error ? error.message : '保存失败'
    }
    return false
  } finally {
    saving.value = false
  }
}

/** 发布：提交 AI 审核后立即返回，审核通过自动发布（立即/定时），完成站内通知。 */
async function handleAutoPublish(): Promise<void> {
  if (!title.value.trim()) {
    saveMessage.value = '请先填写标题'
    return
  }
  if (!content.value.trim()) {
    saveMessage.value = '请先填写正文'
    return
  }
  if (!kbId.value) {
    saveMessage.value = '请选择知识库'
    return
  }
  if (publishAt.value && new Date(publishAt.value).getTime() <= Date.now()) {
    saveMessage.value = '定时发布时间必须晚于当前时间'
    return
  }
  const publishPlan = publishAt.value
    ? `AI 审核通过后，将于 ${publishAt.value.replace('T', ' ')} 定时发布，结果将通过消息中心通知你。`
    : '审核通过后将自动发布，结果将通过消息中心通知你。'
  try {
    await ElMessageBox.confirm(`点击确认后进入 AI 审核。${publishPlan}`, '确认发布', {
      confirmButtonText: '提交审核',
      cancelButtonText: '返回修改',
      type: 'info',
    })
  } catch {
    return
  }
  submitting.value = true
  try {
    if (!knowledgeId.value || isDirty()) {
      const saved = await handleSave()
      if (!saved) return
    }
    if (!knowledgeId.value) return
    const review = await createAutoReview(knowledgeId.value, publishAt.value || undefined)
    if (review.status === 'APPROVED') {
      // 强制审核关闭：无 AI 任务，提交即通过（既有审核记录也可能直接 APPROVED）
      ElMessage.success(publishAt.value ? '已提交定时发布' : '已发布，公开可见')
    } else {
      ElMessage.success('已提交 AI 审核，审核完成后将通过消息中心通知你')
    }
    await router.push({ name: 'knowledge-list' })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交审核失败')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  // 加载当前空间知识库列表（编辑器归属选择）
  try {
    knowledgeBases.value = await fetchKnowledgeBases()
  } catch {
    knowledgeBases.value = []
  }
  if (!isNew.value) {
    try {
      const knowledge = await fetchKnowledge(String(route.params.id))
      knowledgeId.value = knowledge.id
      version.value = knowledge.version
      title.value = knowledge.title
      content.value = knowledge.content
      const loadedKbId = knowledge.kbId ?? ''
      const loadedDirectoryId = knowledge.directoryId ?? '0'
      // 目录树异步加载：先登记目标目录，kbId watch 据此跳过「复位库根」
      pendingDirectoryId = loadedDirectoryId
      kbId.value = loadedKbId
      directoryId.value = loadedDirectoryId
      status.value = knowledge.status
      tagsInput.value = knowledge.tags.join(', ')
      lastSaved.value = knowledge.content
      lastTitle.value = knowledge.title
      lastKbId.value = loadedKbId
      lastDirectoryId.value = loadedDirectoryId
      lastTagsInput.value = knowledge.tags.join(', ')
    } catch {
      loadError.value = true
    }
  } else {
    lastSaved.value = ''
    lastTitle.value = ''
  }
  loading.value = false
})

/** 新建草稿也可直接发布：确认后先保存，再执行 AI 审核。 */
const canAutoPublish = computed(() => status.value === 2 || status.value === 4)

const editorStatusText = computed(() => {
  if (conflict.value) return '版本冲突：内容已在其他地方被修改'
  if (submitting.value) return 'AI 审核中，请勿关闭页面…'
  if (autoSave.saving.value || saving.value) return '保存中…'
  if (autoSave.error.value) return autoSave.error.value
  if (autoSave.savedAt.value) return '已自动保存'
  return ''
})
</script>

<template>
  <main class="editor-page">
    <!-- 吸顶命令栏 -->
    <header class="editor-page__cmdbar">
      <div class="editor-page__cmdbar-left">
        <RouterLink class="editor-page__back" :to="{ name: 'knowledge-list' }">返回列表</RouterLink>
        <span class="editor-page__cmdbar-sep" aria-hidden="true">/</span>
        <h1 class="editor-page__title">{{ isNew ? '新建知识' : '编辑知识' }}</h1>
      </div>
      <div class="editor-page__cmdbar-status" aria-live="polite">
        <span class="editor-page__status" :class="{ 'editor-page__status--conflict': conflict }">
          {{ editorStatusText }}
        </span>
      </div>
      <div class="editor-page__actions">
        <el-button :loading="saving" :disabled="submitting" @click="handleSave">保存</el-button>
        <el-button
          v-if="canAutoPublish"
          type="primary"
          :loading="submitting"
          :disabled="saving"
          loading-text="AI 审核中"
          @click="handleAutoPublish"
          >发布</el-button
        >
      </div>
    </header>

    <div v-if="loading" class="editor-page__loading" role="status">加载中…</div>
    <div v-else-if="loadError" class="editor-page__error">
      <p>知识加载失败，可能不存在或已被删除。</p>
      <RouterLink class="editor-page__back" :to="{ name: 'knowledge-list' }">返回列表</RouterLink>
    </div>
    <template v-else>
      <div v-if="conflict" class="editor-page__conflict" role="alert">
        <p>内容已在其他地方被修改。本地未保存的内容会一直保留，直到你选择如何处理，不会被静默覆盖。</p>
        <p v-if="conflictDetail" class="editor-page__conflict-detail">服务端提示：{{ conflictDetail }}</p>
        <div class="editor-page__conflict-actions">
          <el-button type="warning" plain size="small" @click="handleConflict"
            >加载服务端最新版本</el-button
          >
          <el-button plain size="small" @click="handleCopyLocal">复制本地内容</el-button>
        </div>
      </div>
      <div v-if="saveMessage" class="editor-page__message" role="status">{{ saveMessage }}</div>
      <div v-if="isPublished" class="editor-page__message" role="status">
        该知识已发布，内容为只读；如需调整请新建知识后重新发布。
      </div>

      <div class="editor-page__workspace">
        <!-- 元数据轨 -->
        <aside class="editor-page__rail">
          <label class="editor-page__field-label" for="editor-title">知识标题</label>
          <el-input
            id="editor-title"
            v-model="title"
            class="editor-page__title-input"
            type="text"
            placeholder="知识标题"
            aria-label="知识标题"
            size="large"
            :disabled="submitting || isPublished"
            @input="autoSave.touch()"
            @blur="autoSave.flush()"
          />

          <label class="editor-page__field-label" for="editor-kb">所属知识库</label>
          <el-select
            id="editor-kb"
            v-model="kbId"
            class="editor-page__kb"
            placeholder="所属知识库（必选）"
            aria-label="所属知识库"
            :disabled="Boolean(knowledgeId) || submitting"
            @change="autoSave.touch()"
          >
            <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>

          <label class="editor-page__field-label" for="editor-dir">所属目录</label>
          <el-select
            id="editor-dir"
            v-model="directoryId"
            class="editor-page__directory"
            placeholder="所属目录（默认库根）"
            aria-label="所属目录"
            :loading="directoriesLoading"
            :disabled="!kbId || submitting || isPublished"
            @change="autoSave.touch()"
          >
            <el-option label="（库根）" value="0" />
            <el-option
              v-for="dir in directoryOptions"
              :key="dir.id"
              :label="dir.label"
              :value="dir.id"
            />
          </el-select>

          <label class="editor-page__field-label" for="editor-tags">标签</label>
          <el-input
            id="editor-tags"
            v-model="tagsInput"
            class="editor-page__tags"
            type="text"
            placeholder="标签（逗号分隔，如：Spring, Vue）"
            aria-label="标签"
            :disabled="submitting || isPublished"
            @input="autoSave.touch()"
            @blur="autoSave.flush()"
          />

          <div v-if="canAutoPublish" class="editor-page__publish-settings">
            <label class="editor-page__publish-label" for="publish-at">发布时间</label>
            <input
              id="publish-at"
              v-model="publishAt"
              class="editor-page__publish-at"
              type="datetime-local"
              :disabled="submitting"
              aria-describedby="publish-at-hint"
            />
            <span id="publish-at-hint" class="editor-page__publish-hint">
              留空表示审核通过后立即发布；选择时间则进入定时发布。
            </span>
          </div>

          <p class="editor-page__status-text">
            <template v-if="knowledgeId">
              当前状态：{{ STATUS_LABELS[status] ?? status }} · 归属库不可修改，目录可调整
            </template>
            <template v-else>
              知识按「库 → 目录 → 知识」组织，请先选择知识库（新建后不可更换）
            </template>
          </p>
        </aside>

        <!-- 编辑画布 -->
        <section class="editor-page__canvas">
          <MarkdownEditor
            v-model="content"
            class="editor-page__editor"
            :disabled="submitting || isPublished"
            @update:model-value="autoSave.touch()"
            @blur="autoSave.flush()"
          />
        </section>
      </div>
    </template>
  </main>
</template>

<style scoped>
.editor-page {
  min-height: 100vh;
  padding-bottom: 64px;
}

.editor-page__cmdbar {
  position: sticky;
  top: var(--xl-header-h);
  z-index: 20;
  display: flex;
  align-items: center;
  gap: var(--xl-space-4);
  padding: 0 var(--xl-content-pad);
  height: 58px;
  border-bottom: 1px solid var(--xl-border);
  background: color-mix(in srgb, var(--xl-bg-surface) 92%, transparent);
  backdrop-filter: saturate(180%) blur(8px);
}

.editor-page__cmdbar-left {
  display: flex;
  align-items: center;
  gap: var(--xl-space-3);
  min-width: 0;
}

.editor-page__title {
  margin: 0;
  font-size: var(--xl-fs-title);
  font-weight: var(--xl-fs-title-w);
  color: var(--xl-text-primary);
  white-space: nowrap;
}

.editor-page__cmdbar-sep {
  color: var(--xl-text-muted);
}

.editor-page__back {
  color: var(--xl-text-secondary);
  font-size: 14px;
  text-decoration: none;
  white-space: nowrap;
}

.editor-page__back:hover {
  color: var(--xl-color-primary);
}

.editor-page__cmdbar-status {
  flex: 1;
  text-align: center;
}

.editor-page__status {
  font-size: var(--xl-fs-caption);
  color: var(--xl-text-secondary);
}

.editor-page__status--conflict {
  color: var(--xl-color-danger);
}

.editor-page__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.editor-page__loading,
.editor-page__error {
  padding: 48px 0;
  text-align: center;
  color: var(--xl-text-secondary);
}

.editor-page__conflict {
  margin: var(--xl-space-4) var(--xl-content-pad) 0;
  padding: 12px 16px;
  border: 1px solid var(--xl-color-danger);
  border-radius: var(--xl-radius-sm);
  background: color-mix(in srgb, var(--xl-color-danger) 6%, transparent);
  color: var(--xl-color-danger);
  font-size: 14px;
}

.editor-page__conflict p {
  margin: 0 0 8px;
}

.editor-page__conflict-detail {
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-caption);
}

.editor-page__conflict-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.editor-page__message {
  margin: var(--xl-space-3) var(--xl-content-pad) 0;
  color: var(--xl-text-secondary);
  font-size: 14px;
}

/* 工作区：元数据轨 + 编辑画布 */
.editor-page__workspace {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  gap: var(--xl-space-6);
  align-items: start;
  max-width: var(--xl-container);
  margin: 0 auto;
  padding: var(--xl-space-6) var(--xl-content-pad);
}

.editor-page__rail {
  position: sticky;
  top: calc(var(--xl-header-h) + 58px + var(--xl-space-4));
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-2);
  padding: var(--xl-space-4) var(--xl-space-4) var(--xl-space-6);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
}

.editor-page__field-label {
  margin-top: var(--xl-space-3);
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-caption);
}

.editor-page__field-label:first-child {
  margin-top: 0;
}

.editor-page__kb,
.editor-page__directory,
.editor-page__tags {
  width: 100%;
}

.editor-page__title-input :deep(.el-input__inner) {
  font-size: 18px;
  font-weight: 600;
}

.editor-page__status-text {
  margin: var(--xl-space-4) 0 0;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-caption);
  line-height: 1.6;
}

.editor-page__publish-settings {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-2);
  margin-top: var(--xl-space-4);
  padding: var(--xl-space-3) var(--xl-space-3) var(--xl-space-2);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-sm);
  background: var(--xl-bg-secondary);
}

.editor-page__publish-label {
  color: var(--xl-text-primary);
  font-size: 14px;
  font-weight: 600;
}

.editor-page__publish-at {
  min-width: 0;
  padding: 7px 9px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-sm);
  background: var(--xl-bg-surface);
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-caption);
}

.editor-page__publish-hint {
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-caption);
  line-height: 1.5;
}

.editor-page__canvas {
  min-width: 0;
}

.editor-page__editor {
  height: 100%;
}

@media (width <= 860px) {
  .editor-page__workspace {
    grid-template-columns: 1fr;
  }

  .editor-page__rail {
    position: static;
  }

  .editor-page__cmdbar {
    flex-wrap: wrap;
    height: auto;
    padding: var(--xl-space-2) var(--xl-content-pad);
    gap: var(--xl-space-2);
  }

  .editor-page__cmdbar-left,
  .editor-page__cmdbar-status {
    min-width: 0;
  }
}
</style>
