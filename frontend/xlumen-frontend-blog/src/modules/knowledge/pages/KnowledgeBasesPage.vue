<script setup lang="ts">
// 知识库发现页（B21，决策 D16）：公开库卡片墙的 MVP 形态——「我的知识库」卡片墙。
// 数据来源：后端暂无全平台公开库聚合接口（KB-3 未做发现页接口），登录后 fetchKnowledgeBases
// 展示我的全部知识库（含私有，🔒 标注）；全平台公开库聚合列表由 V2 提供（见下方说明注释）。
// 卡片点击进入库页 /kb/:id；编辑/删除为库主入口操作（二次确认）。
import { onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Collection, Edit, Lock, Plus, Unlock } from '@element-plus/icons-vue'

import { useSessionStore } from '@/stores/session'
import {
  deleteKnowledgeBase,
  fetchKnowledgeBases,
  updateKnowledgeBase,
} from '@/modules/knowledge/api/knowledgeBase'

import type { KnowledgeBase } from '@/modules/knowledge/api/knowledgeBase'

const router = useRouter()
const session = useSessionStore()

const kbs = ref<KnowledgeBase[]>([])
const loading = ref(true)
const loadError = ref(false)

const editVisible = ref(false)
const editTarget = ref<KnowledgeBase | null>(null)
const editForm = ref({ name: '', intro: '', cover: '' })

async function load(): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    kbs.value = await fetchKnowledgeBases()
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function openEdit(kb: KnowledgeBase): void {
  editTarget.value = kb
  editForm.value = { name: kb.name, intro: kb.intro, cover: kb.cover }
  editVisible.value = true
}

/** 编辑库资料：name 必填。 */
async function submitEdit(): Promise<void> {
  const target = editTarget.value
  if (!target) return
  const name = editForm.value.name.trim()
  if (!name) {
    ElMessage.warning('库名不能为空')
    return
  }
  try {
    await updateKnowledgeBase(target.id, {
      name,
      intro: editForm.value.intro.trim(),
      ...(editForm.value.cover.trim() ? { cover: editForm.value.cover.trim() } : {}),
    })
    editTarget.value = null
    editVisible.value = false
    ElMessage.success('库资料已更新')
    await load()
  } catch {
    ElMessage.error('更新失败，请重试')
  }
}

