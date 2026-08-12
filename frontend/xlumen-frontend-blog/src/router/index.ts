import { createRouter, createWebHistory } from 'vue-router'

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
  ],
})

// 全局守卫：骨架阶段仅设置标题；访问控制在 M02（身份）落地后启用
router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - xLumen 博客` : 'xLumen 博客'
})

export default router
