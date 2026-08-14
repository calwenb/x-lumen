<script setup lang="ts">
// 知识库发现页（B21，F-0308，决策 D16）：公开库卡片墙的 MVP 形态——「我的知识库」卡片墙。
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
      <!-- 说明：MVP 展示「我的知识库」；全平台公开库聚合列表（发现页公开卡片墙）由 V2 提供 -->
      <p class="kb-discovery__desc">
        这里是知识平台化的入口——知识按「库 → 目录 → 知识」组织。
        当前展示我的知识库；全平台公开知识库聚合将在 V2 提供。
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
    <div v-else class="kb-discovery__grid">
      <article v-for="kb in kbs" :key="kb.id" class="kb-tile">
        <RouterLink class="kb-tile__link" :to="`/kb/${kb.id}`">
          <div
            class="kb-tile__cover"
            :class="`kb-tile__cover--${kb.visibility === 1 ? 'public' : 'private'}`"
          >
            <span class="kb-tile__cover-text">{{ kb.name.slice(0, 1) }}</span>
          </div>
          <div class="kb-tile__body">
            <div class="kb-tile__name-row">
              <h2 class="kb-tile__name">{{ kb.name }}</h2>
              <el-tag :type="kb.visibility === 1 ? 'success' : 'info'" effect="plain" size="small">
                <el-icon class="kb-tile__tag-icon">
                  <Lock v-if="kb.visibility === 0" />
                  <Unlock v-else />
                </el-icon>
                {{ kb.visibility === 1 ? '公开' : '私有' }}
              </el-tag>
            </div>
            <p class="kb-tile__intro">{{ kb.intro || '暂无简介' }}</p>
            <div class="kb-tile__meta">
              <span>知识 {{ kb.knowledgeCount }}</span>
            </div>
          </div>
        </RouterLink>
        <div class="kb-tile__actions">
          <button type="button" class="kb-tile__action" @click="openEdit(kb)">
            <el-icon><Edit /></el-icon>
            编辑
          </button>
          <button
            type="button"
            class="kb-tile__action kb-tile__action--danger"
            @click="handleDelete(kb)"
          >
            删除
          </button>
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
  max-width: 1080px;
  margin: 0 auto;
  padding: var(--xl-space-6) var(--xl-space-4) var(--xl-space-8);
}

.kb-discovery__header {
  position: relative;
  margin-bottom: var(--xl-space-6);
}

.kb-discovery__title {
  margin: 0 0 var(--xl-space-2);
  color: var(--xl-text-primary);
  font-size: 24px;
}

.kb-discovery__desc {
  max-width: 640px;
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.kb-discovery__create {
  position: absolute;
  top: 0;
  right: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  border-radius: var(--xl-radius);
  background: var(--xl-color-primary);
  color: #fff;
  font-size: 14px;
  text-decoration: none;
}

.kb-discovery__create:hover {
  background: var(--xl-color-primary-hover);
}

.kb-discovery__state {
  padding: var(--xl-space-8) 0;
  text-align: center;
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.kb-discovery__state p {
  margin: 0 0 var(--xl-space-3);
}

.kb-discovery__state-icon {
  display: block;
  margin: 0 auto var(--xl-space-3);
  font-size: 40px;
  color: var(--xl-text-muted);
}

.kb-discovery__skeleton {
  height: 180px;
  border-radius: var(--xl-radius-card);
  background: color-mix(in srgb, var(--xl-border) 60%, transparent);
}

.kb-discovery__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--xl-space-4);
}

.kb-tile {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
  transition:
    box-shadow var(--xl-transition),
    transform var(--xl-transition);
}

.kb-tile:hover {
  box-shadow: var(--xl-shadow-md);
  transform: translateY(-2px);
}

.kb-tile__link {
  display: flex;
  flex-direction: column;
  text-decoration: none;
}

.kb-tile__cover {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88px;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--xl-color-primary) 22%, transparent),
    transparent
  );
}

.kb-tile__cover--private {
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--xl-text-muted) 30%, transparent),
    transparent
  );
}

.kb-tile__cover-text {
  color: color-mix(in srgb, var(--xl-color-primary) 70%, var(--xl-text-primary));
  font-size: 36px;
  font-weight: 700;
}

.kb-tile__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: var(--xl-space-2);
  padding: var(--xl-space-4);
}

.kb-tile__name-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--xl-space-2);
}

.kb-tile__name {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: var(--xl-text-primary);
  font-size: 16px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-tile__tag-icon {
  margin-right: 4px;
  vertical-align: -2px;
}

.kb-tile__intro {
  flex: 1;
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: 13px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.kb-tile__meta {
  color: var(--xl-text-muted);
  font-size: 12px;
}

.kb-tile__actions {
  display: flex;
  gap: var(--xl-space-2);
  padding: 0 var(--xl-space-4) var(--xl-space-4);
}

.kb-tile__action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-sm);
  background: transparent;
  color: var(--xl-text-secondary);
  font-size: 12px;
  cursor: pointer;
}

.kb-tile__action:hover {
  border-color: var(--xl-color-primary);
  color: var(--xl-color-primary);
}

.kb-tile__action--danger:hover {
  border-color: var(--xl-color-danger);
  color: var(--xl-color-danger);
}

@media (width <= 640px) {
  .kb-discovery__create {
    position: static;
    margin-top: var(--xl-space-3);
  }
}
</style>
