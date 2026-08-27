<script setup lang="ts">
// 品牌 Logo（V2 设计系统，FRONTEND.md §10.1）：双纵向 Indigo 光柱 + 中心四角星 + 负空间 X + Ink wordmark。
// 仅使用 Indigo（品牌规范：Logo 本体不使用渐变/描边/拉伸/外框/容器）。
// 与博客端 frontend/xlumen-frontend-blog/src/components/XlLogo.vue 保持同步，勿单侧修改。
// 提供 three forms：icon（纯图标）/ full（图标 + 字标）；reverse 用于深色底（反白字标）。
withDefaults(
  defineProps<{
    /** 渲染形态：icon 仅图标，full 图标+字标 */
    variant?: 'icon' | 'full'
    /** 深色底反白：字标变浅色（后台深色侧栏/登录深色品牌面用） */
    reverse?: boolean
    /** 图标高度（px），full 时字标随之缩放 */
    size?: number
    /** 字标文本 */
    wordmark?: string
  }>(),
  {
    variant: 'full',
    reverse: false,
    size: 28,
    wordmark: 'xLumen',
  },
)
</script>

<template>
  <span
    class="xl-logo"
    :class="[`xl-logo--${variant}`, { 'xl-logo--reverse': reverse }]"
    :style="{ '--xl-logo-h': `${size}px` }"
    role="img"
    :aria-label="wordmark"
  >
    <svg
      class="xl-logo__mark"
      :width="size"
      :height="size * 1.1"
      viewBox="0 0 40 44"
      fill="none"
      aria-hidden="true"
    >
      <!-- 左光柱 -->
      <path :fill="reverse ? '#fff' : 'var(--xl-color-primary)'" d="M9 4 L15 8 L12 40 L6 36 Z" />
      <!-- 右光柱 -->
      <path :fill="reverse ? '#fff' : 'var(--xl-color-primary)'" d="M25 8 L31 4 L34 36 L28 40 Z" />
      <!-- 中心四角星（隐藏 X / AI 小光） -->
      <path
        :fill="reverse ? 'var(--xl-color-primary)' : '#fff'"
        d="M20 9 Q20 16 24 16 Q20 16 20 23 Q20 16 16 16 Q20 16 20 9 Z"
      />
    </svg>
    <span v-if="variant === 'full'" class="xl-logo__word">{{ wordmark }}</span>
  </span>
</template>

<style scoped>
.xl-logo {
  display: inline-flex;
  align-items: center;
  gap: 0.4em;
  line-height: 1;
  color: var(--xl-text-primary);
  user-select: none;
}

.xl-logo__mark {
  display: block;
  flex-shrink: 0;
}

.xl-logo__word {
  font-family: var(--xl-font-sans);
  font-weight: 650;
  letter-spacing: -0.03em;
  /* 字标随图标高度等比缩放 */
  font-size: calc(var(--xl-logo-h) * 0.62);
}

/* 深色底反白字标 */
.xl-logo--reverse .xl-logo__word {
  color: #fff;
}
</style>
