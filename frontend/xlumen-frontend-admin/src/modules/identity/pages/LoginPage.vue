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

// 管理后台准入角色：仅 OWNER/ADMIN
const ADMIN_ROLES = ['OWNER', 'ADMIN']

async function submit(): Promise<void> {
  errorMessage.value = ''
  // 临时方案：仅 calwen 账号可登录管理后台
  if (username.value.trim() !== 'calwen') {
    errorMessage.value = '该账号无权访问'
    return
  }
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
    <!-- A00 左侧：深 Ink 品牌面，静态主 Logo + 受控光柱 -->
    <section class="login__brand-panel" aria-label="xLumen 管理后台">
      <div class="login__pillars" aria-hidden="true">
        <span class="login__pillar login__pillar--left" />
        <span class="login__pillar login__pillar--right" />
      </div>
      <div class="login__brand-content">
        <div class="login__brand-logo">
          <img src="/brand/xlumen-logo-primary-generated.png" alt="xLumen" />
        </div>
        <span class="login__eyebrow">AI KNOWLEDGE PLATFORM</span>
        <h1 class="login__title">xLumen 管理后台</h1>
        <p class="login__subtitle">仅限空间所有者与管理员登录</p>
      </div>
    </section>

    <!-- A00 右侧：Paper 背景，轻量卡片表单 -->
    <section class="login__form-panel">
      <div class="login__form-wrap">
        <div class="login__form-heading">
          <span class="login__form-kicker">WELCOME BACK</span>
          <h2>登录控制台</h2>
          <p>使用管理员账号继续管理空间配置与 AI 服务。</p>
        </div>
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
  background:
    radial-gradient(circle at 28% 30%, rgb(83 103 232 / 20%), transparent 34%),
    radial-gradient(circle at 78% 72%, rgb(18 165 148 / 11%), transparent 32%),
    var(--xl-text-primary);
}

/* 两条低对比 Indigo 光柱，作为空间结构而非满屏背景块 */
.login__pillars {
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

.login__pillar {
  width: 52px;
  height: 380px;
  border: 1px solid rgb(130 147 255 / 18%);
  border-radius: 999px;
  background: linear-gradient(180deg, rgb(83 103 232 / 34%), rgb(83 103 232 / 5%));
  box-shadow: 0 0 80px rgb(83 103 232 / 18%);
}

.login__pillar--left {
  transform: translateY(-18px);
}

.login__pillar--right {
  transform: translateY(18px);
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

.login__brand-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 14px 22px;
  border: 1px solid rgb(255 255 255 / 62%);
  border-radius: 16px;
  background: rgb(255 255 255 / 96%);
  box-shadow: 0 18px 42px rgb(0 0 0 / 18%);
}

.login__brand-logo img {
  display: block;
  width: auto;
  height: 38px;
}

.login__eyebrow {
  margin-top: var(--xl-space-2);
  color: color-mix(in srgb, var(--xl-color-ai) 82%, #fff);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.login__title {
  margin: var(--xl-space-2) 0 0;
  font-size: 28px;
  font-weight: 650;
  letter-spacing: -0.01em;
}

.login__subtitle {
  margin: 0;
  color: color-mix(in srgb, #fff 62%, transparent);
  font-size: var(--xl-fs-body);
}

/* A00 右侧 56%：Paper 背景 + 约 420px 表单 */
.login__form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56%;
  padding: var(--xl-space-8) clamp(24px, 7vw, 120px);
  background:
    radial-gradient(circle at 75% 15%, rgb(83 103 232 / 7%), transparent 30%),
    var(--xl-bg-page);
  box-sizing: border-box;
}

.login__form-wrap {
  width: 100%;
  max-width: 480px;
  padding: 44px 48px 48px;
  border: 1px solid var(--xl-border);
  border-radius: 24px;
  background: var(--xl-bg-surface);
  box-shadow: 0 24px 60px rgb(22 32 51 / 8%);
  box-sizing: border-box;
}

.login__form-heading {
  margin-bottom: var(--xl-space-6);
}

.login__form-kicker {
  color: var(--xl-color-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.login__form-heading h2 {
  margin: var(--xl-space-2) 0 var(--xl-space-2);
  color: var(--xl-text-primary);
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.login__form-heading p {
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
  line-height: 1.7;
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

@media (width <= 800px) {
  .login {
    display: block;
  }

  .login__brand-panel,
  .login__form-panel {
    width: 100%;
  }

  .login__brand-panel {
    min-height: 300px;
  }

  .login__form-wrap {
    max-width: 520px;
  }
}
</style>
