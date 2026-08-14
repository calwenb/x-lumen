<script setup lang="ts">
// 知识库管理页（B22，F-0308/F-0309，决策 D16）：我的知识库卡片墙 + 库资料编辑/可见性切换/删除 +
// 每卡片可展开的目录管理（目录树、新建/改名/删除）。需登录（路由 meta 守卫 + 页内兜底）。
// 破坏性操作一律二次确认：删库（知识一并入回收站）、删目录（知识上挂父目录）。
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Collection, Edit, Folder, Plus } from '@element-plus/icons-vue'

import { useSessionStore } from '@/stores/session'
import {
  changeKnowledgeBaseVisibility,
  createDirectory,
  createKnowledgeBase,
  deleteDirectory,
  deleteKnowledgeBase,
  fetchDirectoryTree,
  fetchKnowledgeBases,
  updateDirectory,
  updateKnowledgeBase,
} from '@/modules/knowledge/api/knowledgeBase'

import type { DirectoryNode, KnowledgeBase } from '@/modules/knowledge/api/knowledgeBase'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const kbs = ref<KnowledgeBase[]>([])
const loading = ref(true)
const loadError = ref(false)

/** 每卡片目录树缓存：kbId → 树（展开时按需加载）。 */
const trees = ref<Record<string, DirectoryNode[]>>({})
/** 当前展开「目录管理」的卡片（单展开）。 */
const expandedId = ref('')
const treeLoading = ref(false)

const createVisible = ref(false)
const createForm = reactive({ name: '', intro: '', visibility: 0 as 0 | 1 })

const editVisible = ref(false)
const editTarget = ref<KnowledgeBase | null>(null)
const editForm = reactive({ name: '', intro: '', cover: '' })

const dirVisible = ref(false)
const dirKbId = ref('')
const dirForm = reactive({ name: '', parentId: '' })

const renameVisible = ref(false)
const renameKbId = ref('')
const renameDirId = ref('')
const renameName = ref('')

const dirOptions = computed(() => flattenDirectories(trees.value[dirKbId.value] ?? []))

function flattenDirectories(nodes: DirectoryNode[], depth = 0): { id: string; label: string }[] {
  const out: { id: string; label: string }[] = []
  for (const node of nodes) {
    out.push({ id: node.id, label: `${'　'.repeat(depth)}${node.name}` })
    out.push(...flattenDirectories(node.children ?? [], depth + 1))
  }
  return out
}

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

/** 展开/收起卡片目录区；首次展开按需拉取目录树。 */
async function toggleExpand(kbId: string): Promise<void> {
  if (expandedId.value === kbId) {
    expandedId.value = ''
    return
  }
  expandedId.value = kbId
  if (!trees.value[kbId]) {
    treeLoading.value = true
    try {
      trees.value[kbId] = await fetchDirectoryTree(kbId)
    } catch {
      ElMessage.error('目录加载失败')
    } finally {
      treeLoading.value = false
    }
  }
}

/** 新建知识库：名称必填，可见性默认私有。 */
async function submitCreate(): Promise<void> {
  const name = createForm.name.trim()
  if (!name) {
    ElMessage.warning('库名不能为空')
    return
  }
  try {
    await createKnowledgeBase({
      name,
      intro: createForm.intro.trim(),
      visibility: createForm.visibility,
    })
    createVisible.value = false
    createForm.name = ''
    createForm.intro = ''
    createForm.visibility = 0
    ElMessage.success('知识库已创建')
    await load()
  } catch {
    ElMessage.error('创建失败，请重试')
  }
}

function openEdit(kb: KnowledgeBase): void {
  editTarget.value = kb
  editForm.name = kb.name
  editForm.intro = kb.intro
  editForm.cover = kb.cover
  editVisible.value = true
}

async function submitEdit(): Promise<void> {
  const target = editTarget.value
  if (!target) return
  const name = editForm.name.trim()
  if (!name) {
    ElMessage.warning('库名不能为空')
    return
  }
  try {
    await updateKnowledgeBase(target.id, {
      name,
      intro: editForm.intro.trim(),
      ...(editForm.cover.trim() ? { cover: editForm.cover.trim() } : {}),
    })
    editVisible.value = false
    ElMessage.success('库资料已更新')
    await load()
  } catch {
    ElMessage.error('更新失败，请重试')
  }
}

