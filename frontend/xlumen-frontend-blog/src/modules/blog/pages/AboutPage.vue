<script setup lang="ts">
// 关于页（B04，MVP）：博主简介、联系方式；RSS 订阅入口 V2 生效（F-0205）。
// 空间设置（博客名/简介等）随 M11（F-1201 空间设置）落地后展示动态内容。
</script>

<template>
  <main class="about">
    <section class="about__card">
      <h1 class="about__title">关于 xLumen 博客</h1>
      <p class="about__text">
        这里是 xLumen——面向个人博主的 AI 内容创作与知识对话平台。博主以文字沉淀思考， 以 AI
        放大创作效率；读者在干净、专注的阅读环境里与内容互动。
      </p>
      <p class="about__text">
        内容创作遵循公开透明的流程：知识发布前经过审校，发布后读者可评论、点赞、纠错，
        确保每篇内容都经得起推敲。
      </p>
      <h2 class="about__subtitle">联系方式</h2>
      <p class="about__text">欢迎通过评论区与博主交流，或关注后续更新的 RSS 订阅（V2 上线）。</p>
    </section>
  </main>
</template>

<style scoped>
.about {
  max-width: 720px;
  margin: 0 auto;
  padding: var(--xl-space-6) var(--xl-space-4) var(--xl-space-8);
}

.about__card {
  padding: var(--xl-space-6);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-md);
}

.about__title {
  margin: 0 0 var(--xl-space-4);
  color: var(--xl-text-primary);
  font-size: 24px;
}

.about__subtitle {
  margin: var(--xl-space-6) 0 var(--xl-space-2);
  color: var(--xl-text-primary);
  font-size: 17px;
}

.about__text {
  margin: 0 0 var(--xl-space-3);
  color: var(--xl-text-secondary);
  font-size: 14px;
  line-height: 1.8;
}
</style>
