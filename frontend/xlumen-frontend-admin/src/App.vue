<script setup lang="ts">
// 应用根组件：左侧边栏（品牌 + 菜单：空间设置/模型配置/审计日志 + 用户名 + 登出）+ 路由出口。
// 登录页（guest）不渲染侧边栏，仅路由出口。侧栏基于 Element Plus el-menu（EP 接入后统一视觉）。
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Operation, Setting, User, View } from '@element-plus/icons-vue'

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
      <div class="app-sidebar__brand">
        <span class="app-sidebar__logo" aria-hidden="true" />
        xLumen 管理后台
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
        <el-menu-item index="/audit-logs">
          <el-icon><View /></el-icon>
          <span>审计日志</span>
        </el-menu-item>
      </el-menu>
      <div class="app-sidebar__footer">
        <span class="app-sidebar__user">
          <el-icon><User /></el-icon>
          {{ session.snapshot?.username }}
        </span>
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
  display: flex;
  align-items: center;
  gap: var(--xl-space-2);
  color: var(--xl-color-primary);
  font-size: 17px;
  font-weight: 600;
  white-space: nowrap;
}

.app-sidebar__logo {
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  border-radius: 6px;
  background: linear-gradient(135deg, var(--xl-color-primary), var(--xl-color-ai));
}

.app-sidebar__menu {
  border-right: none;
}

.app-sidebar__menu :deep(.el-menu-item) {
  height: 40px;
  border-radius: var(--xl-radius);
  color: var(--xl-text-secondary);
}

.app-sidebar__menu :deep(.el-menu-item:hover) {
  background: var(--xl-bg-secondary);
  color: var(--xl-color-primary);
}

.app-sidebar__menu :deep(.el-menu-item.is-active) {
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
  display: flex;
  align-items: center;
  gap: var(--xl-space-2);
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
