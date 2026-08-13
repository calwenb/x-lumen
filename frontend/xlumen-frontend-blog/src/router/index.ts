import { createRouter, createWebHistory } from 'vue-router'

import { useSessionStore } from '@/stores/session'

// 路由元数据访问级别（FRONTEND.md §9）：guest 未登录可访问 / authenticated 需登录 / workspace 需登录且已选空间
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
      name: 'home',
      component: () => import('@/modules/blog/pages/HomePage.vue'),
      meta: { title: '首页' },
    },
    {
      path: '/articles/:id',
      name: 'article-detail',
      component: () => import('@/modules/publishing/pages/ArticleDetailPage.vue'),
      meta: { title: '文章详情' },
    },
    {
      path: '/search',
      name: 'search',
      component: () => import('@/modules/publishing/pages/SearchPage.vue'),
      meta: { title: '搜索' },
    },
    {
      path: '/about',
      name: 'about',
      component: () => import('@/modules/blog/pages/AboutPage.vue'),
      meta: { title: '关于' },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/modules/identity/pages/LoginPage.vue'),
      meta: { guest: true, title: '登录' },
    },
    {
      path: '/studio',
      name: 'workbench',
      component: () => import('@/modules/workbench/pages/WorkbenchPage.vue'),
      meta: { authenticated: true, title: '创作工作台' },
    },
    {
      path: '/studio/articles',
      name: 'article-list',
      component: () => import('@/modules/content/pages/ArticleListPage.vue'),
      meta: { authenticated: true, title: '文章管理' },
    },
    {
      path: '/studio/articles/new',
      name: 'article-new',
      component: () => import('@/modules/content/pages/ArticleEditorPage.vue'),
      meta: { authenticated: true, title: '新建文章' },
    },
    {
      path: '/studio/articles/:id/edit',
      name: 'article-edit',
      component: () => import('@/modules/content/pages/ArticleEditorPage.vue'),
      meta: { authenticated: true, title: '编辑文章' },
    },
    {
      path: '/chat',
      name: 'chat',
      component: () => import('@/modules/chat/pages/ChatPage.vue'),
      meta: { title: 'AI 助理' },
    },
    {
      path: '/studio/writing',
      name: 'writing',
      component: () => import('@/modules/ai/pages/AiWritePage.vue'),
      meta: { authenticated: true, title: 'AI 写作' },
    },
    {
      path: '/studio/review',
      name: 'review-center',
      component: () => import('@/modules/publishing/pages/ReviewCenterPage.vue'),
      meta: { authenticated: true, title: '审核中心' },
    },
    {
      path: '/studio/releases',
      name: 'release-list',
      component: () => import('@/modules/publishing/pages/ReleasePage.vue'),
      meta: { authenticated: true, title: '发布管理' },
    },
    {
      path: '/studio/knowledge',
      name: 'index-status',
      component: () => import('@/modules/knowledge/pages/IndexStatusPage.vue'),
      meta: { authenticated: true, title: 'RAG 索引' },
    },
  ],
})

// 访问控制守卫（FRONTEND.md §9）：guest 页已登录跳首页；authenticated 页未登录跳登录页并携带回跳地址。
// 会话快照只在内存（刷新令牌不持久化），页面刷新后需重新登录（M02 MVP 约束）。
router.beforeEach((to) => {
  const session = useSessionStore()
  if (to.meta.authenticated && !session.loggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guest && session.loggedIn) {
    return { name: 'home' }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - xLumen 博客` : 'xLumen 博客'
})

export default router
