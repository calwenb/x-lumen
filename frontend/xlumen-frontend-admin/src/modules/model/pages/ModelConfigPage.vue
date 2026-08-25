<script setup lang="ts">
// A03 模型配置：场景模型表格（供应商下拉 + 模型输入 + 保存）与连通性测试。
// 关键状态：加载骨架、失败重试、空态、保存中、测试中、结果提示。
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'

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
  try {
    const updated = await updateModelConfig(item.scene, {
      provider: item.provider,
      model: item.model,
      ...(item.paramsJson ? { paramsJson: item.paramsJson } : {}),
    })
    Object.assign(item, updated)
    ElMessage.success('已保存')
  } catch {
    ElMessage.error('保存失败，请稍后重试')
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
          <el-select v-model="testProvider" class="models__test-provider" aria-label="测试供应商">
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

.models__state {
  padding: 48px 0;
  text-align: center;
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.models__state-icon {
  display: block;
  margin-bottom: var(--xl-space-3);
  font-size: 40px;
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
  font-size: 16px;
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
  font-size: 13px;
}

.models__test-result--ok {
  color: var(--xl-color-success);
}

.models__test-result--fail {
  color: var(--xl-color-danger);
}
</style>
