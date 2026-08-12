<script setup lang="ts">
// 应用根组件：顶栏（品牌、导航：首页/分类/标签/关于、搜索框、登录态，PROTOTYPE B01）与路由出口。
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { useSessionStore } from '@/stores/session'

import { logoutApi } from '@/modules/identity/api/auth'

const router = useRouter()
const session = useSessionStore()

const keyword = ref('')

async function handleLogout(): Promise<void> {
  if (session.refreshToken) {
    await logoutApi(session.refreshToken).catch(() => undefined)
  }
  session.clear()
  await router.push({ name: 'home' })
}

/** 顶栏搜索：跳转 B03 搜索页并携带关键词。 */
function submitSearch(): void {
  const q = keyword.value.trim()
  void router.push({ name: 'search', query: q ? { keyword: q } : {} })
  keyword.value = ''
}
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <RouterLink class="app-header__brand" to="/">xLumen 博客</RouterLink>
      <nav class="app-header__nav">
        <RouterLink class="app-header__link" to="/">首页</RouterLink>
        <RouterLink class="app-header__link" :to="{ name: 'search' }">分类</RouterLink>
        <RouterLink class="app-header__link" :to="{ name: 'search' }">标签</RouterLink>
        <RouterLink class="app-header__link" to="/about">关于</RouterLink>
      </nav>
      <form class="app-header__search" @submit.prevent="submitSearch">
        <input
          v-model="keyword"
          class="app-header__search-input"
          type="search"
          placeholder="搜索…"
          aria-label="搜索文章"
        />
      </form>
      <div class="app-header__account">
        <RouterLink v-if="!session.loggedIn" class="app-header__link" to="/login">登录</RouterLink>
        <template v-else>
          <span class="app-header__user">{{ session.snapshot?.username }}</span>
          <button type="button" class="app-header__link app-header__button" @click="handleLogout">
            登出
          </button>
        </template>
      </div>
    </header>
    <RouterView />
  </div>
</template>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  gap: var(--xl-space-6);
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
  white-space: nowrap;
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
  white-space: nowrap;
}

.app-header__link:hover {
  color: var(--xl-color-primary);
}

.app-header__search {
  flex: 1;
  max-width: 320px;
  margin-left: auto;
}

.app-header__search-input {
  width: 100%;
  box-sizing: border-box;
  padding: 6px 12px;
  border: 1px solid var(--xl-border);
  border-radius: 999px;
  background: var(--xl-bg-page);
  color: var(--xl-text-primary);
  font-size: 13px;
}

.app-header__search-input:focus {
  outline: none;
  border-color: var(--xl-color-primary);
}

.app-header__account {
  display: flex;
  align-items: center;
  gap: var(--xl-space-4);
}

.app-header__button {
  border: none;
  background: none;
  cursor: pointer;
}

.app-header__user {
  color: var(--xl-text-primary);
  font-size: 14px;
  white-space: nowrap;
}

@media (width <= 700px) {
  .app-header__nav {
    display: none;
  }

  .app-header__search {
    max-width: none;
  }
}
</style>
