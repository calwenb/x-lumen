<script setup lang="ts">
// A02 空间设置：空间名/slug 只读展示，intro 与 forceReview 可编辑保存。
// 关键状态：加载骨架、失败重试、提交中、保存成功提示。
import { onMounted, ref } from 'vue'

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
  } catch {
    window.alert('保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <main class="settings">
    <h1 class="settings__title">空间设置</h1>

    <div v-if="loading" class="settings__skeleton" role="status">加载中…</div>
    <div v-else-if="loadError" class="settings__error">
      <p>加载失败，请稍后重试。</p>
      <button type="button" class="settings__retry" @click="load()">重试</button>
    </div>
    <form v-else class="settings__card" @submit.prevent="save">
      <div class="settings__field settings__field--readonly">
        <span class="settings__label">空间名</span>
        <span class="settings__value">{{ name }}</span>
      </div>
      <div class="settings__field settings__field--readonly">
        <span class="settings__label">空间标识（slug）</span>
        <span class="settings__value settings__value--mono">{{ slug }}</span>
      </div>
      <label class="settings__field">
        <span class="settings__label">空间简介</span>
        <textarea
          v-model="intro"
          class="settings__textarea"
          rows="4"
          maxlength="500"
          placeholder="介绍你的空间…"
        ></textarea>
      </label>
      <div class="settings__field settings__field--switch">
        <span class="settings__label">发布审核</span>
        <span class="settings__switch-row">
          <input
            v-model="forceReview"
            type="checkbox"
            class="settings__switch-input"
            aria-label="开启发布审核"
          />
          <span class="settings__switch-hint">开启后，成员发布内容需管理员审核</span>
        </span>
      </div>
      <div class="settings__actions">
        <button type="submit" class="settings__submit" :disabled="saving">
          {{ saving ? '保存中…' : '保存' }}
        </button>
        <span v-if="saved" class="settings__saved" role="status">已保存</span>
      </div>
    </form>
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

.settings__skeleton,
.settings__error {
  padding: 48px 0;
  text-align: center;
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.settings__retry {
  margin-top: var(--xl-space-3);
  padding: 6px 18px;
  border: 1px solid var(--xl-color-primary);
  border-radius: var(--xl-radius-sm);
  background: transparent;
  color: var(--xl-color-primary);
  cursor: pointer;
}

.settings__card {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-6);
  padding: var(--xl-space-6);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
}

.settings__field {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-2);
}

.settings__field--readonly {
  gap: var(--xl-space-1);
}

.settings__field--switch {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: var(--xl-space-4);
}

.settings__label {
  color: var(--xl-text-secondary);
  font-size: 13px;
}

.settings__value {
  color: var(--xl-text-primary);
  font-size: 15px;
}

.settings__value--mono {
  font-family: var(--xl-font-mono);
}

.settings__textarea {
  padding: var(--xl-space-2) var(--xl-space-3);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius);
  color: var(--xl-text-primary);
  font-size: 14px;
  resize: vertical;
}

.settings__textarea:focus {
  outline: none;
  border-color: var(--xl-color-primary);
}

.settings__switch-row {
  display: flex;
  align-items: center;
  gap: var(--xl-space-2);
}

.settings__switch-input {
  position: relative;
  width: 36px;
  height: 20px;
  margin: 0;
  appearance: none;
  border-radius: 999px;
  background: var(--xl-border);
  cursor: pointer;
  transition: background 0.2s;
}

.settings__switch-input::after {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--xl-bg-surface);
  content: '';
  transition: transform 0.2s;
}

.settings__switch-input:checked {
  background: var(--xl-color-primary);
}

.settings__switch-input:checked::after {
  transform: translateX(16px);
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

.settings__submit {
  padding: var(--xl-space-2) var(--xl-space-6);
  border: none;
  border-radius: var(--xl-radius);
  background: var(--xl-color-primary);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}

.settings__submit:hover {
  background: var(--xl-color-primary-hover);
}

.settings__submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.settings__saved {
  color: var(--xl-color-success);
  font-size: 13px;
}
</style>
