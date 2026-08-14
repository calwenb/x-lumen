<script setup lang="ts">
// A01 管理员登录：仅 OWNER/ADMIN 可进；登录失败统一提示（防枚举）；
// 非管理员登录后校验 roles 并登出（撤销令牌），提示无权限。
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'

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
      <div class="login__brand">
        <span class="login__logo" aria-hidden="true" />
        <h1 class="login__title">xLumen 管理后台</h1>
      </div>
      <p class="login__subtitle">仅限空间所有者与管理员登录</p>
      <el-form class="login__form" label-position="top" size="large" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input
            v-model="username"
            name="username"
            placeholder="请输入用户名"
            :prefix-icon="User"
            autocomplete="username"
          />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="password"
            name="password"
            type="password"
            show-password
            placeholder="请输入密码"
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
          show-icon
          class="login__error"
        />
        <el-button type="primary" native-type="submit" class="login__submit" :loading="loading">
          {{ loading ? '登录中…' : '登 录' }}
        </el-button>
      </el-form>
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
  background:
    radial-gradient(
      1200px 600px at 15% -10%,
      color-mix(in srgb, var(--xl-color-primary) 12%, transparent),
      transparent 60%
    ),
    radial-gradient(
      1000px 500px at 110% 110%,
      color-mix(in srgb, var(--xl-color-ai) 10%, transparent),
      transparent 55%
    ),
    var(--xl-bg-page);
}

.login__card {
  width: 100%;
  max-width: 400px;
  padding: var(--xl-space-8) var(--xl-space-6);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-lg);
}

.login__brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--xl-space-2);
}

.login__logo {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--xl-color-primary), var(--xl-color-ai));
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

.login__error {
  margin-bottom: var(--xl-space-4);
}

.login__submit {
  width: 100%;
  margin-top: var(--xl-space-2);
}
</style>
