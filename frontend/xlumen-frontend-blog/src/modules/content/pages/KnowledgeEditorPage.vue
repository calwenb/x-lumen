<script setup lang="ts">
// 知识编辑页（B10，F-0301/F-0302/F-0307，KB-4 适配决策 D16）：
// 单库单目录归属（知识库/目录选择器，无文章级可见性/分类），草稿自动保存（10s 节流 + 失焦触发）。
// 显式保存走乐观锁版本校验，409 冲突提供恢复入口；草稿态可提交审核（F-0902，BUG-5 入口补全）。
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import {
  autosaveDraft,
  createKnowledge,
  fetchKnowledge,
  updateKnowledge,
  STATUS_LABELS,
} from '@/modules/content/api/knowledge'
import MarkdownEditor from '@/modules/content/components/MarkdownEditor.vue'
import { useAutoSave } from '@/modules/content/composables/useAutoSave'
import { fetchDirectoryTree, fetchKnowledgeBases } from '@/modules/knowledge/api/knowledgeBase'
import type { DirectoryNode, KnowledgeBase } from '@/modules/knowledge/api/knowledgeBase'
import { createReview } from '@/modules/publishing/api/review'

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

const loading = ref(true)
const loadError = ref(false)
const saving = ref(false)
const saveMessage = ref('')
const submitting = ref(false)

/** 可选知识库列表（当前空间，F-0308）。 */
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
const isDirty = () => content.value !== lastSaved.value || title.value !== lastTitle.value

/** 自动保存回调：新建草稿必须有归属库（决策 D16），未选库时跳过。 */
async function doAutoSave(): Promise<{ id: string; version: string } | null> {
  if (!title.value.trim() || !kbId.value) {
    return null
  }
  const saved = await autosaveDraft({
    ...(knowledgeId.value ? { knowledgeId: knowledgeId.value, version: version.value } : {}),
    title: title.value,
    content: content.value,
    kbId: kbId.value,
    directoryId: directoryId.value,
    tags: tags.value,
  })
  knowledgeId.value = saved.id
  version.value = saved.version
  lastSaved.value = content.value
  lastTitle.value = title.value
  return { id: saved.id, version: saved.version }
}

const autoSave = useAutoSave(isDirty, doAutoSave)