/** 删除知识库（二次确认，库内知识一并移入回收站）。 */
async function handleDelete(kb: KnowledgeBase): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除「${kb.name}」吗？库内 ${kb.knowledgeCount} 篇知识将一并移入回收站，可在回收站恢复。`,
      '删除知识库',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteKnowledgeBase(kb.id)
    ElMessage.success('已删除')
    await load()
  } catch {
    ElMessage.error('删除失败，请重试')
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <main class="kb-discovery">
    <header class="kb-discovery__header">
      <h1 class="kb-discovery__title">知识库</h1>
      <!-- 说明：MVP 展示「我的知识库」；全平台公开库聚合见「发现」页公开知识流 -->
      <p class="kb-discovery__desc">
        这里是知识平台化的入口——知识按「库 → 目录 → 知识」组织。
        当前展示我的知识库；全平台公开知识库聚合见「发现」页。
      </p>
      <RouterLink v-if="session.loggedIn" class="kb-discovery__create" to="/studio/knowledge-bases">
        <el-icon><Plus /></el-icon>
        新建知识库
      </RouterLink>
    </header>

    <div v-if="!session.loggedIn" class="kb-discovery__state">
      <el-icon class="kb-discovery__state-icon"><Collection /></el-icon>
      <p class="kb-discovery__state-text">登录后查看和管理我的知识库。</p>
      <el-button
        type="primary"
        plain
        @click="router.push({ name: 'login', query: { redirect: '/knowledge-bases' } })"
      >
        去登录
      </el-button>
    </div>
    <div v-else-if="loading" class="kb-discovery__state">
      <div v-for="i in 4" :key="i" class="kb-discovery__skeleton" aria-hidden="true" />
    </div>
    <div v-else-if="loadError" class="kb-discovery__state">
      <p class="kb-discovery__state-text">知识库加载失败</p>
      <el-button type="primary" plain @click="load">重试</el-button>
    </div>
    <div v-else-if="kbs.length === 0" class="kb-discovery__state">
      <el-icon class="kb-discovery__state-icon"><Collection /></el-icon>
      <p class="kb-discovery__state-text">还没有知识库，点击右上角「新建知识库」开始。</p>
    </div>
    <!-- 书架索引：纵向列表，不使用等宽卡片网格 -->
    <div v-else class="kb-shelf">
      <article v-for="kb in kbs" :key="kb.id" class="kb-shelf__item">
        <RouterLink class="kb-shelf__cover" :to="`/kb/${kb.id}`">
          <span class="kb-shelf__cover-text">{{ kb.name.slice(0, 1) }}</span>
        </RouterLink>
        <div class="kb-shelf__body">
          <div class="kb-shelf__name-row">
            <RouterLink class="kb-shelf__name" :to="`/kb/${kb.id}`">{{ kb.name }}</RouterLink>
            <el-tag :type="kb.visibility === 1 ? 'success' : 'info'" effect="plain" size="small">
              <el-icon class="kb-shelf__tag-icon">
                <Lock v-if="kb.visibility === 0" />
                <Unlock v-else />
              </el-icon>
              {{ kb.visibility === 1 ? '公开' : '私有' }}
            </el-tag>
          </div>
          <p class="kb-shelf__intro">{{ kb.intro || '暂无简介' }}</p>
        </div>
        <div class="kb-shelf__meta">
          <span class="kb-shelf__count">知识 {{ kb.knowledgeCount }}</span>
          <div class="kb-shelf__actions">
            <button type="button" class="kb-shelf__action" @click="openEdit(kb)">
              <el-icon><Edit /></el-icon>
              编辑
            </button>
            <button
              type="button"
              class="kb-shelf__action kb-shelf__action--danger"
              @click="handleDelete(kb)"
            >
              删除
            </button>
          </div>
        </div>
      </article>
    </div>

    <el-dialog v-model="editVisible" title="编辑库资料" width="440px">
      <el-form label-position="top">
        <el-form-item label="库名">
          <el-input v-model="editForm.name" maxlength="40" placeholder="知识库名称" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="editForm.intro" type="textarea" :rows="3" maxlength="200" />
        </el-form-item>
        <el-form-item label="封面地址（可选）">
          <el-input v-model="editForm.cover" placeholder="https://…" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.kb-discovery {
  width: min(calc(100% - 48px), var(--xl-container));
  margin: 0 auto;
  padding: var(--xl-space-8) var(--xl-content-pad) var(--xl-space-8);
  box-sizing: border-box;
}

.kb-discovery__header {
  position: relative;
  margin-bottom: var(--xl-space-8);
}

.kb-discovery__title {
  margin: 0 0 var(--xl-space-3);
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-h1);
  font-weight: var(--xl-fs-h1-w);
  line-height: var(--xl-fs-h1-lh);
  letter-spacing: var(--xl-fs-h1-track);
}

.kb-discovery__desc {
  max-width: 640px;
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
  line-height: 1.7;
}

.kb-discovery__create {
  position: absolute;
  top: 4px;
  right: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  border-radius: var(--xl-radius);
  background: var(--xl-color-primary);
  color: #fff;
  font-size: var(--xl-fs-body);
  text-decoration: none;
  transition: background var(--xl-transition);
}

.kb-discovery__create:hover {
  background: var(--xl-color-primary-hover);
}

/* ===== 状态区 ===== */
.kb-discovery__state {
  padding: var(--xl-space-10) 0;
  text-align: center;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
}

.kb-discovery__state p {
  margin: 0 0 var(--xl-space-3);
}

.kb-discovery__state-icon {
  display: block;
  margin: 0 auto var(--xl-space-3);
  font-size: 42px;
  color: var(--xl-text-muted);
}

.kb-discovery__skeleton {
  height: 96px;
  margin-bottom: var(--xl-space-4);
  border-radius: var(--xl-radius-card);
  background: color-mix(in srgb, var(--xl-border) 60%, transparent);
}

/* ===== 书架索引 ===== */
.kb-shelf {
  display: flex;
  flex-direction: column;
}

.kb-shelf__item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: var(--xl-space-6);
  align-items: center;
  padding: var(--xl-space-6) 0;
  border-bottom: 1px solid var(--xl-border);
}

.kb-shelf__item:first-child {
  border-top: 1px solid var(--xl-border);
}

.kb-shelf__cover {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 84px;
  height: 84px;
  flex-shrink: 0;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--xl-color-primary) 16%, transparent),
    var(--xl-bg-surface)
  );
  text-decoration: none;
  transition: border-color var(--xl-transition);
}

.kb-shelf__cover:hover {
  border-color: var(--xl-color-primary);
}

.kb-shelf__cover-text {
  color: color-mix(in srgb, var(--xl-color-primary) 78%, var(--xl-text-primary));
  font-size: 32px;
  font-weight: 700;
}

.kb-shelf__body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-2);
}

.kb-shelf__name-row {
  display: flex;
  align-items: center;
  gap: var(--xl-space-3);
}

.kb-shelf__name {
  min-width: 0;
  overflow: hidden;
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-title);
  font-weight: var(--xl-fs-title-w);
  text-overflow: ellipsis;
  white-space: nowrap;
  text-decoration: none;
  transition: color var(--xl-transition);
}

.kb-shelf__name:hover {
  color: var(--xl-color-primary);
}

.kb-shelf__tag-icon {
  margin-right: 4px;
  vertical-align: -2px;
}

.kb-shelf__intro {
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.kb-shelf__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--xl-space-3);
  flex-shrink: 0;
}

.kb-shelf__count {
  color: var(--xl-text-muted);
  font-size: var(--xl-fs-caption);
}

.kb-shelf__actions {
  display: flex;
  gap: var(--xl-space-2);
}

.kb-shelf__action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-sm);
  background: transparent;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-caption);
  cursor: pointer;
  transition:
    border-color var(--xl-transition),
    color var(--xl-transition);
}

.kb-shelf__action:hover {
  border-color: var(--xl-color-primary);
  color: var(--xl-color-primary);
}

.kb-shelf__action--danger:hover {
  border-color: var(--xl-color-danger);
  color: var(--xl-color-danger);
}

@media (width <= 800px) {
  .kb-shelf__item {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .kb-shelf__meta {
    grid-column: 2;
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }

  .kb-discovery__create {
    position: static;
    margin-top: var(--xl-space-3);
  }
}
</style>
