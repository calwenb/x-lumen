<script setup lang="ts">
// 应用根组件（V2 设计系统）：64px 吸顶头部（品牌 Logo、主导航「发现/知识库/动态/创作中心/AI小光」、
// 搜索胶囊、主题图标『仅视觉·固定浅色』、通知铃、写知识 CTA、头像菜单，FRONTEND.md §5.1）与路由出口。
// 导航高亮用 router-link-exact-active（首页 / 为全部路由父级，泛匹配会全站误高亮）。
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'

import { useSessionStore } from '@/stores/session'

import { logoutApi } from '@/modules/identity/api/auth'
import NotificationBell from '@/modules/notification/components/NotificationBell.vue'
import FloatingAssistant from '@/modules/chat/components/FloatingAssistant.vue'
import SiteTour from '@/modules/blog/components/SiteTour.vue'
import XlLogo from '@/components/XlLogo.vue'
import InitialAvatar from '@/components/InitialAvatar.vue'

const router = useRouter()
const session = useSessionStore()

const keyword = ref('')

const avatarText = computed(() => (session.snapshot?.username ?? '?').slice(0, 1).toUpperCase())

// 管理后台入口：新标签页跳转，可用 VITE_ADMIN_URL 覆盖（部署构建期由 deploy-blog.sh 写入）。
// 不做角色判断：非管理员进入后由后台自身的路由守卫统一处理。
// 兜底值=本地 dev 端口（9-07 端口方案：blog 6010/admin 6011，旧 5173/5174 作废）
const adminUrl = import.meta.env.VITE_ADMIN_URL ?? 'http://localhost:6011'

function openAdmin(): void {
  window.open(adminUrl, '_blank', 'noopener')
}

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
    case 'admin':
      openAdmin()
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
    case 'changelog':
      void router.push({ name: 'changelog' })
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
      <RouterLink class="app-header__brand" to="/" aria-label="xLumen 首页">
        <XlLogo :size="30" />
      </RouterLink>

      <nav class="app-header__nav">
        <RouterLink class="app-header__link" to="/">发现</RouterLink>
        <RouterLink class="app-header__link" :to="{ name: 'chat' }">
          AI小光<span class="app-header__ai-star" aria-hidden="true">✦</span>
        </RouterLink>
        <RouterLink class="app-header__link" :to="{ name: 'kb-discovery' }">知识库</RouterLink>
        <RouterLink class="app-header__link" :to="{ name: 'changelog' }">动态</RouterLink>
        <RouterLink v-if="session.loggedIn" class="app-header__link" :to="{ name: 'workbench' }">
          创作中心
        </RouterLink>
      </nav>

      <div class="app-header__menu">
        <el-dropdown trigger="click" @command="handleNavCommand">
          <button type="button" class="app-header__hamburger" aria-label="打开导航菜单">☰</button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="home">发现</el-dropdown-item>
              <el-dropdown-item command="chat">AI小光</el-dropdown-item>
              <el-dropdown-item command="kb-discovery">知识库</el-dropdown-item>
              <el-dropdown-item command="changelog">动态</el-dropdown-item>
              <el-dropdown-item v-if="session.loggedIn" command="studio">创作中心</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <div class="app-header__right">
        <form class="app-header__search" @submit.prevent="submitSearch">
          <el-input
            v-model="keyword"
            class="app-header__search-input"
            type="search"
            placeholder="搜索知识、文档、主题或问题…"
            aria-label="搜索知识"
          >
            <template #suffix>
              <el-icon class="app-header__search-icon"><Search /></el-icon>
            </template>
          </el-input>
        </form>

        <!-- 通用消息：AI 审核完成等站内提醒（仅登录态） -->
        <NotificationBell v-if="session.loggedIn" />

        <RouterLink
          v-if="session.loggedIn"
          class="app-header__write"
          :to="{ name: 'knowledge-new' }"
        >
          写知识
        </RouterLink>

        <template v-if="session.loggedIn">
          <el-dropdown trigger="click" @command="handleAccountCommand">
            <span
              class="app-header__avatar"
              role="button"
              tabindex="0"
              :aria-label="`${session.snapshot?.username ?? ''} 账号菜单`"
            >
              <InitialAvatar :name="avatarText" :size="32" />
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="my-kbs">我的知识库</el-dropdown-item>
                <el-dropdown-item command="favorites">我的收藏</el-dropdown-item>
                <el-dropdown-item command="studio">创作中心</el-dropdown-item>
                <el-dropdown-item command="recycle-bin">回收站</el-dropdown-item>
                <el-dropdown-item disabled>个人设置（即将上线）</el-dropdown-item>
                <el-dropdown-item command="admin" divided>管理后台</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>

        <RouterLink v-else class="app-header__login" to="/login">登录 / 注册</RouterLink>
      </div>
    </header>

    <RouterView />
    <!-- 全站悬浮小光：登录/访客均可用，右下角悬浮球 -->
    <FloatingAssistant />
    <!-- 站点 AI 导游：首次访问自动弹出的分步导览（登录/注册页不放行） -->
    <SiteTour />
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-header {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: var(--xl-space-8);
  height: var(--xl-header-h);
  padding: 0 var(--xl-content-pad);
  border-bottom: 1px solid var(--xl-border);
  background: var(--xl-bg-surface);
}

