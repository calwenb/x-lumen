<script setup lang="ts">
// A01 管理员登录：仅 OWNER/ADMIN 可进；登录失败统一提示（防枚举）；
// 非管理员登录后校验 roles 并登出（撤销令牌），提示无权限。
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'

import { useSessionStore } from '@/stores/session'
import type { SessionSnapshot } from '@/stores/session'
import XlLogo from '@/components/XlLogo.vue'

import { loginApi, logoutApi } from '../api/auth'

const router = useRouter()
const route = useRoute()
const session = useSessionStore()

const username = ref('')
const password = ref('')
const loading = ref(false)
const errorMessage = ref('')

// 管理后台准入角色：仅 OWNER/ADMIN
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
    <!-- A00 左侧：深 Ink 品牌面，反白 Logo + 双 Indigo 光柱 -->
    <section class="login__brand-panel" aria-label="xLumen 管理后台">
      <div class="login__pillars" aria-hidden="true">
        <span class="login__pillar login__pillar--left" />
        <span class="login__pillar login__pillar--right" />
      </div>
      <div class="login__brand-content">
        <XlLogo variant="full" reverse :size="46" wordmark="xLumen" />
        <h1 class="login__title">xLumen 管理后台</h1>
        <p class="login__subtitle">仅限空间所有者与管理员登录</p>
      </div>
    </section>

    <!-- A00 右侧：Paper 背景，约 420px 无厚重阴影表单 -->
    <section class="login__form-panel">
      <div class="login__form-wrap">
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
    </section>
  </main>
</template>

<style scoped>
.login {
  display: flex;
  min-height: 100vh;
}

/* A00 左侧 44%：深 Ink 品牌面 */
.login__brand-panel {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44%;
  overflow: hidden;
  background: var(--xl-text-primary);
}

/* 两条低对比 Indigo 纵向光柱，作为空间结构 */
.login__pillars {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: stretch;
  justify-content: center;
  gap: 16%;
}

.login__pillar {
  width: 64px;
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--xl-color-primary) 28%, transparent),
    color-mix(in srgb, var(--xl-color-primary) 6%, transparent)
  );
  filter: blur(1px);
}

.login__brand-content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--xl-space-4);
  text-align: center;
  color: #fff;
}

.login__title {
  margin: 0;
  font-size: 26px;
  font-weight: 650;
  letter-spacing: -0.01em;
}

.login__subtitle {
  margin: 0;
  color: color-mix(in srgb, #fff 62%, transparent);
  font-size: 14px;
}

/* A00 右侧 56%：Paper 背景 + 约 420px 表单 */
.login__form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56%;
  padding: var(--xl-space-8) var(--xl-space-4);
  background: var(--xl-bg-page);
}

.login__form-wrap {
  width: 100%;
  max-width: 420px;
}

.login__form {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-2);
}

.login__error {
  margin: var(--xl-space-1) 0;
}

.login__submit {
  width: 100%;
  margin-top: var(--xl-space-2);
}
</style>
