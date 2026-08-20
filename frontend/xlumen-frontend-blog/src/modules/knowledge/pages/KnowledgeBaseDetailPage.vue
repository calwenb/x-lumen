<script setup lang="ts">
// 知识库详情页（B20，F-0308/F-0309/F-0201，决策 D16）：库头部 + 多级目录树 + 知识列表。
// 数据流：route.params.id → kbId；登录态经 fetchKnowledgeBases 匹配自己的库（isOwner），
// 访客/非本人显示「公开知识库」占位头部，仅展示公开知识列表（卡片带 kbName badge）。
// 排序由后端处理（未选目录 updated_at DESC、选中目录 created_at ASC），前端只传参。
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Collection, Edit, Folder, Lock, Plus, Unlock } from '@element-plus/icons-vue'

import { useSessionStore } from '@/stores/session'
import {
  createDirectory,
  fetchDirectoryTree,
  fetchKnowledgeBases,
  updateKnowledgeBase,
} from '@/modules/knowledge/api/knowledgeBase'
import { fetchKnowledges } from '@/modules/publishing/api/public'
import DirectoryTreeContextMenu from '@/modules/knowledge/components/DirectoryTreeContextMenu.vue'
import { useInfinitePage } from '@/composables/useInfinitePage'

import type { DirectoryNode, KnowledgeBase } from '@/modules/knowledge/api/knowledgeBase'
import type { KnowledgeCard } from '@/modules/publishing/api/public'

const PAGE_SIZE = 10

const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const kbId = computed(() => String(route.params.id))

const kbDetail = ref<KnowledgeBase | null>(null)
const isOwner = ref(false)
const directories = ref<DirectoryNode[]>([])

const selectedDirectoryId = ref('')
const sentinel = ref<HTMLElement | null>(null)

const editVisible = ref(false)
const editForm = ref({ name: '', intro: '', cover: '' })

const dirVisible = ref(false)
const dirName = ref('')
const dirParentId = ref('')

/** F-0312 右键菜单实例（open(event, node?) 由目录树 contextmenu 调用，node 省略 = 树根「全部知识」）。 */
const dirMenu = ref<InstanceType<typeof DirectoryTreeContextMenu> | null>(null)

/** 目录总数（扁平化树节点）。 */
const directoryCount = computed(() => countDirectories(directories.value))
/** 目录选择器选项（缩进表示层级，'' = 根目录）。 */
const dirOptions = computed(() => flattenDirectories(directories.value))
/** 可见性徽标文案。 */
const visibilityText = computed(() => (kbDetail.value?.visibility === 1 ? '公开' : '私有'))

function countDirectories(nodes: DirectoryNode[]): number {
  return nodes.reduce((sum, node) => sum + 1 + countDirectories(node.children ?? []), 0)
}

function flattenDirectories(nodes: DirectoryNode[], depth = 0): { id: string; label: string }[] {
  const out: { id: string; label: string }[] = []
  for (const node of nodes) {
    out.push({ id: node.id, label: `${'　'.repeat(depth)}${node.name}` })
    out.push(...flattenDirectories(node.children ?? [], depth + 1))
  }
  return out
}

function formatDate(iso: string): string {
  return iso.slice(0, 10)
}

/** 知识列表加载：全部视图不传 directoryId，选中目录只传参（排序由后端处理）。 */
const infinite = useInfinitePage<KnowledgeCard>({
  sentinel,
  pageSize: PAGE_SIZE,
  loadPage: (pageNo, pageSize) => fetchKnowledges({
      kbId: kbId.value,
      ...(selectedDirectoryId.value ? { directoryId: selectedDirectoryId.value } : {}),
      pageNo,
      pageSize,
    }),
})

const knowledges = infinite.items
const loading = infinite.loading
const loadError = infinite.error

/** 登录态下匹配自己的库：命中则加载库详情 + 目录树（库主模式），否则保持访客占位。 */
async function loadOwnerInfo(): Promise<void> {
  if (!session.loggedIn) return
  try {
    const list = await fetchKnowledgeBases()
    const mine = list.find((kb) => kb.id === kbId.value)
    if (!mine) return
    kbDetail.value = mine
    isOwner.value = true
    directories.value = await fetchDirectoryTree(kbId.value)
  } catch {
    // 非本人库或接口异常：维持访客占位视图，知识列表仍可公开读取
  }
}

/** 目录/全部视图切换：重置到第一页重新查询。 */
function selectDirectory(id: string): void {
  selectedDirectoryId.value = id
  void infinite.loadFirst()
}

