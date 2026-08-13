<script setup lang="ts">
// 应用根组件：左侧边栏（品牌 + 菜单：空间设置/模型配置/审计日志 + 用户名 + 登出）+ 路由出口。
// 登录页（guest）不渲染侧边栏，仅路由出口。
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { logoutApi } from '@/modules/identity/api/auth'
import { useSessionStore } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const showShell = computed(() => !route.meta.guest)

async function handleLogout(): Promise<void> {
  if (session.refreshToken) {
    await logoutApi(session.refreshToken).catch(() => undefined)
  }
  session.clear()
  await router.push({ name: 'login' })
}
</script>

<template>
  <div v-if="showShell" class="app-shell">
    <aside class="app-sidebar">
      <div class="app-sidebar__brand">xLumen 管理后台</div>
      <nav class="app-sidebar__nav">
        <RouterLink class="app-sidebar__link" :to="{ name: 'settings' }">空间设置</RouterLink>
        <RouterLink class="app-sidebar__link" :to="{ name: 'models' }">模型配置</RouterLink>
        <RouterLink class="app-sidebar__link" :to="{ name: 'audit-logs' }">审计日志</RouterLink>
      </nav>
      <div class="app-sidebar__footer">
        <span class="app-sidebar__user">{{ session.snapshot?.username }}</span>
        <button type="button" class="app-sidebar__logout" @click="handleLogout">登出</button>
      </div>
    </aside>
    <div class="app-main">
      <RouterView />
    </div>
  </div>
  <RouterView v-else />
</template>

<style scoped>
.app-shell {
  display: flex;
  min-height: 100vh;
}

.app-sidebar {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-6);
  width: 220px;
  flex-shrink: 0;
  padding: var(--xl-space-4);
  border-right: 1px solid var(--xl-border);
  background: var(--xl-bg-surface);
}

.app-sidebar__brand {
  color: var(--xl-color-primary);
  font-size: 17px;
  font-weight: 600;
}

.app-sidebar__nav {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-1);
}

.app-sidebar__link {
  padding: var(--xl-space-2) var(--xl-space-3);
  border-radius: var(--xl-radius);
  color: var(--xl-text-secondary);
  font-size: 14px;
  text-decoration: none;
}

.app-sidebar__link:hover {
  background: var(--xl-bg-secondary);
  color: var(--xl-color-primary);
}

.app-sidebar__link.router-link-active {
  background: color-mix(in srgb, var(--xl-color-primary) 10%, transparent);
  color: var(--xl-color-primary);
}

.app-sidebar__footer {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-2);
  margin-top: auto;
}

.app-sidebar__user {
  color: var(--xl-text-primary);
  font-size: 14px;
  overflow-wrap: anywhere;
}

.app-sidebar__logout {
  padding: var(--xl-space-2);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius);
  background: transparent;
  color: var(--xl-text-secondary);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.app-sidebar__logout:hover {
  border-color: var(--xl-color-primary);
  color: var(--xl-color-primary);
}

.app-main {
  flex: 1;
  min-width: 0;
}
</style>
