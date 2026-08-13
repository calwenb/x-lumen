<script setup lang="ts">
// A03 模型配置：场景模型表格（供应商下拉 + 模型输入 + 保存）与连通性测试。
// 关键状态：加载骨架、失败重试、空态、保存中、测试中、结果提示。
import { onMounted, reactive, ref } from 'vue'

import {
  PROVIDER_OPTIONS,
  SCENE_LABELS,
  fetchModelConfigs,
  testModelConfig,
  updateModelConfig,
} from '../api/model'

import type { ModelConfig, ModelTestResult, ProviderValue } from '../api/model'

const configs = ref<ModelConfig[]>([])
const loading = ref(true)
const loadError = ref(false)

// 正在保存的场景集合（支持多行并发提交）
const savingScenes = reactive(new Set<string>())

const notice = ref('')
const noticeKind = ref<'success' | 'error'>('success')
let noticeTimer: number | undefined

const testProvider = ref<ProviderValue>('BAILIAN')
const testModel = ref('')
const testing = ref(false)
const testResult = ref<ModelTestResult | null>(null)

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

function showNotice(text: string, kind: 'success' | 'error'): void {
  notice.value = text
  noticeKind.value = kind
  window.clearTimeout(noticeTimer)
  noticeTimer = window.setTimeout(() => {
    notice.value = ''
  }, 3000)
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    configs.value = await fetchModelConfigs()
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

async function save(item: ModelConfig): Promise<void> {
  savingScenes.add(item.scene)
  notice.value = ''
  try {
    const updated = await updateModelConfig(item.scene, {
      provider: item.provider,
      model: item.model,
      ...(item.paramsJson ? { paramsJson: item.paramsJson } : {}),
    })
    Object.assign(item, updated)
    showNotice('已保存', 'success')
  } catch {
    showNotice('保存失败，请稍后重试', 'error')
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
    <p class="models__hint">API Key 在服务器 .env 配置，界面不展示</p>

    <p
      v-if="notice"
      class="models__notice"
      :class="noticeKind === 'error' ? 'models__notice--error' : 'models__notice--success'"
      role="status"
    >
      {{ notice }}
    </p>

    <div v-if="loading" class="models__skeleton" role="status">加载中…</div>
    <div v-else-if="loadError" class="models__error">
      <p>加载失败，请稍后重试。</p>
      <button type="button" class="models__retry" @click="load()">重试</button>
    </div>
    <div v-else-if="configs.length === 0" class="models__empty">暂无场景配置</div>
    <template v-else>
      <div class="models__table-wrap">
        <table class="models__table">
          <thead>
            <tr>
              <th>场景</th>
              <th>供应商</th>
              <th>模型</th>
              <th>更新时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in configs" :key="item.scene">
              <td>{{ SCENE_LABELS[item.scene] ?? item.scene }}</td>
              <td>
                <select v-model="item.provider" class="models__select" aria-label="供应商">
                  <option v-for="opt in providerOptions(item.provider)" :key="opt.value" :value="opt.value">
                    {{ opt.label }}
                  </option>
                </select>
              </td>
              <td>
                <input v-model="item.model" class="models__input" placeholder="模型名称" />
              </td>
              <td>{{ formatTime(item.updatedAt) }}</td>
              <td>
                <button
                  type="button"
                  class="models__save"
                  :disabled="savingScenes.has(item.scene)"
                  @click="save(item)"
                >
                  {{ savingScenes.has(item.scene) ? '保存中…' : '保存' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <section class="models__test">
        <h2 class="models__test-title">连通性测试</h2>
        <div class="models__test-form">
          <select v-model="testProvider" class="models__select" aria-label="测试供应商">
            <option v-for="opt in PROVIDER_OPTIONS" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </option>
          </select>
          <input v-model="testModel" class="models__input" placeholder="模型名称" />
          <button
            type="button"
            class="models__test-run"
            :disabled="testing || testModel.trim() === ''"
            @click="runTest"
          >
            {{ testing ? '测试中…' : '测试' }}
          </button>
        </div>
        <p
          v-if="testResult"
          class="models__test-result"
          :class="testResult.ok ? 'models__test-result--ok' : 'models__test-result--fail'"
        >
          {{ testResult.ok ? '连接成功' : '连接失败' }}：{{ testResult.message }}
        </p>
      </section>
    </template>
  </main>
</template>

<style scoped>
.models {
  max-width: 960px;
  margin: 0 auto;
  padding: var(--xl-space-8) var(--xl-space-4);
}

.models__title {
  margin: 0;
  color: var(--xl-text-primary);
  font-size: 22px;
}

.models__hint {
  margin: var(--xl-space-1) 0 var(--xl-space-6);
  color: var(--xl-text-muted);
  font-size: 13px;
}

.models__notice {
  margin: 0 0 var(--xl-space-4);
  padding: var(--xl-space-2) var(--xl-space-3);
  border-radius: var(--xl-radius);
  font-size: 13px;
}

.models__notice--success {
  background: color-mix(in srgb, var(--xl-color-success) 12%, transparent);
  color: var(--xl-color-success);
}

.models__notice--error {
  background: color-mix(in srgb, var(--xl-color-danger) 12%, transparent);
  color: var(--xl-color-danger);
}

.models__skeleton,
.models__error,
.models__empty {
  padding: 48px 0;
  text-align: center;
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.models__retry {
  margin-top: var(--xl-space-3);
  padding: 6px 18px;
  border: 1px solid var(--xl-color-primary);
  border-radius: var(--xl-radius-sm);
  background: transparent;
  color: var(--xl-color-primary);
  cursor: pointer;
}

.models__table-wrap {
  overflow-x: auto;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
}

.models__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.models__table th,
.models__table td {
  padding: var(--xl-space-3) var(--xl-space-4);
  text-align: left;
  border-bottom: 1px solid var(--xl-border);
}

.models__table th {
  color: var(--xl-text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.models__table tbody tr:last-child td {
  border-bottom: none;
}

.models__select,
.models__input {
  padding: 6px 10px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-sm);
  background: var(--xl-bg-surface);
  color: var(--xl-text-primary);
  font-size: 13px;
}

.models__input {
  width: 100%;
  min-width: 140px;
}

.models__select:focus,
.models__input:focus {
  outline: none;
  border-color: var(--xl-color-primary);
}

.models__save {
  padding: 6px 14px;
  border: 1px solid var(--xl-color-primary);
  border-radius: var(--xl-radius-sm);
  background: transparent;
  color: var(--xl-color-primary);
  font-size: 13px;
  cursor: pointer;
}

.models__save:hover {
  background: color-mix(in srgb, var(--xl-color-primary) 8%, transparent);
}

.models__save:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.models__test {
  margin-top: var(--xl-space-6);
  padding: var(--xl-space-6);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
}

.models__test-title {
  margin: 0 0 var(--xl-space-4);
  color: var(--xl-text-primary);
  font-size: 16px;
}

.models__test-form {
  display: flex;
  flex-wrap: wrap;
  gap: var(--xl-space-3);
}

.models__test-form .models__input {
  flex: 1;
  min-width: 160px;
}

.models__test-run {
  padding: 6px 18px;
  border: none;
  border-radius: var(--xl-radius-sm);
  background: var(--xl-color-primary);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}

.models__test-run:hover {
  background: var(--xl-color-primary-hover);
}

.models__test-run:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.models__test-result {
  margin: var(--xl-space-4) 0 0;
  font-size: 13px;
}

.models__test-result--ok {
  color: var(--xl-color-success);
}

.models__test-result--fail {
  color: var(--xl-color-danger);
}
</style>