/** 编辑库资料（库主）：name 必填，intro/cover 可空。 */
async function submitEdit(): Promise<void> {
  const name = editForm.value.name.trim()
  if (!name) {
    ElMessage.warning('库名不能为空')
    return
  }
  try {
    const updated = await updateKnowledgeBase(kbId.value, {
      name,
      intro: editForm.value.intro.trim(),
      ...(editForm.value.cover.trim() ? { cover: editForm.value.cover.trim() } : {}),
    })
    kbDetail.value = updated
    editVisible.value = false
    ElMessage.success('库资料已更新')
  } catch {
    ElMessage.error('更新失败，请重试')
  }
}

function openEdit(): void {
  if (!kbDetail.value) return
  editForm.value = {
    name: kbDetail.value.name,
    intro: kbDetail.value.intro,
    cover: kbDetail.value.cover,
  }
  editVisible.value = true
}

/** 新建目录（库主）：名称必填，父目录可选（默认根目录 0）。 */
async function submitDirectory(): Promise<void> {
  const name = dirName.value.trim()
  if (!name) {
    ElMessage.warning('目录名不能为空')
    return
  }
  try {
    await createDirectory(kbId.value, {
      name,
      ...(dirParentId.value ? { parentId: dirParentId.value } : {}),
    })
    directories.value = await fetchDirectoryTree(kbId.value)
    dirVisible.value = false
    dirName.value = ''
    dirParentId.value = ''
    ElMessage.success('目录已创建')
  } catch {
    ElMessage.error('创建失败，请重试')
  }
}

/** F-0312 右键菜单操作成功后刷新目录树（目录总数 computed 自动更新；失败保留原树）。 */
async function refreshDirectories(): Promise<void> {
  directories.value = await fetchDirectoryTree(kbId.value).catch(() => directories.value)
}

/** F-0312 删除目录后：选中目录在删除范围内则重置为「全部知识」，并重新拉取列表（知识上挂父目录）。 */
function onDirectoryDeleted(ids: string[]): void {
  if (selectedDirectoryId.value && ids.includes(selectedDirectoryId.value)) {
    selectedDirectoryId.value = ''
  }
  void infinite.loadFirst()
}

onMounted(() => {
  void loadOwnerInfo()
  void infinite.loadFirst()
})
</script>

