<script setup lang="ts">
// B08 登录/注册（MVP · ）：登录/注册切换；登录失败不暴露账号是否存在（后端统一提示）；
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

/** 演示账号：点击提示自动填入登录表单。 */
function fillDemo(): void {
  mode.value = 'login'
  username.value = 'demo'
  password.value = '123456'
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
    <!-- 左侧：深 Ink 品牌面，静态主 Logo + 受控光柱（与管理后台登录页同款） -->
    <section class="auth__brand-panel" aria-label="xLumen">
      <div class="auth__pillars" aria-hidden="true">
        <span class="auth__pillar auth__pillar--left" />
        <span class="auth__pillar auth__pillar--right" />
      </div>
      <div class="auth__brand-content">
        <div class="auth__brand-logo">
          <img src="/brand/xlumen-logo-primary-generated.png" alt="xLumen" />
        </div>
        <span class="auth__eyebrow">AI KNOWLEDGE PLATFORM</span>
        <h1 class="auth__title">xLumen 知识平台</h1>
        <p class="auth__subtitle">登录创作，注册即创建个人工作空间</p>
      </div>
    </section>

    <!-- 右侧：Paper 背景，轻量卡片表单 -->
    <section class="auth__form-panel">
      <div class="auth__form-wrap">
        <div class="auth__form-heading">
          <span class="auth__form-kicker">WELCOME BACK</span>
          <h2>登录 / 注册</h2>
          <p>登录后继续创作与阅读，新用户可直接注册个人空间。</p>
        </div>
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
          <p v-if="isLogin" class="auth__demo-tip">
            演示账号 demo / 123456
            <button type="button" class="auth__demo-fill" @click="fillDemo()">一键填充</button>
          </p>
          <el-alert
            v-if="errorMessage"
            :title="errorMessage"
            type="error"
            :closable="false"
            class="auth__error"
            show-icon
          />
          <el-button type="primary" native-type="submit" class="auth__submit" :loading="loading">
            {{ loading ? '处理中…' : isLogin ? '登 录' : '注 册' }}
          </el-button>
        </el-form>
      </div>
    </section>
  </main>
</template>

<style scoped>
.auth {
  display: flex;
  min-height: 100vh;
}

/* ===== 左侧 44%：深 Ink 品牌面（与管理后台登录页同款） ===== */
.auth__brand-panel {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44%;
  overflow: hidden;
  background:
    radial-gradient(circle at 28% 30%, rgb(83 103 232 / 20%), transparent 34%),
    radial-gradient(circle at 78% 72%, rgb(18 165 148 / 11%), transparent 32%),
    var(--xl-text-primary);
}

/* 两条低对比 Indigo 光柱，作为空间结构而非满屏背景块 */
.auth__pillars {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 360px;
  height: 520px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 96px;
  opacity: 0.58;
  transform: translate(-50%, -50%) rotate(-8deg);
}

.auth__pillar {
  width: 52px;
  height: 380px;
  border: 1px solid rgb(130 147 255 / 18%);
  border-radius: 999px;
  background: linear-gradient(180deg, rgb(83 103 232 / 34%), rgb(83 103 232 / 5%));
  box-shadow: 0 0 80px rgb(83 103 232 / 18%);
}

.auth__pillar--left {
  transform: translateY(-18px);
}

.auth__pillar--right {
  transform: translateY(18px);
}

.auth__brand-content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--xl-space-4);
  text-align: center;
  color: #fff;
}

.auth__brand-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 14px 22px;
  border: 1px solid rgb(255 255 255 / 62%);
  border-radius: 16px;
  background: rgb(255 255 255 / 96%);
  box-shadow: 0 18px 42px rgb(0 0 0 / 18%);
}

.auth__brand-logo img {
  display: block;
  width: auto;
  height: 38px;
}

.auth__eyebrow {
  margin-top: var(--xl-space-2);
  color: color-mix(in srgb, var(--xl-color-ai) 82%, #fff);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.auth__title {
  margin: var(--xl-space-2) 0 0;
  font-size: 28px;
  font-weight: 650;
  letter-spacing: -0.01em;
}

.auth__subtitle {
  margin: 0;
  color: color-mix(in srgb, #fff 62%, transparent);
  font-size: var(--xl-fs-body);
}

/* ===== 右侧 56%：Paper 背景 + 约 480px 卡片表单 ===== */
.auth__form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56%;
  padding: var(--xl-space-8) clamp(24px, 7vw, 120px);
  background:
    radial-gradient(circle at 75% 15%, rgb(83 103 232 / 7%), transparent 30%), var(--xl-bg-page);
  box-sizing: border-box;
}

.auth__form-wrap {
  width: 100%;
  max-width: 480px;
  padding: 44px 48px 48px;
  border: 1px solid var(--xl-border);
  border-radius: 24px;
  background: var(--xl-bg-surface);
  box-shadow: 0 24px 60px rgb(22 32 51 / 8%);
  box-sizing: border-box;
}

.auth__form-heading {
  margin-bottom: var(--xl-space-6);
}

.auth__form-kicker {
  color: var(--xl-color-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.auth__form-heading h2 {
  margin: var(--xl-space-2) 0 var(--xl-space-2);
  color: var(--xl-text-primary);
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.auth__form-heading p {
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
  line-height: 1.7;
}

.auth__tabs :deep(.el-tabs__header) {
  margin-bottom: var(--xl-space-4);
}

.auth__tabs :deep(.el-tabs__item) {
  font-size: var(--xl-fs-body);
}

.auth__demo-tip {
  margin: calc(-1 * var(--xl-space-2)) 0 var(--xl-space-4);
  color: var(--xl-text-muted);
  font-size: var(--xl-fs-caption);
}

.auth__demo-fill {
  padding: 0;
  border: none;
  background: none;
  color: var(--xl-color-primary);
  font-size: var(--xl-fs-caption);
  cursor: pointer;
}

.auth__error {
  margin-bottom: var(--xl-space-4);
}

.auth__submit {
  width: 100%;
  margin-top: var(--xl-space-2);
}

@media (width <= 800px) {
  .auth {
    display: block;
  }

  .auth__brand-panel,
  .auth__form-panel {
    width: 100%;
  }

  .auth__brand-panel {
    min-height: 300px;
  }

  .auth__form-wrap {
    max-width: 520px;
  }
}
</style>
