<script setup lang="ts">
// 应用根组件：左侧边栏（品牌 + 菜单：空间设置/模型配置/AI 调用追踪/站点动态/审计日志 + 用户名 + 登出）+ 路由出口。
// 登录页（guest）不渲染侧边栏，仅路由出口。侧栏基于 Element Plus el-menu（EP 接入后统一视觉）。
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Document, Monitor, Operation, Setting, View } from '@element-plus/icons-vue'

import { logoutApi } from '@/modules/identity/api/auth'
import { useSessionStore } from '@/stores/session'
import XlLogo from '@/components/XlLogo.vue'
import InitialAvatar from '@/components/InitialAvatar.vue'

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
      <div class="app-sidebar__brand">
        <XlLogo variant="icon" :size="26" class="app-sidebar__logo" />
        <span class="app-sidebar__brand-text">xLumen 管理后台</span>
      </div>
      <el-menu class="app-sidebar__menu" :default-active="route.path" router>
        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <span>空间设置</span>
        </el-menu-item>
        <el-menu-item index="/models">
          <el-icon><Operation /></el-icon>
          <span>模型配置</span>
        </el-menu-item>
        <el-menu-item index="/ai-traces">
          <el-icon><Monitor /></el-icon>
          <span>AI 调用追踪</span>
        </el-menu-item>
        <el-menu-item index="/changelogs">
          <el-icon><Document /></el-icon>
          <span>站点动态</span>
        </el-menu-item>
        <el-menu-item index="/audit-logs">
          <el-icon><View /></el-icon>
          <span>审计日志</span>
        </el-menu-item>
      </el-menu>
      <div class="app-sidebar__footer">
        <div class="app-sidebar__user">
          <InitialAvatar :name="session.snapshot?.username ?? ''" :size="26" />
          <span class="app-sidebar__username">{{ session.snapshot?.username }}</span>
        </div>
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
  width: 232px;
  flex-shrink: 0;
  padding: var(--xl-space-4);
  border-right: 1px solid var(--xl-border);
  background: var(--xl-bg-surface);
}

.app-sidebar__brand {
  display: flex;
  align-items: center;
  gap: var(--xl-space-2);
  padding: var(--xl-space-1) var(--xl-space-1) var(--xl-space-2);
  white-space: nowrap;
}

.app-sidebar__logo {
  flex-shrink: 0;
}

.app-sidebar__brand-text {
  color: var(--xl-text-primary);
  font-size: 16px;
  font-weight: 650;
  letter-spacing: -0.01em;
}

.app-sidebar__menu {
  border-right: none;
}

.app-sidebar__menu :deep(.el-menu-item) {
  height: 40px;
  margin-bottom: 2px;
  border-radius: var(--xl-radius);
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.app-sidebar__menu :deep(.el-menu-item:hover) {
  background: var(--xl-bg-secondary);
  color: var(--xl-text-primary);
}

.app-sidebar__menu :deep(.el-menu-item.is-active) {
  background: color-mix(in srgb, var(--xl-color-primary) 10%, transparent);
  color: var(--xl-color-primary);
  font-weight: 600;
}

.app-sidebar__footer {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-2);
  margin-top: auto;
  padding-top: var(--xl-space-4);
  border-top: 1px solid var(--xl-border);
}

.app-sidebar__user {
  display: flex;
  align-items: center;
  gap: var(--xl-space-2);
  padding: var(--xl-space-1);
  color: var(--xl-text-primary);
  font-size: 14px;
  overflow-wrap: anywhere;
}

.app-sidebar__username {
  flex: 1;
  min-width: 0;
}

.app-sidebar__logout {
  padding: var(--xl-space-2) var(--xl-space-3);
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