/** 切换知识库时重新加载目录树并复位目录选择。 */
watch(kbId, async (next) => {
  directoryId.value = '0'
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
  // 已有知识加载完成后设置目录，避免被 watch 复位
  if (restoring.value) {
    restoring.value = false
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

/** 编辑模式加载过程中抑制目录 watch 复位（加载完统一回填）。 */
const restoring = ref(false)

/** 冲突提示状态。 */
const conflict = ref(false)

async function handleConflict(): Promise<void> {
  // 409 恢复入口：重新拉取服务端最新内容覆盖本地
  if (knowledgeId.value) {
    const latest = await fetchKnowledge(knowledgeId.value)
    title.value = latest.title
    content.value = latest.content
    kbId.value = latest.kbId ?? ''
    directoryId.value = latest.directoryId ?? '0'
    tagsInput.value = latest.tags.join(', ')
    version.value = latest.version
    lastSaved.value = latest.content
    lastTitle.value = latest.title
  }
  conflict.value = false
}

/** 显式保存（创建或更新）：与自动保存同一幂等通道。 */
async function handleSave(): Promise<void> {
  if (!title.value.trim()) {
    saveMessage.value = '请先填写标题'
    return
  }
  if (!kbId.value) {
    saveMessage.value = '请选择知识库'
    return
  }
  saving.value = true
  conflict.value = false
  saveMessage.value = ''
  try {
    if (isNew.value && !knowledgeId.value) {
      const created = await createKnowledge({
        title: title.value,
        content: content.value,
        kbId: kbId.value,
        directoryId: directoryId.value,
        tags: tags.value,
      })
      knowledgeId.value = created.id
      version.value = created.version
      status.value = created.status
      lastSaved.value = content.value
      lastTitle.value = title.value
      await router.replace({ name: 'knowledge-edit', params: { id: created.id } })
    } else if (knowledgeId.value) {
      const updated = await updateKnowledge(knowledgeId.value, version.value, {
        title: title.value,
        content: content.value,
        kbId: kbId.value,
        directoryId: directoryId.value,
        tags: tags.value,
      })
      version.value = updated.version
      status.value = updated.status
      lastSaved.value = content.value
      lastTitle.value = title.value
    }
    saveMessage.value = '已保存'
  } catch (error) {
    if (error instanceof Error && error.message.includes('冲突')) {
      conflict.value = true
    } else {
      saveMessage.value = error instanceof Error ? error.message : '保存失败'
    }
  } finally {
    saving.value = false
  }
}

/** 提交审核（F-0902）：先保存最新内容再提交，草稿/已通过态可用。 */
async function handleSubmitReview(): Promise<void> {
  if (!knowledgeId.value) {
    saveMessage.value = '请先保存知识再提交审核'
    return
  }
  if (isDirty()) {
    await handleSave()
    if (saveMessage.value && saveMessage.value !== '已保存') {
      return
    }
  }
  submitting.value = true
  try {
    await createReview(knowledgeId.value)
    saveMessage.value = '已提交审核'
    status.value = 3
  } catch (error) {
    saveMessage.value = error instanceof Error ? error.message : '提交审核失败'
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  // 加载当前空间知识库列表（编辑器归属选择，F-0308）
  try {
    knowledgeBases.value = await fetchKnowledgeBases()
  } catch {
    knowledgeBases.value = []
  }
  if (!isNew.value) {
    restoring.value = true
    try {
      const knowledge = await fetchKnowledge(String(route.params.id))
      knowledgeId.value = knowledge.id
      version.value = knowledge.version
      title.value = knowledge.title
      content.value = knowledge.content
      kbId.value = knowledge.kbId ?? ''
      directoryId.value = knowledge.directoryId ?? '0'
      status.value = knowledge.status
      tagsInput.value = knowledge.tags.join(', ')
      lastSaved.value = knowledge.content
      lastTitle.value = knowledge.title
    } catch {
      loadError.value = true
    }
  } else {
    lastSaved.value = ''
    lastTitle.value = ''
  }
  loading.value = false
})

/** 草稿（2）或已通过（4）且已落库时可提交审核。 */
const canSubmitReview = computed(
  () => !!knowledgeId.value && (status.value === 2 || status.value === 4),
)
</script>

<template>
  <main class="editor-page">
    <div class="editor-page__header">
      <h1 class="editor-page__title">{{ isNew ? '新建知识' : '编辑知识' }}</h1>
      <div class="editor-page__actions">
        <span class="editor-page__status" :class="{ 'editor-page__status--conflict': conflict }">
          {{
            conflict
              ? '版本冲突'
              : autoSave.saving.value || saving || submitting
                ? '保存中…'
                : autoSave.savedAt.value
                  ? '已自动保存'
                  : ''
          }}
        </span>
        <el-button
          v-if="canSubmitReview"
          type="success"
          plain
          :loading="submitting"
          @click="handleSubmitReview"
          >提交审核</el-button
        >
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        <RouterLink class="editor-page__back" :to="{ name: 'knowledge-list' }">返回列表</RouterLink>
      </div>
    </div>

    <div v-if="loading" class="editor-page__loading" role="status">加载中…</div>
    <div v-else-if="loadError" class="editor-page__error">
      <p>知识加载失败，可能不存在或已被删除。</p>
      <RouterLink class="editor-page__back" :to="{ name: 'knowledge-list' }">返回列表</RouterLink>
    </div>
    <template v-else>
      <div v-if="conflict" class="editor-page__conflict" role="alert">
        <p>知识在其他地方已被修改，为避免覆盖，请选择：</p>
        <el-button type="warning" plain size="small" @click="handleConflict"
          >加载服务端最新版本</el-button
        >
      </div>
      <div v-if="saveMessage" class="editor-page__message" role="status">{{ saveMessage }}</div>

      <section class="editor-page__fields">
        <el-input
          v-model="title"
          class="editor-page__title-input"
          type="text"
          placeholder="知识标题"
          aria-label="知识标题"
          size="large"
          @input="autoSave.touch()"
        />
        <div class="editor-page__row">
          <el-select
            v-model="kbId"
            class="editor-page__kb"
            placeholder="所属知识库（必选）"
            aria-label="所属知识库"
            :disabled="!isNew"
            @change="autoSave.touch()"
          >
            <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
          <el-select
            v-model="directoryId"
            class="editor-page__directory"
            placeholder="所属目录（默认库根）"
            aria-label="所属目录"
            :loading="directoriesLoading"
            :disabled="!kbId"
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
          <el-input
            v-model="tagsInput"
            class="editor-page__tags"
            type="text"
            placeholder="标签（逗号分隔，如：Spring, Vue）"
            aria-label="标签"
            @input="autoSave.touch()"
          />
        </div>
        <div class="editor-page__row editor-page__hint">
          <span v-if="knowledgeId" class="editor-page__status-text">
            当前状态：{{ STATUS_LABELS[status] ?? status }} · 归属库与目录不可修改（单库单目录，决策
            D16）
          </span>
          <span v-else>知识按「库 → 目录 → 知识」组织，请先选择知识库（新建后不可更换）</span>
        </div>
      </section>

      <MarkdownEditor
        v-model="content"
        class="editor-page__editor"
        @update:model-value="autoSave.touch()"
      />
    </template>
  </main>
</template>

<style scoped>
.editor-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 32px 20px 64px;
}

.editor-page__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.editor-page__title {
  margin: 0;
  font-size: 24px;
}

.editor-page__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.editor-page__status {
  font-size: 12px;
  color: var(--xl-text-secondary);
}

.editor-page__status--conflict {
  color: var(--xl-color-danger);
}

.editor-page__back {
  color: var(--xl-text-secondary);
  font-size: 13px;
  text-decoration: none;
}

.editor-page__back:hover {
  color: var(--xl-color-primary);
}

.editor-page__loading,
.editor-page__error {
  padding: 48px 0;
  text-align: center;
  color: var(--xl-text-secondary);
}

.editor-page__conflict {
  margin-bottom: 16px;
  padding: 12px 16px;
  border: 1px solid var(--xl-color-danger);
  border-radius: var(--xl-radius-sm);
  background: color-mix(in srgb, var(--xl-color-danger) 6%, transparent);
  color: var(--xl-color-danger);
  font-size: 13px;
}

.editor-page__conflict p {
  margin: 0 0 8px;
}

.editor-page__message {
  margin-bottom: 12px;
  color: var(--xl-text-secondary);
  font-size: 13px;
}

.editor-page__fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.editor-page__title-input :deep(.el-input__inner) {
  font-size: 18px;
  font-weight: 600;
}

.editor-page__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.editor-page__kb,
.editor-page__directory,
.editor-page__tags {
  flex: 1;
  min-width: 180px;
}

.editor-page__hint {
  font-size: 12px;
  color: var(--xl-text-secondary);
}

.editor-page__status-text {
  font-size: 12px;
  color: var(--xl-text-secondary);
}
</style>
