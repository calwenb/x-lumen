<script setup lang="ts">
// 关于页（B26）：品牌杂志式 38/62 分栏阅读页。
// 左侧大留白放最终 Logo、标题与现有介绍引文；右侧以舒适行宽排布现有项目介绍与联系方式。
// 一条细 Indigo 纵线贯穿两区，局部点缀 AI Teal 四角星。仅重排已有文字，不新增业务信息。
import XlLogo from '@/components/XlLogo.vue'
</script>

<template>
  <main class="about">
    <!-- 品牌锚列（38） -->
    <section class="about__lead">
      <XlLogo variant="full" :size="30" />
      <h1 class="about__title">关于 xLumen 博客</h1>
      <p class="about__quote">
        博主以文字沉淀思考，以 AI 放大创作效率；读者在干净、专注的阅读环境里与内容互动。
      </p>
    </section>

    <!-- 阅读正文列（62） -->
    <section class="about__body">
      <span class="about__star" aria-hidden="true">✦</span>
      <p class="about__text">
        这里是 xLumen——面向个人博主的 AI 内容创作与知识对话平台。 博主以文字沉淀思考， 以 AI
        放大创作效率；读者在干净、专注的阅读环境里与内容互动。
      </p>
      <p class="about__text">
        内容创作遵循公开透明的流程：知识发布前经过审校，发布后读者可评论、点赞、纠错，确保每篇内容都经得起推敲。
      </p>
      <h2 class="about__subtitle">联系方式</h2>
      <p class="about__text">欢迎通过评论区与博主交流，或关注后续更新的 RSS 订阅（V2 上线）。</p>
    </section>
  </main>
</template>

<style scoped>
.about {
  display: grid;
  grid-template-columns: 38% minmax(0, 1fr);
  align-items: start;
  width: min(calc(100% - 48px), 1080px);
  margin: 0 auto;
  padding: var(--xl-space-8) var(--xl-content-pad) var(--xl-space-8);
  box-sizing: border-box;
}

/* ===== 品牌锚列 ===== */
.about__lead {
  position: relative;
  padding: var(--xl-space-8) var(--xl-space-8) var(--xl-space-8) 0;
  border-right: 1px solid var(--xl-color-primary);
  text-align: left;
}

.about__title {
  margin: var(--xl-space-6) 0 var(--xl-space-4);
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-h1);
  font-weight: var(--xl-fs-h1-w);
  line-height: var(--xl-fs-h1-lh);
  letter-spacing: var(--xl-fs-h1-track);
}

.about__quote {
  margin: 0;
  padding-top: var(--xl-space-4);
  border-top: 1px solid var(--xl-border);
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-title);
  font-weight: var(--xl-fs-title-w);
  line-height: 1.6;
}

/* ===== 阅读正文列 ===== */
.about__body {
  position: relative;
  max-width: 640px;
  padding-left: var(--xl-space-8);
  font-size: var(--xl-fs-body);
  line-height: 1.9;
}

.about__star {
  position: absolute;
  top: 8px;
  left: calc(var(--xl-space-8) - 8px);
  color: var(--xl-color-ai);
  font-size: var(--xl-fs-body);
}

.about__text {
  margin: 0 0 var(--xl-space-4);
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
  line-height: 1.9;
}

.about__subtitle {
  margin: var(--xl-space-8) 0 var(--xl-space-3);
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-title);
  font-weight: var(--xl-fs-title-w);
}

@media (width <= 800px) {
  .about {
    grid-template-columns: 1fr;
  }

  .about__lead {
    border-right: none;
    border-bottom: 1px solid var(--xl-color-primary);
    padding: 0 0 var(--xl-space-6);
  }

  .about__body {
    padding: var(--xl-space-6) 0 0;
  }

  .about__star {
    left: -8px;
  }
}
</style>
