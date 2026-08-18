<script setup lang="ts">
// 目录树右键菜单（F-0312，PROTOTYPE B01/B20，仅库主）：节点右键 新增子目录/重命名/删除，
// 树根（「全部知识」占位）右键仅 新增根目录。新增/重命名走 el-dialog（名称必填、maxlength 30，
// 与 B20 既有「新建目录」弹窗一致）；删除二次确认，文案按 DirectoryController 注释写准连带规则：
// 目录及全部子目录删除，目录下知识上挂父目录（根级目录删除后挂库根）。
// 成功后 emit('refresh') 交页面刷新目录树；emit('deleted', ids) 携带被删子树 ID，
// 供页面在当前选中目录被删时重置为「全部知识」并重新拉取列表。
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  createDirectory,
  deleteDirectory,
  updateDirectory,
} from '@/modules/knowledge/api/knowledgeBase'

import type { DirectoryNode } from '@/modules/knowledge/api/knowledgeBase'

/** 右键目标目录（null = 树根占位「全部知识」）。 */
interface MenuTarget {
  id: string
  name: string
}

const props = defineProps<{
  kbId: string
  owner: boolean
  directories: DirectoryNode[]
}>()

const emit = defineEmits<{
  refresh: []
  deleted: [ids: string[]]
}>()

const menu = reactive({ visible: false, x: 0, y: 0 })
const target = ref<MenuTarget | null>(null)
const menuRef = ref<HTMLElement | null>(null)

const dialog = reactive({
  visible: false,
  mode: 'create' as 'create' | 'rename',
  name: '',
  submitting: false,
})

const isRoot = computed(() => target.value === null)
const dialogTitle = computed(() => {
  if (dialog.mode === 'rename') return '重命名目录'
  return isRoot.value ? '新增根目录' : '新增子目录'
})

function closeMenu(): void {
  menu.visible = false
}

function onGlobalMouseDown(event: MouseEvent): void {
  if (!menu.visible) return
  if (menuRef.value && event.target instanceof Node && menuRef.value.contains(event.target)) return
  closeMenu()
}

function onGlobalKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') closeMenu()
}

onMounted(() => {
  window.addEventListener('mousedown', onGlobalMouseDown, true)
  window.addEventListener('keydown', onGlobalKeydown)
  window.addEventListener('resize', closeMenu)
  window.addEventListener('scroll', closeMenu, true)
})

onBeforeUnmount(() => {
  window.removeEventListener('mousedown', onGlobalMouseDown, true)
  window.removeEventListener('keydown', onGlobalKeydown)
  window.removeEventListener('resize', closeMenu)
  window.removeEventListener('scroll', closeMenu, true)
})

/** 打开右键菜单（页面 contextmenu 事件入口；非库主直接忽略并放行浏览器默认菜单）。 */
function open(event: MouseEvent, node?: MenuTarget): void {
  if (!props.owner || !props.kbId) return
  event.preventDefault()
  target.value = node ?? null
  menu.visible = true
  menu.x = Math.min(event.clientX, window.innerWidth - 170)
  menu.y = Math.min(event.clientY, window.innerHeight - 140)
}

defineExpose({ open })

function openCreateDialog(): void {
  closeMenu()
  dialog.mode = 'create'
  dialog.name = ''
  dialog.visible = true
}

function openRenameDialog(): void {
  if (!target.value) return
  closeMenu()
  dialog.mode = 'rename'
  dialog.name = target.value.name
  dialog.visible = true
}

/** 提交新增/重命名：名称必填（trim 后非空），成功后刷新目录树。 */
async function submitDialog(): Promise<void> {
  const name = dialog.name.trim()
  if (!name) {
    ElMessage.warning('目录名不能为空')
    return
  }
  dialog.submitting = true
  try {
    if (dialog.mode === 'create') {
      await createDirectory(props.kbId, {
        ...(target.value ? { parentId: target.value.id } : {}),
        name,
      })
      ElMessage.success('目录已创建')
    } else if (target.value) {
      await updateDirectory(props.kbId, target.value.id, { name })
      ElMessage.success('目录已重命名')
    }
    dialog.visible = false
    emit('refresh')
  } catch {
    ElMessage.error('操作失败，请重试')
  } finally {
    dialog.submitting = false
  }
}

/** 收集节点及其全部子孙目录 ID（供页面判断选中目录是否在删除范围内）。 */
function collectSubtreeIds(nodes: DirectoryNode[], id: string): string[] {
  for (const node of nodes) {
    if (node.id === id) return [node.id, ...collectChildrenIds(node.children ?? [])]
    const hit = collectSubtreeIds(node.children ?? [], id)
    if (hit.length > 0) return hit
  }
  return []
}

function collectChildrenIds(nodes: DirectoryNode[]): string[] {
  return nodes.flatMap((node) => [node.id, ...collectChildrenIds(node.children ?? [])])
}

/** 删除目录：二次确认（连带规则提示），成功后通知页面刷新并重置选中。 */
async function confirmDelete(): Promise<void> {
  const node = target.value
  if (!node) return
  closeMenu()
  try {
    await ElMessageBox.confirm(
      `确定删除「${node.name}」吗？该目录及其全部子目录将被删除，目录下的知识将移动到父目录（根级目录删除后移动到库根）。`,
      '删除目录',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteDirectory(props.kbId, node.id)
    ElMessage.success('目录已删除')
    emit('deleted', collectSubtreeIds(props.directories, node.id))
    emit('refresh')
  } catch {
    ElMessage.error('删除失败，请重试')
  }
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="menu.visible"
      ref="menuRef"
      class="dir-menu"
      :style="{ left: `${menu.x}px`, top: `${menu.y}px` }"
      @contextmenu.prevent
    >
      <button v-if="isRoot" type="button" class="dir-menu__item" @click="openCreateDialog">
        新增根目录
      </button>
      <template v-else>
        <button type="button" class="dir-menu__item" @click="openCreateDialog">新增子目录</button>
        <button type="button" class="dir-menu__item" @click="openRenameDialog">重命名</button>
        <button type="button" class="dir-menu__item dir-menu__item--danger" @click="confirmDelete">
          删除
        </button>
      </template>
    </div>
  </Teleport>

  <el-dialog v-model="dialog.visible" :title="dialogTitle" width="420px">
    <el-form label-position="top" @submit.prevent>
      <el-form-item label="目录名">
        <el-input
          v-model="dialog.name"
          maxlength="30"
          placeholder="目录名称"
          @keydown.enter="submitDialog"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="dialog.submitting" @click="submitDialog">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.dir-menu {
  position: fixed;
  z-index: 3000;
  min-width: 130px;
  padding: 4px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-sm);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-md);
}

.dir-menu__item {
  display: block;
  width: 100%;
  padding: 6px 10px;
  border: none;
  border-radius: var(--xl-radius-sm);
  background: none;
  color: var(--xl-text-secondary);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.dir-menu__item:hover {
  background: var(--xl-bg-secondary);
  color: var(--xl-color-primary);
}

.dir-menu__item--danger:hover {
  background: color-mix(in srgb, var(--xl-color-danger, #d03050) 8%, transparent);
  color: var(--xl-color-danger, #d03050);
}
</style>
