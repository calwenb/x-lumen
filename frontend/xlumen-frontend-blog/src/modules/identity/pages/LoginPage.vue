<script setup lang="ts">
// B08 登录/注册（MVP · F-0101）：登录/注册切换；登录失败不暴露账号是否存在（后端统一提示）；
// 注册成功即建空间；登录后进入博客首页（PROTOTYPE §7.7）。
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'

import { useSessionStore } from '@/stores/session'
import type { SessionSnapshot } from '@/stores/session'

import { loginApi, registerApi } from '../api/auth'

const router = useRouter()
const route = useRoute()
const session = useSessionStore()

const mode = ref<'login' | 'register'>('login')
const username = ref('')
const password = ref('')
const email = ref('')
const loading = ref(false)
const errorMessage = ref('')

const isLogin = computed(() => mode.value === 'login')

/** tab 切换（v-model 已同步模式）：仅清除错误提示。 */
function onTabChange(): void {
  errorMessage.value = ''
}

async function submit(): Promise<void> {
  errorMessage.value = ''
  loading.value = true
  try {
    const token = isLogin.value
      ? await loginApi(username.value.trim(), password.value)
      : await registerApi(username.value.trim(), password.value, email.value.trim() || undefined)
    const snapshot: SessionSnapshot = {
      userId: token.user.userId,
      username: token.user.username,
      workspaceId: token.workspaceId,
      roles: token.user.roles,
      // exactOptionalPropertyTypes：无邮箱时不携带 email 属性
      ...(token.user.email ? { email: token.user.email } : {}),
    }
    // 会话快照整体写入（FRONTEND.md §7 accept 原则）
    session.establish(snapshot, token.accessToken, token.refreshToken)
    // 登录后进入博客首页（B08 核心交互）；有回跳目标则回跳
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.push(redirect)
  } catch (error: unknown) {
    errorMessage.value = error instanceof Error ? error.message : '操作失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth">
    <div class="auth__brand">
      <span class="auth__logo" aria-hidden="true" />
      <h1 class="auth__title">欢迎使用 xLumen</h1>
    </div>
    <p class="auth__subtitle">注册即创建个人工作空间</p>
    <div class="auth__card">
      <el-tabs v-model="mode" class="auth__tabs" @tab-change="onTabChange">
        <el-tab-pane label="登录" name="login" />
        <el-tab-pane label="注册" name="register" />
      </el-tabs>
      <el-form class="auth__form" label-position="top" size="large" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input
            v-model="username"
            name="username"
            placeholder="请输入用户名"
            :prefix-icon="User"
            autocomplete="username"
          />
        </el-form-item>
        <el-form-item v-if="!isLogin" label="邮箱（可选）">
          <el-input
            v-model="email"
            name="email"
            type="email"
            placeholder="name@example.com"
            autocomplete="email"
          />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="password"
            name="password"
            type="password"
            show-password
            placeholder="至少 8 位"
            :prefix-icon="Lock"
            autocomplete="current-password"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-alert
          v-if="errorMessage"
          :title="errorMessage"
          type="error"
          :closable="false"
          class="auth__error"
          show-icon
        />
        <el-button type="primary" native-type="submit" class="auth__submit" :loading="loading">
          {{ loading ? '处理中…' : isLogin ? '登录' : '注册' }}
        </el-button>
      </el-form>
    </div>
  </main>
</template>

<style scoped>
.auth {
  max-width: 420px;
  margin: 0 auto;
  padding: var(--xl-space-8) var(--xl-space-4);
  min-height: calc(100vh - 56px);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(
      800px 400px at 50% -10%,
      color-mix(in srgb, var(--xl-color-primary) 8%, transparent),
      transparent 60%
    ),
    var(--xl-bg-page);
}

.auth__brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--xl-space-2);
}

.auth__logo {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--xl-color-primary), var(--xl-color-ai));
}

.auth__title {
  margin: 0;
  color: var(--xl-text-primary);
  font-size: 24px;
  text-align: center;
}

.auth__subtitle {
  margin: var(--xl-space-2) 0 0;
  color: var(--xl-text-secondary);
  text-align: center;
}

.auth__card {
  width: 100%;
  margin-top: var(--xl-space-6);
  padding: var(--xl-space-6);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-md);
}

.auth__tabs :deep(.el-tabs__header) {
  margin-bottom: var(--xl-space-6);
}

.auth__tabs :deep(.el-tabs__item) {
  font-size: 15px;
}

.auth__error {
  margin-bottom: var(--xl-space-4);
}

.auth__submit {
  width: 100%;
}
</style>
