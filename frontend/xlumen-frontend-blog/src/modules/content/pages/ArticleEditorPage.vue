<script setup lang="ts">
// 文章编辑页（B10，F-0301/F-0302/F-0307）：标题/分类/标签/可见性 + Markdown 编辑器。
// 草稿自动保存（10s 节流 + 失焦触发，F-0302）；显式保存走乐观锁版本校验，409 冲突提供恢复入口。
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { autosaveDraft, createArticle, fetchArticle, updateArticle } from '@/modules/content/api/article'
import MarkdownEditor from '@/modules/content/components/MarkdownEditor.vue'
import { useAutoSave } from '@/modules/content/composables/useAutoSave'

const route = useRoute()
const router = useRouter()

/** 新建模式（无 :id 参数）。 */
const isNew = computed(() => !route.params.id)

const articleId = ref<string | null>(null)
const version = ref('0')
const title = ref('')
const content = ref('')
const category = ref('')
const tagsInput = ref('')
const visibility = ref(1)

const loading = ref(true)
const loadError = ref(false)
const saving = ref(false)
const saveMessage = ref('')

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

/** 自动保存回调：幂等提交，返回最新 ID/版本。 */
async function doAutoSave(): Promise<{ id: string; version: string } | null> {
  if (!title.value.trim()) {
    return null
  }
  const saved = await autosaveDraft({
    ...(articleId.value ? { articleId: articleId.value, version: version.value } : {}),
    title: title.value,
    content: content.value,
    category: category.value,
    tags: tags.value,
    visibility: visibility.value,
  })
  articleId.value = saved.id
  version.value = saved.version
  lastSaved.value = content.value
  lastTitle.value = title.value
  return { id: saved.id, version: saved.version }
}

const autoSave = useAutoSave(isDirty, doAutoSave)

/** 冲突提示状态。 */
const conflict = ref(false)

