<script setup lang="ts">
// 模型配置：场景模型表格（供应商下拉 + 模型输入 + 每日配额 + Prompt 编辑 + 保存）与连通性测试。
// WRITING 场景 Prompt 为 JSON 文本（大纲/分章/自审/修订 4 槽位），其余场景为单个 Prompt 文本。
// 保存语义：provider/model/dailyQuota 必发；prompt 仅当用户编辑过或点击「恢复默认」才发送。
// 关键状态：加载骨架、失败重试、空态、保存中、测试中、结果提示。
import { computed, onMounted, reactive, ref } from 'vue'
import { Setting } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

import {
  PROVIDER_OPTIONS,
  SCENE_LABELS,
  fetchModelConfigs,
  testModelConfig,
  updateModelConfig,
} from '../api/model'

import type { ModelConfig, ModelConfigUpdate, ModelTestResult, ProviderValue } from '../api/model'

const configs = ref<ModelConfig[]>([])
const loading = ref(true)
const loadError = ref(false)

// 正在保存的场景集合（支持多行并发提交）
const savingScenes = reactive(new Set<string>())

const testProvider = ref<ProviderValue>('BAILIAN')
const testModel = ref('')
const testing = ref(false)
const testResult = ref<ModelTestResult | null>(null)

// WRITING 场景 Prompt 槽位（存为 JSON 对象）。
const PROMPT_SLOTS = [
  { key: 'outline', label: '大纲' },
  { key: 'chapter', label: '分章' },
  { key: 'self_review', label: '自审' },
  { key: 'revise', label: '修订' },
] as const

type PromptSlotKey = (typeof PROMPT_SLOTS)[number]['key']
type PromptSlots = Record<PromptSlotKey, string>

interface PromptDraft {
  slots: PromptSlots
  text: string
  dirty: boolean
}

const EMPTY_SLOTS: PromptSlots = { outline: '', chapter: '', self_review: '', revise: '' }

// 场景 Prompt 草稿（scene → 草稿）；Prompt 为空或无效 JSON 时四个槽位均为空。
const promptDrafts = reactive(new Map<string, PromptDraft>())

// Prompt 编辑弹窗状态：当前行、场景、草稿（草稿与 promptDrafts 共享引用）。
const promptEditorVisible = ref(false)
const promptEditorRecord = ref<ModelConfig | null>(null)
const promptEditorScene = ref('')
const promptEditorDraft = ref<PromptDraft>({ slots: { ...EMPTY_SLOTS }, text: '', dirty: false })

const promptEditorTitle = computed(() => {
  const item = promptEditorRecord.value
  if (!item) {
    return 'Prompt 配置'
  }
  return `${SCENE_LABELS[item.scene] ?? item.scene} · Prompt 配置`
})

function formatTime(iso: string): string {
  return iso ? iso.slice(0, 16).replace('T', ' ') : '—'
}

/** 供应商选项：当前值不在预设内时补一个兜底项，保证下拉可回显。 */
function providerOptions(current: string): Array<{ value: string; label: string }> {
  const options: Array<{ value: string; label: string }> = PROVIDER_OPTIONS.map((opt) => ({
    value: opt.value,
    label: opt.label,
  }))
  if (current !== '' && !PROVIDER_OPTIONS.some((opt) => opt.value === current)) {
    options.push({ value: current, label: current })
  }
  return options
}

/** 解析 WRITING 场景的 JSON Prompt（空/无效 JSON 时四槽位为空）。 */
function parseWritingSlots(prompt: string): PromptSlots {
  if (!prompt) {
    return { ...EMPTY_SLOTS }
  }
  try {
    const parsed: unknown = JSON.parse(prompt)
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      return { ...EMPTY_SLOTS }
    }
    const source = parsed as Record<string, unknown>
    const slots: PromptSlots = { ...EMPTY_SLOTS }
    for (const slot of PROMPT_SLOTS) {
      const value = source[slot.key]
      slots[slot.key] = typeof value === 'string' ? value : ''
    }
    return slots
  } catch {
    return { ...EMPTY_SLOTS }
  }
}

/** 构造场景 Prompt 草稿。 */
function buildDraft(scene: string, prompt: string | undefined): PromptDraft {
  if (scene === 'WRITING') {
    return { slots: parseWritingSlots(prompt ?? ''), text: '', dirty: false }
  }
  return { slots: { ...EMPTY_SLOTS }, text: prompt ?? '', dirty: false }
}

