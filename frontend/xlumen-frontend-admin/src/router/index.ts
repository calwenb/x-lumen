import { createRouter, createWebHistory } from 'vue-router'

// 路由元数据访问级别（FRONTEND.md §9）：admin 全部页面需登录且为管理员（F-0101/0103）
declare module 'vue-router' {
  interface RouteMeta {
    guest?: boolean
    authenticated?: boolean
    workspace?: boolean
    title?: string
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'dashboard',
      component: () => import('@/modules/workspace/pages/DashboardPage.vue'),
      meta: { title: '管理后台', authenticated: true },
    },
  ],
})

// 全局守卫：骨架阶段仅设置标题；管理员准入校验在 M02（身份）落地后启用
router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - xLumen` : 'xLumen 管理后台'
})

export default router
