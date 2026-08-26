<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createChangelog,
  deleteChangelog,
  fetchChangelogs,
  updateChangelog,
  type ChangelogItem,
  type ChangelogPayload,
} from '../api/changelog'

const records = ref<ChangelogItem[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = 10
const loading = ref(false)

const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<string | null>(null)
const form = ref<ChangelogPayload>({ title: '', content: '', published: true })

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await fetchChangelogs(pageNo.value, pageSize)
    records.value = page.records
    total.value = page.total
  } catch (e) {
    ElMessage.error((e as Error).message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editingId.value = null
  form.value = { title: '', content: '', published: true }
  dialogVisible.value = true
}

function openEdit(item: ChangelogItem): void {
  editingId.value = item.id
  form.value = {
    title: item.title,
    content: item.content,
    published: item.published,
  }
  dialogVisible.value = true
}

async function save(): Promise<void> {
  if (!form.value.title.trim() || !form.value.content.trim()) {
    ElMessage.warning('标题与内容不能为空')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateChangelog(editingId.value, form.value)
    } else {
      await createChangelog(form.value)
    }
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(item: ChangelogItem): Promise<void> {
  await ElMessageBox.confirm(`确认删除「${item.title}」？`, '删除确认', { type: 'warning' })
  await deleteChangelog(item.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="changelog-admin">
    <el-card shadow="never">
      <template #header>
        <div class="changelog-admin__head">
          <span>站点更新日志</span>
          <el-button type="primary" @click="openCreate">新增</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="records">
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.published ? 'success' : 'info'" size="small">
              {{ row.published ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" width="170">
          <template #default="{ row }">
            {{ row.publishedAt ? String(row.publishedAt).slice(0, 16) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pageNo"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        class="changelog-admin__pager"
        @current-change="load"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑动态' : '新增动态'" width="640px">
      <el-form label-width="70px">
        <el-form-item label="标题">
          <el-input v-model="form.title" maxlength="200" placeholder="例如：V2 语义检索上线" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="10"
            placeholder="支持 Markdown。发布后将在前台「动态」页展示。"
          />
        </el-form-item>
        <el-form-item label="发布">
          <el-switch v-model="form.published" active-text="立即发布" inactive-text="存为草稿" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.changelog-admin__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.changelog-admin__pager {
  margin-top: var(--xl-space-4);
  justify-content: flex-end;
}
</style>