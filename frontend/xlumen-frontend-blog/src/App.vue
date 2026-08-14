<script setup lang="ts">
// 应用根组件：顶栏（品牌 Logo、导航：首页/分类/标签/关于、搜索框、登录态，PROTOTYPE B01）与路由出口。
// 顶栏基于 Element Plus 图标与胶囊激活态，内容展示组件保留定制样式（统一 --xl-* token）。
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ChatDotRound,
  EditPen,
  HomeFilled,
  CollectionTag,
  InfoFilled,
  Search,
  UserFilled,
} from '@element-plus/icons-vue'

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
      <RouterLink class="app-header__brand" to="/">
        <span class="app-header__logo" aria-hidden="true" />
        xLumen 博客
      </RouterLink>
      <nav class="app-header__nav">
        <RouterLink class="app-header__link" to="/">
          <el-icon class="app-header__link-icon"><HomeFilled /></el-icon>
          首页
        </RouterLink>
        <RouterLink class="app-header__link app-header__link--entry" :to="{ name: 'search' }">
          <el-icon class="app-header__link-icon"><CollectionTag /></el-icon>
          分类
        </RouterLink>
        <RouterLink class="app-header__link app-header__link--entry" :to="{ name: 'search' }">
          <el-icon class="app-header__link-icon"><CollectionTag /></el-icon>
          标签
        </RouterLink>
        <RouterLink class="app-header__link" to="/about">
          <el-icon class="app-header__link-icon"><InfoFilled /></el-icon>
          关于
        </RouterLink>
      </nav>
      <form class="app-header__search" @submit.prevent="submitSearch">
        <el-input
          v-model="keyword"
          class="app-header__search-input"
          type="search"
          placeholder="搜索…"
          aria-label="搜索知识"
          :prefix-icon="Search"
        />
      </form>
      <div class="app-header__account">
        <RouterLink v-if="!session.loggedIn" class="app-header__link" to="/login">登录</RouterLink>
        <template v-else>
          <RouterLink class="app-header__link app-header__link--icon" :to="{ name: 'chat' }">
            <el-icon class="app-header__link-icon"><ChatDotRound /></el-icon>
            AI 助理
          </RouterLink>
          <RouterLink class="app-header__link app-header__link--icon" :to="{ name: 'workbench' }">
            <el-icon class="app-header__link-icon"><EditPen /></el-icon>
            创作中心
          </RouterLink>
          <span class="app-header__user">
            <el-icon class="app-header__user-icon"><UserFilled /></el-icon>
            {{ session.snapshot?.username }}
          </span>
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
  display: flex;
  align-items: center;
  gap: var(--xl-space-2);
  color: var(--xl-color-primary);
  font-size: 17px;
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
}

.app-header__logo {
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  border-radius: 6px;
  background: linear-gradient(135deg, var(--xl-color-primary), var(--xl-color-ai));
}

.app-header__nav {
  display: flex;
  align-items: center;
  gap: var(--xl-space-1);
}

.app-header__link {
  display: inline-flex;
  align-items: center;
  gap: var(--xl-space-1);
  padding: 6px 12px;
  border-radius: 999px;
  color: var(--xl-text-secondary);
  font-size: 14px;
  text-decoration: none;
  white-space: nowrap;
}

.app-header__link:hover {
  background: var(--xl-bg-secondary);
  color: var(--xl-color-primary);
}

.app-header__link.router-link-active {
  background: color-mix(in srgb, var(--xl-color-primary) 10%, transparent);
  color: var(--xl-color-primary);
}

/* 分类/标签为搜索页工具入口（共享 search 路由）：不做激活态高亮，避免双高亮 */
.app-header__link--entry.router-link-active {
  background: none;
  color: var(--xl-text-secondary);
}

.app-header__link-icon {
  font-size: 15px;
}

.app-header__search {
  flex: 1;
  max-width: 320px;
  margin-left: auto;
}

.app-header__search-input {
  width: 100%;
}

.app-header__search-input :deep(.el-input__wrapper) {
  border-radius: 999px;
  background: var(--xl-bg-page);
  box-shadow: none;
  border: 1px solid var(--xl-border);
  padding-left: var(--xl-space-3);
}

.app-header__search-input :deep(.el-input__wrapper.is-focus) {
  border-color: var(--xl-color-primary);
}

.app-header__account {
  display: flex;
  align-items: center;
  gap: var(--xl-space-1);
}

.app-header__button {
  border: none;
  background: none;
  cursor: pointer;
}

.app-header__user {
  display: inline-flex;
  align-items: center;
  gap: var(--xl-space-1);
  margin: 0 var(--xl-space-1);
  color: var(--xl-text-primary);
  font-size: 14px;
  white-space: nowrap;
}

.app-header__user-icon {
  color: var(--xl-text-muted);
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