<template>
  <main class="kb-detail">
    <header class="kb-detail__header">
      <div class="kb-detail__bar">
        <button type="button" class="kb-detail__back" aria-label="返回" @click="router.back()">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </button>
        <div v-if="kbDetail" class="kb-detail__title-row">
          <h1 class="kb-detail__title">{{ kbDetail.name }}</h1>
          <el-tag
            :type="kbDetail.visibility === 1 ? 'success' : 'info'"
            effect="plain"
            size="small"
          >
            <el-icon class="kb-detail__tag-icon">
              <Lock v-if="kbDetail.visibility === 0" />
              <Unlock v-else />
            </el-icon>
            {{ visibilityText }}
          </el-tag>
          <div class="kb-detail__stats">
            知识 {{ kbDetail.knowledgeCount }} · 目录 {{ directoryCount }}
          </div>
        </div>
        <div v-else class="kb-detail__title-row">
          <h1 class="kb-detail__title">公开知识库</h1>
          <el-tag type="success" effect="plain" size="small">
            <el-icon class="kb-detail__tag-icon"><Unlock /></el-icon>
            公开
          </el-tag>
        </div>
        <div v-if="isOwner && kbDetail" class="kb-detail__owner-actions">
          <el-button size="small" plain @click="openEdit">
            <el-icon><Edit /></el-icon>
            编辑库资料
          </el-button>
          <el-button size="small" type="primary" @click="dirVisible = true">
            <el-icon><Plus /></el-icon>
            新建目录
          </el-button>
        </div>
      </div>
      <p v-if="kbDetail && kbDetail.intro" class="kb-detail__intro">{{ kbDetail.intro }}</p>
    </header>

    <div class="kb-detail__layout">
      <aside v-if="isOwner" class="kb-detail__side">
        <button
          type="button"
          class="kb-detail__all"
          :class="{ 'kb-detail__all--active': selectedDirectoryId === '' }"
          @click="selectDirectory('')"
          @contextmenu="dirMenu?.open($event)"
        >
          <el-icon><Collection /></el-icon>
          全部知识
        </button>
        <el-tree
          :data="directories"
          node-key="id"
          :props="{ label: 'name', children: 'children' }"
          :expand-on-click-node="false"
          default-expand-all
          class="kb-detail__tree"
          @node-click="(data: DirectoryNode) => selectDirectory(data.id)"
          @node-contextmenu="
            (event: MouseEvent, data: DirectoryNode) =>
              dirMenu?.open(event, { id: data.id, name: data.name })
          "
        >
          <template #default="{ data }">
            <span
              class="kb-detail__dir"
              :class="{ 'kb-detail__dir--active': data.id === selectedDirectoryId }"
            >
              <el-icon class="kb-detail__dir-icon"><Folder /></el-icon>
              <span class="kb-detail__dir-name">{{ data.name }}</span>
              <span class="kb-detail__dir-count">{{ data.knowledgeCount }}</span>
            </span>
          </template>
        </el-tree>
      </aside>

      <section class="kb-detail__main">
        <div v-if="loading" class="kb-detail__state">
          <div v-for="i in 3" :key="i" class="kb-detail__skeleton" aria-hidden="true" />
        </div>
        <div v-else-if="loadError" class="kb-detail__state">
          <p class="kb-detail__state-text">知识加载失败</p>
          <el-button type="primary" plain @click="infinite.retry()">重试</el-button>
        </div>
        <template v-else>
          <div v-if="knowledges.length === 0" class="kb-detail__state">
            <el-icon class="kb-detail__state-icon"><Collection /></el-icon>
            <p class="kb-detail__state-text">这个视图下还没有知识。</p>
          </div>
          <article v-for="knowledge in knowledges" v-else :key="knowledge.id" class="kb-card">
            <RouterLink class="kb-card__title" :to="`/knowledge/${knowledge.id}`">
              {{ knowledge.title }}
            </RouterLink>
            <p class="kb-card__summary">{{ knowledge.summary }}</p>
            <div class="kb-card__meta">
              <span class="kb-card__badge">{{ knowledge.kbName }}</span>
              <span>{{ knowledge.authorName }}</span>
              <span>{{ formatDate(knowledge.publishedAt) }}</span>
              <span>{{ knowledge.readMinutes }} 分钟阅读</span>
              <span>{{ knowledge.viewCount }} 阅读</span>
              <span>{{ knowledge.commentCount }} 评论</span>
              <span>{{ knowledge.likeCount }} 点赞</span>
            </div>
            <div v-if="knowledge.tags.length > 0" class="kb-card__tags">
              <RouterLink
                v-for="tag in knowledge.tags"
                :key="tag"
                class="kb-card__tag"
                :to="`/search?tag=${encodeURIComponent(tag)}`"
              >
                # {{ tag }}
              </RouterLink>
            </div>
          </article>
          <div ref="sentinel" class="kb-detail__sentinel" aria-hidden="true" />
          <div v-if="infinite.loadingMore" class="kb-detail__load-more" role="status">加载更多…</div>
          <div v-else-if="infinite.loadMoreError" class="kb-detail__load-more">
            <el-button type="primary" plain size="small" @click="infinite.retryMore()">重试加载</el-button>
          </div>
          <div v-else-if="!infinite.hasMore" class="kb-detail__load-more">已加载全部知识</div>
        </template>
      </section>
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

    <el-dialog v-model="dirVisible" title="新建目录" width="420px">
      <el-form label-position="top">
        <el-form-item label="目录名">
          <el-input v-model="dirName" maxlength="30" placeholder="目录名称" />
        </el-form-item>
        <el-form-item label="父目录">
          <el-select v-model="dirParentId" style="width: 100%">
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
        <el-button type="primary" @click="submitDirectory">创建</el-button>
      </template>
    </el-dialog>

    <!-- F-0312 目录树右键菜单（新增子目录/重命名/删除，仅库主；open 非库主时忽略） -->
    <DirectoryTreeContextMenu
      ref="dirMenu"
      :kb-id="kbId"
      :owner="isOwner"
      :directories="directories"
      @refresh="refreshDirectories"
      @deleted="onDirectoryDeleted"
    />
  </main>
</template>

<style scoped>
.kb-detail {
  max-width: 1080px;
  margin: 0 auto;
  padding: var(--xl-space-6) var(--xl-space-4) var(--xl-space-8);
}

.kb-detail__header {
  padding: var(--xl-space-4) var(--xl-space-6);
  margin-bottom: var(--xl-space-6);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
}

.kb-detail__bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--xl-space-3);
}

.kb-detail__back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: none;
  border-radius: var(--xl-radius-sm);
  background: none;
  color: var(--xl-text-secondary);
  font-size: 13px;
  cursor: pointer;
}

