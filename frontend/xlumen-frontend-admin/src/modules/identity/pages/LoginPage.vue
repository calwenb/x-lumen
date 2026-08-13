<script setup lang="ts">
// A01 管理员登录：仅 OWNER/ADMIN 可进；登录失败统一提示（防枚举）；
// 非管理员登录后校验 roles 并登出（撤销令牌），提示无权限。
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useSessionStore } from '@/stores/session'
import type { SessionSnapshot } from '@/stores/session'

import { loginApi, logoutApi } from '../api/auth'

const router = useRouter()
const route = useRoute()
const session = useSessionStore()

const username = ref('')
const password = ref('')
const loading = ref(false)
const errorMessage = ref('')

// 管理后台准入角色（F-0101）：仅 OWNER/ADMIN
const ADMIN_ROLES = ['OWNER', 'ADMIN']

async function submit(): Promise<void> {
  errorMessage.value = ''
  loading.value = true
  try {
    const token = await loginApi(username.value.trim(), password.value)
    if (!token.user.roles.some((role) => ADMIN_ROLES.includes(role))) {
      // 非管理员：撤销刚签发的刷新令牌，不建立会话
      await logoutApi(token.refreshToken).catch(() => undefined)
      errorMessage.value = '仅管理员可访问管理后台'
      return
    }
    const snapshot: SessionSnapshot = {
      userId: token.user.userId,
      username: token.user.username,
      workspaceId: token.workspaceId,
      roles: token.user.roles,
      // exactOptionalPropertyTypes：无邮箱时不携带 email 属性
      ...(token.user.email ? { email: token.user.email } : {}),
    }
    // 会话快照整体写入（FRONTEND.md §7 establish 原则）
    session.establish(snapshot, token.accessToken, token.refreshToken)
    // 登录后进入空间设置；有回跳目标则回跳
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/settings'
    await router.push(redirect)
  } catch {
    // 防枚举：登录失败不区分「账号不存在」与「密码错误」
    errorMessage.value = '用户名或密码错误'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login">
    <div class="login__card">
      <h1 class="login__title">xLumen 管理后台</h1>
      <p class="login__subtitle">仅限空间所有者与管理员登录</p>
      <form class="login__form" @submit.prevent="submit">
        <label class="login__field">
          <span>用户名</span>
          <input
            v-model="username"
            name="username"
            required
            minlength="3"
            maxlength="32"
            autocomplete="username"
          />
        </label>
        <label class="login__field">
          <span>密码</span>
          <input
            v-model="password"
            name="password"
            type="password"
            required
            minlength="8"
            maxlength="64"
            autocomplete="current-password"
          />
        </label>
        <p v-if="errorMessage" class="login__error" role="alert">{{ errorMessage }}</p>
        <button type="submit" class="login__submit" :disabled="loading">
          {{ loading ? '登录中…' : '登录' }}
        </button>
      </form>
    </div>
  </main>
</template>

<style scoped>
.login {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: var(--xl-space-4);
}

.login__card {
  width: 100%;
  max-width: 400px;
  padding: var(--xl-space-8) var(--xl-space-6);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
}

.login__title {
  margin: 0;
  color: var(--xl-text-primary);
  font-size: 24px;
  text-align: center;
}

.login__subtitle {
  margin: var(--xl-space-2) 0 var(--xl-space-6);
  color: var(--xl-text-secondary);
  text-align: center;
}

.login__form {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-4);
}

.login__field {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-1);
  color: var(--xl-text-secondary);
  font-size: 13px;
}

.login__field input {
  padding: var(--xl-space-2) var(--xl-space-3);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius);
  color: var(--xl-text-primary);
  font-size: 14px;
}

.login__field input:focus {
  outline: none;
  border-color: var(--xl-color-primary);
}

.login__error {
  margin: 0;
  color: var(--xl-color-danger);
  font-size: 13px;
}

.login__submit {
  padding: var(--xl-space-3);
  border: none;
  border-radius: var(--xl-radius);
  background: var(--xl-color-primary);
  color: #fff;
  font-size: 15px;
  cursor: pointer;
}

.login__submit:hover {
  background: var(--xl-color-primary-hover);
}

.login__submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
