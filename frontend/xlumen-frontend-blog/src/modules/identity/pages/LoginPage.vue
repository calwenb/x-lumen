<script setup lang="ts">
// B08 登录/注册（MVP · ）：登录/注册切换；登录失败不暴露账号是否存在（后端统一提示）；
// 注册成功即建空间；登录后进入博客首页（PROTOTYPE §7.7）。
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'

import { useSessionStore } from '@/stores/session'
import type { SessionSnapshot } from '@/stores/session'
import XlLogo from '@/components/XlLogo.vue'

import { loginApi, registerApi } from '../api/auth'

const router = useRouter()
const route = useRoute()
const session = useSessionStore()

const mode = ref<'login' | 'register'>(route.name === 'register' ? 'register' : 'login')
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
    <!-- 品牌区（42）：Paper 底，Logo + 标题 + 副标题，两条抽象 Indigo 光柱 + 一颗 AI Teal 星 -->
    <section class="auth__brand">
      <div class="auth__brand-inner">
        <div class="auth__beams" aria-hidden="true">
          <span class="auth__beam auth__beam--l"></span>
          <span class="auth__beam auth__beam--r"></span>
          <span class="auth__star">✦</span>
        </div>
        <XlLogo variant="icon" :size="42" class="auth__logo" />
        <h1 class="auth__title">欢迎使用 xLumen</h1>
        <p class="auth__subtitle">注册即创建个人工作空间</p>
      </div>
    </section>

    <!-- 表单区（58）：白底，约 420px 居中表单 -->
    <section class="auth__panel">
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
    </section>
  </main>
</template>

<style scoped>
.auth {
  display: grid;
  grid-template-columns: 42% minmax(0, 1fr);
  min-height: calc(100vh - var(--xl-header-h));
  background: var(--xl-bg-page);
}

/* ===== 品牌区 ===== */
.auth__brand {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background:
    radial-gradient(
      90% 70% at 20% 10%,
      color-mix(in srgb, var(--xl-color-primary) 10%, transparent),
      transparent 60%
    ),
    var(--xl-bg-page);
}

.auth__brand-inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--xl-space-3);
  text-align: center;
}

.auth__beams {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--xl-space-6);
  margin-bottom: var(--xl-space-4);
}

.auth__beam {
  width: 40px;
  height: 128px;
  border-radius: 999px;
  background: linear-gradient(
    180deg,
    transparent,
    var(--xl-color-primary) 18%,
    var(--xl-color-primary) 62%,
    transparent
  );
  opacity: 0.75;
}

.auth__beam--r {
  transform: translateY(8px);
}

.auth__star {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  color: var(--xl-color-ai);
  font-size: 18px;
}

.auth__logo {
  transform: scale(1.1);
}

.auth__title {
  margin: var(--xl-space-3) 0 0;
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-h1);
  font-weight: var(--xl-fs-h1-w);
  line-height: var(--xl-fs-h1-lh);
  letter-spacing: var(--xl-fs-h1-track);
}

.auth__subtitle {
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
}

/* ===== 表单区 ===== */
.auth__panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--xl-space-8) var(--xl-space-6);
  background: var(--xl-bg-surface);
}

.auth__card {
  width: 100%;
  max-width: 420px;
  padding: var(--xl-space-6) 0;
}

.auth__tabs :deep(.el-tabs__header) {
  margin-bottom: var(--xl-space-6);
}

.auth__tabs :deep(.el-tabs__item) {
  font-size: var(--xl-fs-body);
}

.auth__error {
  margin-bottom: var(--xl-space-4);
}

.auth__submit {
  width: 100%;
}

@media (width <= 800px) {
  .auth {
    grid-template-columns: 1fr;
  }

  .auth__brand {
    min-height: 260px;
  }
}
</style>