.kb-detail__back:hover {
  background: var(--xl-bg-secondary);
  color: var(--xl-color-primary);
}

.kb-detail__title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--xl-space-3);
}

.kb-detail__title {
  margin: 0;
  color: var(--xl-text-primary);
  font-size: 24px;
}

.kb-detail__tag-icon {
  margin-right: 4px;
  vertical-align: -2px;
}

.kb-detail__stats {
  color: var(--xl-text-muted);
  font-size: 13px;
}

.kb-detail__owner-actions {
  margin-left: auto;
  display: flex;
  gap: var(--xl-space-2);
}

.kb-detail__intro {
  margin: var(--xl-space-3) 0 0;
  color: var(--xl-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.kb-detail__layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: var(--xl-space-6);
  align-items: start;
}

.kb-detail__side {
  position: sticky;
  top: 72px;
  padding: var(--xl-space-4);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
}

.kb-detail__all {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 7px 10px;
  border: none;
  border-radius: var(--xl-radius-sm);
  background: none;
  color: var(--xl-text-secondary);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.kb-detail__all:hover {
  background: var(--xl-bg-secondary);
  color: var(--xl-color-primary);
}

.kb-detail__all--active {
  background: color-mix(in srgb, var(--xl-color-primary) 10%, transparent);
  color: var(--xl-color-primary);
  font-weight: 600;
}

.kb-detail__tree {
  margin-top: var(--xl-space-2);
  background: transparent;
}

.kb-detail__tree :deep(.el-tree-node__content) {
  height: 32px;
  border-radius: var(--xl-radius-sm);
}

.kb-detail__dir {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding-right: 4px;
  color: var(--xl-text-secondary);
  font-size: 13px;
}

.kb-detail__dir--active {
  color: var(--xl-color-primary);
  font-weight: 600;
}

.kb-detail__dir-icon {
  flex-shrink: 0;
  color: var(--xl-text-muted);
}

.kb-detail__dir-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-detail__dir-count {
  flex-shrink: 0;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--xl-bg-secondary);
  color: var(--xl-text-muted);
  font-size: 11px;
}

.kb-detail__main {
  min-width: 0;
}

.kb-detail__state {
  padding: var(--xl-space-8) 0;
  text-align: center;
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.kb-detail__state p {
  margin: 0;
}

.kb-detail__state-icon {
  display: block;
  margin: 0 auto var(--xl-space-3);
  font-size: 40px;
  color: var(--xl-text-muted);
}

.kb-detail__sentinel {
  height: 1px;
}

.kb-detail__load-more {
  min-height: 34px;
  padding: 14px 0 4px;
  color: var(--xl-text-secondary);
  font-size: 13px;
  text-align: center;
}

.kb-detail__skeleton {
  height: 110px;
  margin-bottom: var(--xl-space-4);
  border-radius: var(--xl-radius-card);
  background: color-mix(in srgb, var(--xl-border) 60%, transparent);
}

.kb-card {
  padding: var(--xl-space-4) var(--xl-space-6);
  margin-bottom: var(--xl-space-4);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
  transition:
    box-shadow var(--xl-transition),
    transform var(--xl-transition);
}

.kb-card:hover {
  box-shadow: var(--xl-shadow-md);
  transform: translateY(-2px);
}

.kb-card__title {
  color: var(--xl-text-primary);
  font-size: 17px;
  font-weight: 600;
  text-decoration: none;
}

.kb-card__title:hover {
  color: var(--xl-color-primary);
}

.kb-card__summary {
  margin: var(--xl-space-2) 0;
  color: var(--xl-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.kb-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--xl-space-3);
  color: var(--xl-text-muted);
  font-size: 12px;
}

.kb-card__badge {
  padding: 2px 10px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--xl-color-primary) 8%, transparent);
  color: var(--xl-color-primary);
}

.kb-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--xl-space-2);
  margin-top: var(--xl-space-3);
}

.kb-card__tag {
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--xl-bg-secondary);
  color: var(--xl-text-secondary);
  font-size: 12px;
  text-decoration: none;
}

.kb-card__tag:hover {
  background: color-mix(in srgb, var(--xl-color-primary) 10%, transparent);
  color: var(--xl-color-primary);
}

@media (width <= 800px) {
  .kb-detail__layout {
    grid-template-columns: 1fr;
  }

  .kb-detail__side {
    position: static;
  }

  .kb-detail__owner-actions {
    margin-left: 0;
  }
}
</style>
