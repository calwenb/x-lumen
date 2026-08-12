<script setup lang="ts">
// B08 登录/注册（MVP · F-0101）：登录/注册切换；登录失败不暴露账号是否存在（后端统一提示）；
// 注册成功即建空间；登录后进入博客首页（PROTOTYPE §7.7）。
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

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

function switchMode(next: 'login' | 'register'): void {
  mode.value = next
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
    <h1 class="auth__title">欢迎使用 xLumen</h1>
    <p class="auth__subtitle">注册即创建个人工作空间</p>
    <div class="auth__card">
      <div class="auth__tabs" role="tablist">
        <button type="button" role="tab" :aria-selected="isLogin" :class="{ active: isLogin }" @click="switchMode('login')">
          登录
        </button>
        <button type="button" role="tab" :aria-selected="!isLogin" :class="{ active: !isLogin }" @click="switchMode('register')">
          注册
        </button>
      </div>
      <form class="auth__form" @submit.prevent="submit">
        <label class="auth__field">
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
        <label v-if="!isLogin" class="auth__field">
          <span>邮箱（可选）</span>
          <input v-model="email" name="email" type="email" autocomplete="email" />
        </label>
        <label class="auth__field">
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
        <p v-if="errorMessage" class="auth__error" role="alert">{{ errorMessage }}</p>
        <button type="submit" class="auth__submit" :disabled="loading">
          {{ loading ? '处理中…' : isLogin ? '登录' : '注册' }}
        </button>
      </form>
    </div>
  </main>
</template>

<style scoped>
.auth {
  max-width: 420px;
  margin: 0 auto;
  padding: var(--xl-space-8) var(--xl-space-4);
}

.auth__title {
  color: var(--xl-text-primary);
  font-size: 24px;
  text-align: center;
}

.auth__subtitle {
  color: var(--xl-text-secondary);
  text-align: center;
}

.auth__card {
  margin-top: var(--xl-space-6);
  padding: var(--xl-space-6);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
}

.auth__tabs {
  display: flex;
  gap: var(--xl-space-4);
  margin-bottom: var(--xl-space-6);
}

.auth__tabs button {
  padding: var(--xl-space-2) 0;
  border: none;
  border-bottom: 2px solid transparent;
  background: none;
  color: var(--xl-text-secondary);
  font-size: 16px;
  cursor: pointer;
}

.auth__tabs button.active {
  border-bottom-color: var(--xl-color-primary);
  color: var(--xl-color-primary);
}

.auth__form {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-4);
}

.auth__field {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-1);
  color: var(--xl-text-secondary);
}

.auth__field input {
  padding: var(--xl-space-2) var(--xl-space-3);
  border: 1px solid var(--xl-border);
  border-radius: 8px;
  color: var(--xl-text-primary);
  font-size: 14px;
}

.auth__error {
  color: #d92d20;
  font-size: 13px;
}

.auth__submit {
  padding: var(--xl-space-3);
  border: none;
  border-radius: 8px;
  background: var(--xl-color-primary);
  color: #fff;
  font-size: 15px;
  cursor: pointer;
}

.auth__submit:hover {
  background: var(--xl-color-primary-hover);
}

.auth__submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
