<script setup lang="ts">
// A02 空间设置：空间名/slug 只读展示，intro 与 forceReview 可编辑保存。
// 关键状态：加载骨架、失败重试、提交中、保存成功提示。
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import { fetchWorkspaceSettings, updateWorkspaceSettings } from '../api/workspace'

const loading = ref(true)
const loadError = ref(false)
const saving = ref(false)
const saved = ref(false)

const name = ref('')
const slug = ref('')
const intro = ref('')
const forceReview = ref(false)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    const settings = await fetchWorkspaceSettings()
    name.value = settings.name
    slug.value = settings.slug
    intro.value = settings.intro
    forceReview.value = settings.forceReview
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

async function save(): Promise<void> {
  saving.value = true
  saved.value = false
  try {
    const updated = await updateWorkspaceSettings({
      intro: intro.value,
      forceReview: forceReview.value,
    })
    // 以服务端回写为准刷新可编辑字段
    intro.value = updated.intro
    forceReview.value = updated.forceReview
    saved.value = true
    ElMessage.success('已保存')
  } catch {
    ElMessage.error('保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void load()
})

// 保存成功后再次编辑任一字段，清除"已保存"提示
watch([intro, forceReview], () => {
  saved.value = false
})
</script>

<template>
  <main class="settings">
    <h1 class="settings__title">空间设置</h1>

    <div v-if="loading" class="settings__state" role="status">
      <el-skeleton :rows="5" animated />
    </div>
    <div v-else-if="loadError" class="settings__state">
      <p>加载失败，请稍后重试。</p>
      <el-button type="primary" plain @click="load()">重试</el-button>
    </div>
    <el-form v-else label-position="top" class="settings__card" @submit.prevent="save">
      <el-form-item label="空间名">
        <el-input :model-value="name" disabled />
      </el-form-item>
      <el-form-item label="空间标识（slug）">
        <el-input :model-value="slug" disabled class="settings__slug" />
      </el-form-item>
      <el-form-item label="空间简介">
        <el-input
          v-model="intro"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          placeholder="介绍你的空间…"
        />
      </el-form-item>
      <el-form-item label="发布审核">
        <div class="settings__switch-row">
          <el-switch v-model="forceReview" aria-label="开启发布审核" />
          <span class="settings__switch-hint">开启后，成员发布内容需管理员审核</span>
        </div>
      </el-form-item>
      <div class="settings__actions">
        <el-button type="primary" native-type="submit" :loading="saving">
          {{ saving ? '保存中…' : '保存' }}
        </el-button>
        <span v-if="saved" class="settings__saved" role="status">已保存</span>
      </div>
    </el-form>
  </main>
</template>

<style scoped>
.settings {
  max-width: 720px;
  margin: 0 auto;
  padding: var(--xl-space-8) var(--xl-space-4);
}

.settings__title {
  margin: 0 0 var(--xl-space-6);
  color: var(--xl-text-primary);
  font-size: 22px;
}

.settings__state {
  padding: 48px 0;
  text-align: center;
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.settings__state :deep(.el-skeleton) {
  text-align: left;
}

.settings__card {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-4);
  padding: var(--xl-space-6);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
}

.settings__slug :deep(.el-input__inner) {
  font-family: var(--xl-font-mono);
}

.settings__switch-row {
  display: flex;
  align-items: center;
  gap: var(--xl-space-3);
}

.settings__switch-hint {
  color: var(--xl-text-secondary);
  font-size: 13px;
}

.settings__actions {
  display: flex;
  align-items: center;
  gap: var(--xl-space-4);
}

.settings__saved {
  color: var(--xl-color-success);
  font-size: 13px;
}
</style>
