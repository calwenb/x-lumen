import { createRouter, createWebHistory } from 'vue-router'

import { useSessionStore } from '@/stores/session'

// 路由元数据访问级别（FRONTEND.md §9）：guest 未登录可访问 / authenticated 需登录且为管理员（OWNER|ADMIN）
declare module 'vue-router' {
  interface RouteMeta {
    guest?: boolean
    authenticated?: boolean
    title?: string
  }
}

// 管理后台准入角色（F-0101）：仅 OWNER/ADMIN 可进
const ADMIN_ROLES = ['OWNER', 'ADMIN']

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/modules/identity/pages/LoginPage.vue'),
      meta: { guest: true, title: '登录' },
    },
    {
      path: '/',
      redirect: { name: 'settings' },
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('@/modules/workspace/pages/WorkspaceSettingsPage.vue'),
      meta: { authenticated: true, title: '空间设置' },
    },
    {
      path: '/models',
      name: 'models',
      component: () => import('@/modules/model/pages/ModelConfigPage.vue'),
      meta: { authenticated: true, title: '模型配置' },
    },
    {
      path: '/audit-logs',
      name: 'audit-logs',
      component: () => import('@/modules/audit/pages/AuditLogPage.vue'),
      meta: { authenticated: true, title: '审计日志' },
    },
  ],
})

// 访问控制守卫（FRONTEND.md §9）：authenticated 页需登录且含 OWNER/ADMIN；guest 页已登录跳空间设置。
router.beforeEach((to) => {
  const session = useSessionStore()
  if (to.meta.authenticated) {
    if (!session.loggedIn) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
    const roles = session.snapshot?.roles ?? []
    if (!roles.some((role) => ADMIN_ROLES.includes(role))) {
      // 非管理员：清理会话并回登录页（A01 准入）
      session.clear()
      return { name: 'login' }
    }
  }
  if (to.meta.guest && session.loggedIn) {
    return { name: 'settings' }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - xLumen 管理后台` : 'xLumen 管理后台'
})

export default router
