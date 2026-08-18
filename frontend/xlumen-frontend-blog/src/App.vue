<script setup lang="ts">
// 应用根组件：顶栏（品牌 Logo、主导航：知识/知识库/创作中心(登录态，F-0214)/AI小光、全局搜索框、
// 写知识 CTA、头像菜单，PROTOTYPE §5.1）与路由出口。
// 当前导航项高亮用 router-link-exact-active（首页 / 为全部路由父级，router-link-active 会全站匹配误高亮）。
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ChatDotRound, Collection, EditPen, HomeFilled, Monitor, Search } from '@element-plus/icons-vue'

import { useSessionStore } from '@/stores/session'

import { logoutApi } from '@/modules/identity/api/auth'

const router = useRouter()
const session = useSessionStore()

const keyword = ref('')

const avatarText = computed(() => (session.snapshot?.username ?? '?').slice(0, 1).toUpperCase())

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

/** 头像菜单命令（PROTOTYPE §5.1；个人设置为占位项，V2 提供）。 */
function handleAccountCommand(command: string): void {
  switch (command) {
    case 'my-kbs':
      void router.push({ name: 'kb-discovery', query: { mine: '1' } })
      break
    case 'favorites':
      void router.push({ name: 'favorites' })
      break
    case 'studio':
      void router.push({ name: 'workbench' })
      break
    case 'recycle-bin':
      void router.push({ name: 'recycle-bin' })
      break
    case 'logout':
      void handleLogout()
      break
  }
}

/** 移动端汉堡菜单命令。 */
function handleNavCommand(command: string): void {
  switch (command) {
    case 'home':
      void router.push({ name: 'home' })
      break
    case 'kb-discovery':
      void router.push({ name: 'kb-discovery' })
      break
    case 'studio':
      void router.push({ name: 'workbench' })
      break
    case 'chat':
      void router.push({ name: 'chat' })
      break
  }
}
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <RouterLink class="app-header__brand" to="/">
        <span class="app-header__logo" aria-hidden="true" />
        xLumen
      </RouterLink>
      <nav class="app-header__nav">
        <RouterLink class="app-header__link" to="/">
          <el-icon class="app-header__link-icon"><HomeFilled /></el-icon>
          知识
        </RouterLink>
        <RouterLink class="app-header__link" :to="{ name: 'kb-discovery' }">
          <el-icon class="app-header__link-icon"><Collection /></el-icon>
          知识库
        </RouterLink>
        <!-- 创作中心（F-0214）：一级导航，仅登录态显示 -->
        <RouterLink v-if="session.loggedIn" class="app-header__link" :to="{ name: 'workbench' }">
          <el-icon class="app-header__link-icon"><Monitor /></el-icon>
          创作中心
        </RouterLink>
        <RouterLink class="app-header__link" :to="{ name: 'chat' }">
          <el-icon class="app-header__link-icon"><ChatDotRound /></el-icon>
          AI小光
        </RouterLink>
      </nav>
      <div class="app-header__menu">
        <el-dropdown trigger="click" @command="handleNavCommand">
          <button type="button" class="app-header__hamburger" aria-label="打开导航菜单">☰</button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="home">知识</el-dropdown-item>
              <el-dropdown-item command="kb-discovery">知识库</el-dropdown-item>
              <el-dropdown-item v-if="session.loggedIn" command="studio">创作中心</el-dropdown-item>
              <el-dropdown-item command="chat">AI小光</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
      <form class="app-header__search" @submit.prevent="submitSearch">
        <el-input
          v-model="keyword"
          class="app-header__search-input"
          type="search"
          placeholder="搜索知识/知识库…"
          aria-label="搜索知识"
          :prefix-icon="Search"
        />
      </form>
      <div class="app-header__account">
        <RouterLink v-if="!session.loggedIn" class="app-header__login" to="/login"
          >登录 / 注册</RouterLink
        >
        <template v-else>
          <RouterLink class="app-header__write" :to="{ name: 'knowledge-new' }">
            <el-icon class="app-header__write-icon"><EditPen /></el-icon>
            写知识
          </RouterLink>
          <el-dropdown trigger="click" @command="handleAccountCommand">
            <button
              type="button"
              class="app-header__avatar"
              :aria-label="`${session.snapshot?.username ?? ''} 账号菜单`"
            >
              {{ avatarText }}
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="my-kbs">我的知识库</el-dropdown-item>
                <el-dropdown-item command="favorites">我的收藏</el-dropdown-item>
                <el-dropdown-item command="studio">创作中心</el-dropdown-item>
                <el-dropdown-item command="recycle-bin">回收站</el-dropdown-item>
                <el-dropdown-item disabled>个人设置（即将上线）</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
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

/* 当前导航项高亮（洋红系主色；exact 精确匹配：/ 为全部路由父级，泛匹配会全站误高亮） */
.app-header__link.router-link-exact-active {
  background: color-mix(in srgb, var(--xl-color-primary) 10%, transparent);
  color: var(--xl-color-primary);
}

.app-header__link-icon {
  font-size: 15px;
}

.app-header__menu {
  display: none;
}

.app-header__hamburger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-sm);
  background: var(--xl-bg-surface);
  color: var(--xl-text-primary);
  font-size: 16px;
  cursor: pointer;
}

.app-header__hamburger:hover {
  border-color: var(--xl-color-primary);
  color: var(--xl-color-primary);
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
  gap: var(--xl-space-2);
}

.app-header__login {
  padding: 6px 16px;
  border: 1px solid var(--xl-border);
  border-radius: 999px;
  color: var(--xl-text-secondary);
  font-size: 14px;
  text-decoration: none;
  white-space: nowrap;
}

.app-header__login:hover {
  border-color: var(--xl-color-primary);
  color: var(--xl-color-primary);
}

/* 写知识 CTA：洋红系实心主按钮 */
.app-header__write {
  display: inline-flex;
  align-items: center;
  gap: var(--xl-space-1);
  padding: 6px 16px;
  border-radius: 999px;
  background: var(--xl-color-primary);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
  transition: background var(--xl-transition);
}

.app-header__write:hover {
  background: var(--xl-color-primary-hover);
}

.app-header__write-icon {
  font-size: 14px;
}

.app-header__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--xl-color-primary), var(--xl-color-ai));
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

@media (width <= 700px) {
  .app-header {
    gap: var(--xl-space-3);
    padding: 0 var(--xl-space-3);
  }

  .app-header__nav {
    display: none;
  }

  .app-header__menu {
    display: block;
  }

  .app-header__search {
    max-width: none;
    margin-left: 0;
  }
}
</style>
