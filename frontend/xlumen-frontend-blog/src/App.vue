<script setup lang="ts">
// 应用根组件：顶栏（站点名 + 登录态入口，B08 登录后头像菜单出现）与路由出口。
import { useRouter } from 'vue-router'

import { useSessionStore } from '@/stores/session'

import { logoutApi } from '@/modules/identity/api/auth'

const router = useRouter()
const session = useSessionStore()

async function handleLogout(): Promise<void> {
  if (session.refreshToken) {
    await logoutApi(session.refreshToken).catch(() => undefined)
  }
  session.clear()
  await router.push({ name: 'home' })
}
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <RouterLink class="app-header__brand" to="/">xLumen 博客</RouterLink>
      <nav class="app-header__nav">
        <RouterLink v-if="!session.loggedIn" class="app-header__link" to="/login">登录</RouterLink>
        <template v-else>
          <span class="app-header__user">{{ session.snapshot?.username }}</span>
          <button type="button" class="app-header__link app-header__button" @click="handleLogout">
            登出
          </button>
        </template>
      </nav>
    </header>
    <RouterView />
  </div>
</template>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--xl-space-6);
  height: 56px;
  border-bottom: 1px solid var(--xl-border);
  background: var(--xl-bg-surface);
}

.app-header__brand {
  color: var(--xl-color-primary);
  font-size: 17px;
  font-weight: 600;
  text-decoration: none;
}

.app-header__nav {
  display: flex;
  align-items: center;
  gap: var(--xl-space-4);
}

.app-header__link {
  color: var(--xl-text-secondary);
  font-size: 14px;
  text-decoration: none;
}

.app-header__button {
  border: none;
  background: none;
  cursor: pointer;
}

.app-header__user {
  color: var(--xl-text-primary);
  font-size: 14px;
}
</style>