/** 可见性切换：即时生效，失败回滚并提示。 */
async function handleVisibility(kb: KnowledgeBase, value: unknown): Promise<void> {
  const next: 0 | 1 = value === 1 ? 1 : 0
  const previous: 0 | 1 = kb.visibility
  try {
    await changeKnowledgeBaseVisibility(kb.id, next)
    kb.visibility = next
    ElMessage.success(next === 1 ? '已设为公开' : '已设为私有')
  } catch {
    kb.visibility = previous
    ElMessage.error('切换失败，请重试')
  }
}

/** 删除知识库（二次确认：库内知识一并移入回收站）。 */
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
    delete trees.value[kb.id]
    if (expandedId.value === kb.id) expandedId.value = ''
    ElMessage.success('已删除')
    await load()
  } catch {
    ElMessage.error('删除失败，请重试')
  }
}

/** 打开新建目录弹窗（某卡片）：父目录可选，默认根目录。 */
function openNewDir(kb: KnowledgeBase): void {
  dirKbId.value = kb.id
  dirForm.name = ''
  dirForm.parentId = ''
  dirVisible.value = true
}

async function submitNewDir(): Promise<void> {
  const name = dirForm.name.trim()
  if (!name) {
    ElMessage.warning('目录名不能为空')
    return
  }
  const kbId = dirKbId.value
  try {
    await createDirectory(kbId, {
      name,
      ...(dirForm.parentId ? { parentId: dirForm.parentId } : {}),
    })
    trees.value[kbId] = await fetchDirectoryTree(kbId)
    dirVisible.value = false
    ElMessage.success('目录已创建')
  } catch {
    ElMessage.error('创建失败，请重试')
  }
}

/** 打开目录改名弹窗。 */
function openRename(kbId: string, dir: DirectoryNode): void {
  renameKbId.value = kbId
  renameDirId.value = dir.id
  renameName.value = dir.name
  renameVisible.value = true
}

async function submitRename(): Promise<void> {
  const name = renameName.value.trim()
  if (!name) {
    ElMessage.warning('目录名不能为空')
    return
  }
  try {
    await updateDirectory(renameKbId.value, renameDirId.value, { name })
    trees.value[renameKbId.value] = await fetchDirectoryTree(renameKbId.value)
    renameVisible.value = false
    ElMessage.success('目录已改名')
  } catch {
    ElMessage.error('改名失败，请重试')
  }
}