async function handleConflict(): Promise<void> {
  // 409 恢复入口：重新拉取服务端最新内容覆盖本地
  if (articleId.value) {
    const latest = await fetchArticle(articleId.value)
    title.value = latest.title
    content.value = latest.content
    category.value = latest.category
    tagsInput.value = latest.tags.join(', ')
    visibility.value = latest.visibility
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
  saving.value = true
  conflict.value = false
  saveMessage.value = ''
  try {
    if (isNew.value && !articleId.value) {
      const created = await createArticle({
        title: title.value,
        content: content.value,
        category: category.value,
        tags: tags.value,
        visibility: visibility.value,
      })
      articleId.value = created.id
      version.value = created.version
      lastSaved.value = content.value
      lastTitle.value = title.value
      await router.replace({ name: 'article-edit', params: { id: created.id } })
    } else if (articleId.value) {
      const updated = await updateArticle(articleId.value, version.value, {
        title: title.value,
        content: content.value,
        category: category.value,
        tags: tags.value,
        visibility: visibility.value,
      })
      version.value = updated.version
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

onMounted(async () => {
  if (!isNew.value) {
    try {
      const article = await fetchArticle(String(route.params.id))
      articleId.value = article.id
      version.value = article.version
      title.value = article.title
      content.value = article.content
      category.value = article.category
      tagsInput.value = article.tags.join(', ')
      visibility.value = article.visibility
      lastSaved.value = article.content
      lastTitle.value = article.title
    } catch {
      loadError.value = true
    }
  } else {
    lastSaved.value = ''
    lastTitle.value = ''
  }
  loading.value = false
})
</script>

<template>
  <main class="editor-page">
    <div class="editor-page__header">
      <h1 class="editor-page__title">{{ isNew ? '新建文章' : '编辑文章' }}</h1>
      <div class="editor-page__actions">
        <span class="editor-page__status" :class="{ 'editor-page__status--conflict': conflict }">
          {{ conflict ? '版本冲突' : autoSave.saving.value || saving ? '保存中…' : autoSave.savedAt.value ? '已自动保存' : '' }}
        </span>
        <button type="button" class="editor-page__save" :disabled="saving" @click="handleSave">保存</button>
        <RouterLink class="editor-page__back" :to="{ name: 'article-list' }">返回列表</RouterLink>
      </div>
    </div>

    <div v-if="loading" class="editor-page__loading" role="status">加载中…</div>
    <div v-else-if="loadError" class="editor-page__error">
      <p>文章加载失败，可能不存在或已被删除。</p>
      <RouterLink class="editor-page__back" :to="{ name: 'article-list' }">返回列表</RouterLink>
    </div>
    <template v-else>
      <div v-if="conflict" class="editor-page__conflict" role="alert">
        <p>文章在其他地方已被修改，为避免覆盖，请选择：</p>
        <button type="button" class="editor-page__conflict-action" @click="handleConflict">加载服务端最新版本</button>
      </div>
      <div v-if="saveMessage" class="editor-page__message" role="status">{{ saveMessage }}</div>

      <section class="editor-page__fields">
        <input v-model="title" class="editor-page__title-input" type="text" placeholder="文章标题" aria-label="文章标题" @input="autoSave.touch()" />
        <div class="editor-page__row">
          <input v-model="category" class="editor-page__category" type="text" placeholder="分类（如：后端）" aria-label="分类" @input="autoSave.touch()" />
          <input v-model="tagsInput" class="editor-page__tags" type="text" placeholder="标签（逗号分隔，如：Spring, Vue）" aria-label="标签" @input="autoSave.touch()" />
        </div>
        <div class="editor-page__row" role="radiogroup" aria-label="可见性">
          <span class="editor-page__label">可见性：</span>
          <label class="editor-page__radio">
            <input v-model="visibility" type="radio" :value="1" @change="autoSave.touch()" /> 公开
          </label>
          <label class="editor-page__radio">
            <input v-model="visibility" type="radio" :value="0" @change="autoSave.touch()" /> 私有
          </label>
          <span class="editor-page__hint">私有不进公开列表与搜索，但参与「小光」AI 问答检索</span>
        </div>
      </section>

      <MarkdownEditor v-model="content" class="editor-page__editor" @update:model-value="autoSave.touch()" />
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
  color: var(--xl-color-danger, #d03050);
}

.editor-page__save {
  padding: 7px 20px;
  border: none;
  border-radius: var(--xl-radius-sm, 6px);
  background: var(--xl-color-primary);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}

.editor-page__save:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.editor-page__save:hover:not(:disabled) {
  background: var(--xl-color-primary-hover);
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
  border: 1px solid var(--xl-color-danger, #d03050);
  border-radius: var(--xl-radius-sm, 6px);
  background: color-mix(in srgb, var(--xl-color-danger, #d03050) 6%, transparent);
  color: var(--xl-color-danger, #d03050);
  font-size: 13px;
}

.editor-page__conflict p {
  margin: 0 0 8px;
}

.editor-page__conflict-action {
  padding: 5px 14px;
  border: 1px solid var(--xl-color-danger, #d03050);
  border-radius: var(--xl-radius-sm, 6px);
  background: transparent;
  color: var(--xl-color-danger, #d03050);
  font-size: 13px;
  cursor: pointer;
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

.editor-page__title-input {
  padding: 12px 14px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-sm, 6px);
  background: var(--xl-bg-secondary);
  color: var(--xl-text-primary);
  font-size: 18px;
  outline: none;
}

.editor-page__title-input:focus {
  border-color: var(--xl-color-primary);
}

.editor-page__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.editor-page__category,
.editor-page__tags {
  flex: 1;
  min-width: 200px;
  padding: 8px 12px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-sm, 6px);
  background: var(--xl-bg-secondary);
  color: var(--xl-text-primary);
  font-size: 13px;
  outline: none;
}

.editor-page__label {
  font-size: 13px;
  color: var(--xl-text-secondary);
}

.editor-page__radio {
  font-size: 13px;
  color: var(--xl-text-primary);
}

.editor-page__hint {
  font-size: 12px;
  color: var(--xl-text-secondary);
}
</style>