/** 加载/更新完成后按返回数据重建全部草稿。 */
function rebuildDrafts(): void {
  promptDrafts.clear()
  for (const item of configs.value) {
    promptDrafts.set(item.scene, buildDraft(item.scene, item.prompt))
  }
}

/** 序列化草稿为提交的 Prompt 文本；内容为空时等同恢复默认（空串）。 */
function serializePrompt(scene: string, draft: PromptDraft): string {
  if (scene === 'WRITING') {
    const obj: Record<string, string> = {}
    for (const slot of PROMPT_SLOTS) {
      if (draft.slots[slot.key]) {
        obj[slot.key] = draft.slots[slot.key]
      }
    }
    return Object.keys(obj).length === 0 ? '' : JSON.stringify(obj)
  }
  return draft.text.trim() === '' ? '' : draft.text
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    const items = await fetchModelConfigs()
    for (const item of items) {
      item.dailyQuota = item.dailyQuota ?? 0
    }
    configs.value = items
    rebuildDrafts()
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

/** 每日配额输入：清空输入按 0（不限）处理。 */
function updateQuota(item: ModelConfig, value: number | undefined): void {
  item.dailyQuota = value ?? 0
}

/** 保存单行：provider/model/dailyQuota 必发；prompt 仅在用户编辑过或恢复默认后才发送。 */
async function save(item: ModelConfig): Promise<boolean> {
  savingScenes.add(item.scene)
  try {
    const payload: ModelConfigUpdate = {
      provider: item.provider,
      model: item.model,
      ...(item.paramsJson ? { paramsJson: item.paramsJson } : {}),
      dailyQuota: item.dailyQuota ?? 0,
    }
    const draft = promptDrafts.get(item.scene)
    if (draft?.dirty) {
      payload.prompt = serializePrompt(item.scene, draft)
      draft.dirty = false
    }
    const updated = await updateModelConfig(item.scene, payload)
    Object.assign(item, updated)
    promptDrafts.set(item.scene, buildDraft(item.scene, item.prompt))
    ElMessage.success('已保存')
    return true
  } catch {
    ElMessage.error('保存失败，请稍后重试')
    return false
  } finally {
    savingScenes.delete(item.scene)
  }
}

/** 打开 Prompt 编辑弹窗。 */
function openPromptEditor(item: ModelConfig): void {
  let draft = promptDrafts.get(item.scene)
  if (!draft) {
    draft = buildDraft(item.scene, item.prompt)
    promptDrafts.set(item.scene, draft)
  }
  promptEditorRecord.value = item
  promptEditorScene.value = item.scene
  promptEditorDraft.value = draft
  promptEditorVisible.value = true
}

/** 编辑 Prompt 之后标记草稿待保存。 */
function markPromptDirty(): void {
  promptEditorDraft.value.dirty = true
}

/** 丢弃弹窗中未保存的编辑（回到最后已保存的 Prompt）。 */
function discardPromptEdits(): void {
  const item = promptEditorRecord.value
  if (!item) {
    return
  }
  const fresh = buildDraft(item.scene, item.prompt)
  promptDrafts.set(item.scene, fresh)
  promptEditorDraft.value = fresh
}

function cancelPromptEditor(): void {
  discardPromptEdits()
  promptEditorRecord.value = null
  promptEditorVisible.value = false
}

/** 保存当前行的 Prompt 编辑（含 provider/model/dailyQuota）。 */
async function savePromptEditor(): Promise<void> {
  const item = promptEditorRecord.value
  if (!item) {
    return
  }
  const ok = await save(item)
  if (ok) {
    promptEditorRecord.value = null
    promptEditorVisible.value = false
  }
}

/** 恢复默认：将 prompt 置空字符串发送。 */
async function restorePromptDefault(): Promise<void> {
  const item = promptEditorRecord.value
  if (!item) {
    return
  }
  savingScenes.add(item.scene)
  try {
    const payload: ModelConfigUpdate = {
      provider: item.provider,
      model: item.model,
      ...(item.paramsJson ? { paramsJson: item.paramsJson } : {}),
      dailyQuota: item.dailyQuota ?? 0,
      prompt: '',
    }
    const updated = await updateModelConfig(item.scene, payload)
    Object.assign(item, updated)
    const fresh = buildDraft(item.scene, updated.prompt ?? '')
    promptDrafts.set(item.scene, fresh)
    promptEditorDraft.value = fresh
    ElMessage.success('已恢复默认')
  } catch {
    ElMessage.error('恢复默认失败，请稍后重试')
  } finally {
    savingScenes.delete(item.scene)
  }
}

async function runTest(): Promise<void> {
  testing.value = true
  testResult.value = null
  try {
    testResult.value = await testModelConfig(testProvider.value, testModel.value.trim())
  } catch {
    testResult.value = { ok: false, message: '测试失败，请稍后重试' }
  } finally {
    testing.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <main class="models">
    <h1 class="models__title">模型配置</h1>
    <p class="models__hint">API Key 在服务器 .env 配置，界面不展示；每日配额为 0 表示不限</p>

    <div class="models__layout">
      <!-- A02 左侧 ~68%：配置表 + 底部连通性测试 -->
      <div class="models__main">
        <div v-if="loading" class="models__state" role="status">
          <el-skeleton :rows="5" animated />
        </div>
        <div v-else-if="loadError" class="models__state">
          <p>加载失败，请稍后重试。</p>
          <el-button type="primary" plain @click="load()">重试</el-button>
        </div>
        <div v-else-if="configs.length === 0" class="models__state">
          <el-icon class="models__state-icon"><Setting /></el-icon>
          <p>暂无场景配置</p>
        </div>
        <template v-else>
          <el-table
            :data="configs"
            class="models__table"
            :header-cell-style="{ background: 'var(--xl-bg-secondary)' }"
          >
            <el-table-column label="场景" min-width="100">
              <template #default="{ row }">{{ SCENE_LABELS[row.scene] ?? row.scene }}</template>
            </el-table-column>
            <el-table-column label="供应商" min-width="140">
              <template #default="{ row }">
                <el-select v-model="row.provider" class="models__select" aria-label="供应商">
                  <el-option
                    v-for="opt in providerOptions(row.provider)"
                    :key="opt.value"
                    :value="opt.value"
                    :label="opt.label"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="模型" min-width="160">
              <template #default="{ row }">
                <el-input v-model="row.model" class="models__model-input" placeholder="模型名称" />
              </template>
            </el-table-column>
            <el-table-column label="每日配额" min-width="130">
              <template #default="{ row }">
                <el-input-number
                  :model-value="row.dailyQuota"
                  :min="0"
                  :controls="false"
                  size="small"
                  class="models__quota-input"
                  aria-label="每日配额"
                  @update:model-value="(value: number | undefined) => updateQuota(row, value)"
                />
              </template>
            </el-table-column>
            <el-table-column label="Prompt 配置" min-width="120">
              <template #default="{ row }">
                <el-button type="primary" link @click="openPromptEditor(row)"
                  >编辑 Prompt</el-button
                >
              </template>
            </el-table-column>
            <el-table-column label="更新时间" min-width="140">
              <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="110">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  plain
                  size="small"
                  :loading="savingScenes.has(row.scene)"
                  @click="save(row)"
                >
                  {{ savingScenes.has(row.scene) ? '保存中' : '保存' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <section class="models__test">
            <h2 class="models__test-title">连通性测试</h2>
            <div class="models__test-form">
              <el-select
                v-model="testProvider"
                class="models__test-provider"
                aria-label="测试供应商"
              >
                <el-option
                  v-for="opt in PROVIDER_OPTIONS"
                  :key="opt.value"
                  :value="opt.value"
                  :label="opt.label"
                />
              </el-select>
              <el-input v-model="testModel" class="models__test-model" placeholder="模型名称" />
              <el-button
                type="primary"
                :loading="testing"
                :disabled="testModel.trim() === ''"
                @click="runTest"
              >
                {{ testing ? '测试中' : '测试' }}
              </el-button>
            </div>
            <p
              v-if="testResult"
              class="models__test-result"
              :class="testResult.ok ? 'models__test-result--ok' : 'models__test-result--fail'"
              role="status"
            >
              {{ testResult.ok ? '连接成功' : '连接失败' }}：{{ testResult.message }}
            </p>
          </section>
        </template>
      </div>

      <!-- A02 右侧 ~32%：Prompt 检查器（编辑 Prompt 时打开） -->
      <aside v-if="promptEditorVisible" class="models__inspector" aria-label="Prompt 配置检查器">
        <div class="models__inspector-head">
          <h2 class="models__inspector-title">{{ promptEditorTitle }}</h2>
          <button
            type="button"
            class="models__inspector-close"
            aria-label="关闭"
            @click="cancelPromptEditor"
          >
            ×
          </button>
        </div>

        <div class="models__prompt-fields">
          <template v-if="promptEditorScene === 'WRITING'">
            <div v-for="slot in PROMPT_SLOTS" :key="slot.key" class="models__prompt-field">
              <label class="models__prompt-label">{{ slot.label }}</label>
              <el-input
                v-model="promptEditorDraft.slots[slot.key]"
                type="textarea"
                :rows="5"
                class="models__prompt-textarea"
                placeholder="输入 Prompt 内容，留空表示不使用该槽位"
                @input="markPromptDirty"
              />
            </div>
            <p class="models__prompt-hint">
              四个槽位内容保存为 JSON；全部留空并保存等同于恢复默认 Prompt
            </p>
          </template>
          <div v-else class="models__prompt-field">
            <label class="models__prompt-label">Prompt 内容</label>
            <el-input
              v-model="promptEditorDraft.text"
              type="textarea"
              :rows="8"
              class="models__prompt-textarea"
              placeholder="输入 Prompt 内容，留空并保存等同于恢复默认"
              @input="markPromptDirty"
            />
          </div>
        </div>

        <div class="models__inspector-footer">
          <el-button :loading="savingScenes.has(promptEditorScene)" @click="restorePromptDefault">
            恢复默认
          </el-button>
          <el-button @click="cancelPromptEditor">取消</el-button>
          <el-button
            type="primary"
            :loading="savingScenes.has(promptEditorScene)"
            @click="savePromptEditor"
          >
            保存
          </el-button>
        </div>
      </aside>
    </div>
  </main>
</template>

<style scoped>
.models {
  width: 100%;
  padding: var(--xl-space-8) var(--xl-content-pad);
}

.models__title {
  margin: 0;
  color: var(--xl-text-primary);
  font-size: 24px;
}

.models__hint {
  margin: var(--xl-space-1) 0 var(--xl-space-6);
  color: var(--xl-text-muted);
  font-size: 14px;
}

/* A02 主从：左侧 ~68% 主区 + 右侧 ~32% Prompt 检查器 */
.models__layout {
  display: flex;
  align-items: flex-start;
  gap: var(--xl-space-6);
}

.models__main {
  flex: 1;
  min-width: 0;
}

.models__state {
  padding: 48px 0;
  text-align: center;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
}

.models__state-icon {
  display: block;
  margin-bottom: var(--xl-space-3);
  font-size: 42px;
  color: var(--xl-text-muted);
}

.models__state p {
  margin: 0;
}

.models__state :deep(.el-skeleton) {
  text-align: left;
}

.models__table {
  width: 100%;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
  overflow: hidden;
}

.models__table :deep(th.el-table__cell) {
  color: var(--xl-text-secondary);
  font-weight: 600;
}

.models__select {
  width: 100%;
}

.models__model-input {
  max-width: 220px;
}

.models__quota-input {
  width: 120px;
}

.models__test {
  margin-top: var(--xl-space-6);
  padding: var(--xl-space-6);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
}

.models__test-title {
  margin: 0 0 var(--xl-space-4);
  color: var(--xl-text-primary);
  font-size: 18px;
}

.models__test-form {
  display: flex;
  flex-wrap: wrap;
  gap: var(--xl-space-3);
}

.models__test-provider {
  width: 140px;
}

.models__test-model {
  flex: 1;
  min-width: 160px;
}

.models__test-result {
  margin: var(--xl-space-4) 0 0;
  font-size: 14px;
}

.models__test-result--ok {
  color: var(--xl-color-success);
}

.models__test-result--fail {
  color: var(--xl-color-danger);
}

/* A02 右侧 Prompt 检查器 ~32% */
.models__inspector {
  width: 32%;
  min-width: 320px;
  position: sticky;
  top: var(--xl-space-6);
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-4);
  padding: var(--xl-space-6);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
}

.models__inspector-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--xl-space-2);
}

.models__inspector-title {
  margin: 0;
  color: var(--xl-text-primary);
  font-size: 18px;
  font-weight: 600;
}

.models__inspector-close {
  border: none;
  background: transparent;
  color: var(--xl-text-muted);
  font-size: 24px;
  line-height: 1;
  cursor: pointer;
}

.models__inspector-close:hover {
  color: var(--xl-text-primary);
}

.models__prompt-fields {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-4);
}

.models__prompt-field {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-2);
}

.models__prompt-label {
  color: var(--xl-text-secondary);
  font-size: 14px;
  font-weight: 600;
}

.models__prompt-textarea {
  width: 100%;
}

.models__prompt-hint {
  margin: 0;
  color: var(--xl-text-muted);
  font-size: var(--xl-fs-caption);
}

.models__inspector-footer {
  display: flex;
  flex-wrap: wrap;
  gap: var(--xl-space-3);
  margin-top: auto;
  padding-top: var(--xl-space-4);
  border-top: 1px solid var(--xl-border);
}
</style>