/** 删除目录（二次确认：目录下知识将上挂父目录）。 */
async function handleDeleteDir(kbId: string, dir: DirectoryNode): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除目录「${dir.name}」吗？目录下的知识将上挂父目录。`,
      '删除目录',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteDirectory(kbId, dir.id)
    trees.value[kbId] = await fetchDirectoryTree(kbId)
    ElMessage.success('目录已删除')
  } catch {
    ElMessage.error('删除失败，请重试')
  }
}

onMounted(() => {
  if (!session.loggedIn) {
    void router.replace({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  void load()
})
</script>

<template>
  <main class="kb-manage">
    <header class="kb-manage__header">
      <div>
        <h1 class="kb-manage__title">我的知识库</h1>
        <p class="kb-manage__desc">知识按「库 → 目录 → 知识」组织；删除的库与知识进入回收站。</p>
      </div>
      <el-button type="primary" @click="createVisible = true">
        <el-icon><Plus /></el-icon>
        新建知识库
      </el-button>
    </header>

    <div v-if="loading" class="kb-manage__state">
      <div v-for="i in 4" :key="i" class="kb-manage__skeleton" aria-hidden="true" />
    </div>
    <div v-else-if="loadError" class="kb-manage__state">
      <p class="kb-manage__state-text">知识库加载失败</p>
      <el-button type="primary" plain @click="load">重试</el-button>
    </div>
    <div v-else-if="kbs.length === 0" class="kb-manage__state">
      <el-icon class="kb-manage__state-icon"><Collection /></el-icon>
      <p class="kb-manage__state-text">还没有知识库，点击右上角「新建知识库」开始。</p>
    </div>
    <div v-else class="kb-manage__grid">
      <article v-for="kb in kbs" :key="kb.id" class="kb-manage__card">
        <div class="kb-manage__cover" :class="{ 'kb-manage__cover--private': kb.visibility === 0 }">
          <span class="kb-manage__cover-text">{{ kb.name.slice(0, 1) }}</span>
        </div>
        <div class="kb-manage__card-body">
          <div class="kb-manage__name-row">
            <h2 class="kb-manage__name">{{ kb.name }}</h2>
            <el-tag :type="kb.visibility === 1 ? 'success' : 'info'" effect="plain" size="small">
              {{ kb.visibility === 1 ? '公开' : '私有' }}
            </el-tag>
          </div>
          <p class="kb-manage__intro">{{ kb.intro || '暂无简介' }}</p>
          <div class="kb-manage__meta">
            <span>知识 {{ kb.knowledgeCount }}</span>
          </div>
          <div class="kb-manage__actions">
            <el-button size="small" plain @click="openEdit(kb)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-switch
              v-model="kb.visibility"
              :active-value="1"
              :inactive-value="0"
              size="small"
              inline-prompt
              :active-text="kb.visibility === 1 ? '公开' : ''"
              :inactive-text="kb.visibility === 0 ? '私有' : ''"
              aria-label="切换可见性"
              @change="(value: unknown) => handleVisibility(kb, value)"
            />
            <el-button size="small" type="danger" plain @click="handleDelete(kb)">删除</el-button>
          </div>
          <button
            type="button"
            class="kb-manage__toggle"
            :class="{ 'kb-manage__toggle--open': expandedId === kb.id }"
            @click="toggleExpand(kb.id)"
          >
            <el-icon><Folder /></el-icon>
            目录管理
            <span class="kb-manage__toggle-arrow">▾</span>
          </button>
          <div v-if="expandedId === kb.id" class="kb-manage__dirs">
            <div class="kb-manage__dirs-toolbar">
              <span class="kb-manage__dirs-label">目录树</span>
              <el-button size="small" plain @click="openNewDir(kb)">
                <el-icon><Plus /></el-icon>
                新建目录
              </el-button>
            </div>
            <div v-if="treeLoading" class="kb-manage__dirs-loading">目录加载中…</div>
            <div v-else-if="(trees[kb.id] ?? []).length === 0" class="kb-manage__dirs-empty">
              还没有目录，新建一个吧。
            </div>
            <el-tree
              v-else
              :data="trees[kb.id] ?? []"
              node-key="id"
              :props="{ label: 'name', children: 'children' }"
              default-expand-all
              class="kb-manage__tree"
            >
              <template #default="{ data }">
                <span class="kb-manage__dir">
                  <el-icon class="kb-manage__dir-icon"><Folder /></el-icon>
                  <span class="kb-manage__dir-name">{{ data.name }}</span>
                  <span class="kb-manage__dir-count">{{ data.knowledgeCount }}</span>
                  <span class="kb-manage__dir-ops">
                    <button
                      type="button"
                      class="kb-manage__dir-op"
                      @click.stop="openRename(kb.id, data)"
                    >
                      改名
                    </button>
                    <button
                      type="button"
                      class="kb-manage__dir-op kb-manage__dir-op--danger"
                      @click.stop="handleDeleteDir(kb.id, data)"
                    >
                      删除
                    </button>
                  </span>
                </span>
              </template>
            </el-tree>
          </div>
        </div>
      </article>
    </div>

    <el-dialog v-model="createVisible" title="新建知识库" width="440px">
      <el-form label-position="top">
        <el-form-item label="库名">
          <el-input v-model="createForm.name" maxlength="40" placeholder="知识库名称" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="createForm.intro" type="textarea" :rows="3" maxlength="200" />
        </el-form-item>
        <el-form-item label="可见性">
          <el-radio-group v-model="createForm.visibility">
            <el-radio :value="0">私有</el-radio>
            <el-radio :value="1">公开</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

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

    <el-dialog v-model="dirVisible" title="新建目录" width="420px">
      <el-form label-position="top">
        <el-form-item label="目录名">
          <el-input v-model="dirForm.name" maxlength="30" placeholder="目录名称" />
        </el-form-item>
        <el-form-item label="父目录">
          <el-select v-model="dirForm.parentId" style="width: 100%">
            <el-option label="根目录" value="" />
            <el-option
              v-for="option in dirOptions"
              :key="option.id"
              :value="option.id"
              :label="option.label"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dirVisible = false">取消</el-button>
        <el-button type="primary" @click="submitNewDir">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="renameVisible" title="重命名目录" width="400px">
      <el-form label-position="top">
        <el-form-item label="目录名">
          <el-input v-model="renameName" maxlength="30" placeholder="目录名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renameVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRename">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.kb-manage {
  max-width: 1080px;
  margin: 0 auto;
  padding: var(--xl-space-6) var(--xl-space-4) var(--xl-space-8);
}

.kb-manage__header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--xl-space-4);
  margin-bottom: var(--xl-space-6);
}

.kb-manage__title {
  margin: 0 0 var(--xl-space-1);
  color: var(--xl-text-primary);
  font-size: 24px;
}

.kb-manage__desc {
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: 13px;
}

.kb-manage__state {
  padding: var(--xl-space-8) 0;
  text-align: center;
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.kb-manage__state p {
  margin: 0;
}

.kb-manage__state-icon {
  display: block;
  margin: 0 auto var(--xl-space-3);
  font-size: 40px;
  color: var(--xl-text-muted);
}

.kb-manage__skeleton {
  height: 220px;
  border-radius: var(--xl-radius-card);
  background: color-mix(in srgb, var(--xl-border) 60%, transparent);
}

.kb-manage__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--xl-space-4);
  align-items: start;
}

.kb-manage__card {
  overflow: hidden;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
  transition: box-shadow var(--xl-transition);
}

.kb-manage__card:hover {
  box-shadow: var(--xl-shadow-md);
}

.kb-manage__cover {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 72px;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--xl-color-primary) 22%, transparent),
    transparent
  );
}

.kb-manage__cover--private {
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--xl-text-muted) 30%, transparent),
    transparent
  );
}

.kb-manage__cover-text {
  color: color-mix(in srgb, var(--xl-color-primary) 70%, var(--xl-text-primary));
  font-size: 32px;
  font-weight: 700;
}

.kb-manage__card-body {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-2);
  padding: var(--xl-space-4);
}

.kb-manage__name-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--xl-space-2);
}

.kb-manage__name {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: var(--xl-text-primary);
  font-size: 16px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-manage__intro {
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: 13px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.kb-manage__meta {
  color: var(--xl-text-muted);
  font-size: 12px;
}

.kb-manage__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--xl-space-2);
  padding-top: var(--xl-space-1);
  border-top: 1px solid var(--xl-border);
}

.kb-manage__toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-sm);
  background: var(--xl-bg-secondary);
  color: var(--xl-text-secondary);
  font-size: 13px;
  cursor: pointer;
}

.kb-manage__toggle:hover {
  color: var(--xl-color-primary);
}

.kb-manage__toggle--open {
  color: var(--xl-color-primary);
}

.kb-manage__toggle-arrow {
  margin-left: auto;
  transition: transform var(--xl-transition);
}

.kb-manage__toggle--open .kb-manage__toggle-arrow {
  transform: rotate(180deg);
}

.kb-manage__dirs {
  padding: var(--xl-space-3);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-sm);
  background: var(--xl-bg-page);
}

.kb-manage__dirs-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--xl-space-2);
}

.kb-manage__dirs-label {
  color: var(--xl-text-muted);
  font-size: 12px;
}

.kb-manage__dirs-loading,
.kb-manage__dirs-empty {
  padding: var(--xl-space-3) 0;
  color: var(--xl-text-muted);
  font-size: 12px;
  text-align: center;
}

.kb-manage__tree {
  background: transparent;
}

.kb-manage__tree :deep(.el-tree-node__content) {
  height: 30px;
  border-radius: var(--xl-radius-sm);
}

.kb-manage__dir {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding-right: 4px;
  color: var(--xl-text-secondary);
  font-size: 13px;
}

.kb-manage__dir-icon {
  flex-shrink: 0;
  color: var(--xl-text-muted);
}

.kb-manage__dir-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-manage__dir-count {
  flex-shrink: 0;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--xl-bg-secondary);
  color: var(--xl-text-muted);
  font-size: 11px;
}

.kb-manage__dir-ops {
  display: none;
  flex-shrink: 0;
  gap: 2px;
}

.kb-manage__tree :deep(.el-tree-node__content:hover) .kb-manage__dir-ops {
  display: inline-flex;
}

.kb-manage__dir-op {
  padding: 1px 6px;
  border: none;
  border-radius: 4px;
  background: none;
  color: var(--xl-text-secondary);
  font-size: 11px;
  cursor: pointer;
}

.kb-manage__dir-op:hover {
  background: color-mix(in srgb, var(--xl-color-primary) 10%, transparent);
  color: var(--xl-color-primary);
}

.kb-manage__dir-op--danger:hover {
  background: color-mix(in srgb, var(--xl-color-danger) 10%, transparent);
  color: var(--xl-color-danger);
}
</style>
