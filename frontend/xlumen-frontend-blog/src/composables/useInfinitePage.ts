import { onBeforeUnmount, onMounted, ref, shallowRef, watch, type Ref } from 'vue'

export interface InfinitePageResult<T> {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
}

export interface UseInfinitePageOptions<T> {
  sentinel: Ref<HTMLElement | null>
  pageSize: number
  loadPage: (pageNo: number, pageSize: number) => Promise<InfinitePageResult<T>>
  getKey?: (item: T) => string
}

/**
 * 页码接口的滚动加载适配器：保留后端分页契约，只把页面交互变为累计追加。
 * 请求代次用于丢弃筛选条件切换后的迟到响应，避免旧列表覆盖新列表。
 */
export function useInfinitePage<T>(options: UseInfinitePageOptions<T>) {
  const items = shallowRef<T[]>([])
  const total = ref(0)
  const loading = ref(true)
  const loadingMore = ref(false)
  const error = ref(false)
  const loadMoreError = ref(false)
  const hasMore = ref(true)

  let generation = 0
  let nextPage = 1
  let observer: IntersectionObserver | null = null

  const keyOf = options.getKey ?? ((item: T) => String((item as { id?: string }).id ?? item))

  function appendUnique(records: T[]): number {
    const known = new Set(items.value.map(keyOf))
    const appended = records.filter((record) => {
      const key = keyOf(record)
      if (known.has(key)) return false
      known.add(key)
      return true
    })
    if (appended.length > 0) items.value = [...items.value, ...appended]
    return appended.length
  }

  async function loadFirst(): Promise<void> {
    const current = ++generation
    loading.value = true
    loadingMore.value = false
    error.value = false
    loadMoreError.value = false
    items.value = []
    total.value = 0
    hasMore.value = true
    nextPage = 1
    try {
      const page = await options.loadPage(1, options.pageSize)
      if (current !== generation) return
      items.value = page.records
      nextPage = 2
      total.value = page.total
      hasMore.value = page.records.length > 0 && page.records.length < page.total
    } catch {
      if (current === generation) error.value = true
    } finally {
      if (current === generation) loading.value = false
    }
  }

  async function loadMore(): Promise<void> {
    if (loading.value || loadingMore.value || !hasMore.value) return
    const current = generation
    loadingMore.value = true
    loadMoreError.value = false
    const requestedPage = nextPage
    try {
      const page = await options.loadPage(requestedPage, options.pageSize)
      if (current !== generation) return
      const appendedCount = appendUnique(page.records)
      nextPage = requestedPage + 1
      total.value = page.total
      // 若服务端重复返回同一页，不能让哨兵持续触发“加载更多”。
      hasMore.value =
        appendedCount > 0 &&
        page.records.length >= options.pageSize &&
        items.value.length < page.total
    } catch {
      if (current === generation) loadMoreError.value = true
    } finally {
      // 迟到响应也必须释放加载锁，否则筛选切换后会永久显示“加载更多”。
      loadingMore.value = false
    }
  }

  function retry(): Promise<void> {
    return loadFirst()
  }

  function retryMore(): Promise<void> {
    return loadMore()
  }

  onMounted(() => {
    observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) void loadMore()
      },
      { rootMargin: '300px 0px' },
    )
    if (options.sentinel.value) observer.observe(options.sentinel.value)
  })

  watch(options.sentinel, (element, previous) => {
    if (previous) observer?.unobserve(previous)
    if (element) observer?.observe(element)
  })

  onBeforeUnmount(() => {
    generation++
    observer?.disconnect()
    observer = null
  })

  return {
    items,
    total,
    loading,
    loadingMore,
    error,
    loadMoreError,
    hasMore,
    loadFirst,
    loadMore,
    retry,
    retryMore,
  }
}