.app-header__brand {
  display: inline-flex;
  align-items: center;
  text-decoration: none;
}

.app-header__nav {
  display: flex;
  align-items: center;
  gap: var(--xl-space-2);
  height: 100%;
}

.app-header__link {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: var(--xl-space-1);
  height: 100%;
  padding: 0 var(--xl-space-3);
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
  text-decoration: none;
  white-space: nowrap;
  transition: color var(--xl-transition);
}

.app-header__link:hover {
  color: var(--xl-text-primary);
}

/* 当前导航项：Indigo 细下划线（V2 视觉语言，非胶囊底） */
.app-header__link.router-link-exact-active {
  color: var(--xl-color-primary);
  font-weight: var(--xl-fs-title-w);
}

.app-header__link.router-link-exact-active::after {
  content: '';
  position: absolute;
  left: var(--xl-space-3);
  right: var(--xl-space-3);
  bottom: 0;
  height: 2px;
  border-radius: 999px;
  background: var(--xl-color-primary);
}

/* AI小光旁的 Teal 四角星（AI 视觉标记，仅此处可用） */
.app-header__ai-star {
  color: var(--xl-color-ai);
  font-size: var(--xl-fs-caption);
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
  font-size: 18px;
  cursor: pointer;
}

.app-header__right {
  display: flex;
  align-items: center;
  gap: var(--xl-space-2);
  margin-left: auto;
}

.app-header__search {
  width: 260px;
}

.app-header__search-input :deep(.el-input__wrapper) {
  border-radius: 999px;
  background: var(--xl-bg-page);
  box-shadow: none;
  border: 1px solid var(--xl-border);
  padding-left: var(--xl-space-4);
}

.app-header__search-input :deep(.el-input__wrapper.is-focus),
.app-header__search-input :deep(.el-input__wrapper:hover) {
  border-color: var(--xl-color-primary);
}

.app-header__search-icon {
  color: var(--xl-text-secondary);
}

/* 写知识 CTA：Indigo 实心主按钮 */
.app-header__write {
  display: inline-flex;
  align-items: center;
  padding: 7px 18px;
  border-radius: 999px;
  background: var(--xl-color-primary);
  color: #fff;
  font-size: var(--xl-fs-body);
  font-weight: var(--xl-fs-title-w);
  text-decoration: none;
  white-space: nowrap;
  transition: background var(--xl-transition);
}

.app-header__write:hover {
  background: var(--xl-color-primary-hover);
}

.app-header__avatar {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
  outline: none;
}

.app-header__login {
  padding: 7px 16px;
  border: 1px solid var(--xl-border);
  border-radius: 999px;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
  text-decoration: none;
  white-space: nowrap;
}

.app-header__login:hover {
  border-color: var(--xl-color-primary);
  color: var(--xl-color-primary);
}

@media (width <= 900px) {
  .app-header {
    gap: var(--xl-space-3);
    padding: 0 var(--xl-space-4);
  }

  .app-header__nav {
    display: none;
  }

  .app-header__menu {
    display: block;
  }

  .app-header__search {
    width: 160px;
  }
}

@media (width <= 700px) {
  .app-header__search {
    display: none;
  }

  .app-header__write {
    display: none;
  }
}
</style>
