// 草稿自动保存组合式函数（F-0302）：10s 节流 + 失焦触发 + 内容未变化不发请求。
// 与服务端幂等去重（KnowledgeServiceImpl.autosave）配合，避免无效版本增长。
import { onBeforeUnmount, ref } from 'vue'

/** 保存回调：返回保存后的知识 ID 与版本（供页面回填）。 */
export type SaveHandler = () => Promise<{ id: string; version: string } | null>

/** 节流间隔（毫秒）。 */
const INTERVAL_MS = 10000

/**
 * 使用草稿自动保存。
 *
 * @param isDirty 内容是否有未保存变更
 * @param onSave 保存回调（返回 null 表示跳过/失败）
 * @param onConflict 版本冲突回调（409）
 */
export function useAutoSave(isDirty: () => boolean, onSave: SaveHandler, onConflict?: () => void) {
  const saving = ref(false)
  const savedAt = ref<Date | null>(null)
  const conflict = ref(false)
  const error = ref('')

  let timer: ReturnType<typeof setTimeout> | null = null

  /** 触发保存（幂等：保存中或内容未变跳过）。 */
  async function save(): Promise<void> {
    if (saving.value || !isDirty()) {
      return
    }
    saving.value = true
    conflict.value = false
    error.value = ''
    try {
      const result = await onSave()
      if (result) savedAt.value = new Date()
    } catch (caught) {
      // 409 版本冲突：标记冲突态，由页面提供恢复入口（查看最新/复制/覆盖）
      if (caught instanceof Error && caught.message.includes('冲突')) {
        conflict.value = true
        onConflict?.()
      } else {
        error.value = caught instanceof Error ? caught.message : '自动保存失败'
      }
    } finally {
      saving.value = false
      schedule()
    }
  }

  /** 调度下一次节流保存。 */
  function schedule(): void {
    if (timer) {
      clearTimeout(timer)
    }
    timer = setTimeout(() => {
      void save()
    }, INTERVAL_MS)
  }

  /** 内容变化后调用：重新计时。 */
  function touch(): void {
    schedule()
  }

  /** 失焦保存（切走页面/关闭前）。 */
  function flush(): void {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
    void save()
  }

  onBeforeUnmount(() => {
    if (timer) {
      clearTimeout(timer)
    }
  })

  return { saving, savedAt, conflict, error, save, touch, flush }
}
