<script setup lang="ts">
// 博客首页（B01 文章展示页）：M01 骨架占位，文章列表随 M03（F-0201~F-0203）落地
</script>

<template>
  <main class="home">
    <h1 class="home__title">xLumen 博客</h1>
    <p class="home__subtitle">面向个人博主的 AI 内容创作与知识对话平台</p>
    <p class="home__hint">代码骨架（M01）已就绪，文章展示功能将在后续里程碑落地。</p>
  </main>
</template>

<style scoped>
.home {
  max-width: 960px;
  margin: 0 auto;
  padding: var(--xl-space-8) var(--xl-space-4);
}

.home__title {
  color: var(--xl-text-primary);
  font-size: 28px;
}

.home__subtitle {
  color: var(--xl-text-secondary);
}

.home__hint {
  color: var(--xl-text-muted);
}
</style>
