<script setup lang="ts">
// 创作工作台（B09）：内容创作链路聚合入口（知识管理/写作/审核/发布）。
// 依赖各里程碑逐个接入：M04 知识管理；AI 写作随 M07、审核随 M10、发布随 M10 接入路由后启用。
// 恢复「审核中心」入口/studio/review → ReviewCenterPage，B12）。
import { RouterLink } from 'vue-router'
import { DocumentChecked, EditPen, Promotion, Stamp } from '@element-plus/icons-vue'

/** 工作台入口定义：路由可用则展示。 */
const entries = [
  {
    title: '知识管理',
    description: '新建、编辑、自动保存草稿，管理公开/私有可见性',
    to: { name: 'knowledge-list' },
    icon: DocumentChecked,
    enabled: true,
  },
  {
    title: 'AI 写作',
    description: '「小光」输入主题或草稿直接输出完整知识，发布前自动审校',
    to: { name: 'writing' },
    icon: EditPen,
    enabled: true,
  },
  {
    title: '审核中心',
    description:
      '查看人工/AI 审核中与已完成项：通过、驳回、唤起审校意见，AI 审核完成有站内消息提醒',
    to: { name: 'review-center' },
    icon: Stamp,
    enabled: true,
  },
  {
    title: '发布管理',
    description: '立即/定时发布，发布幂等防重复，发布成功自动建立 RAG 索引',
    to: { name: 'release-list' },
    icon: Promotion,
    enabled: true,
  },
] as const
</script>

<template>
  <main class="workbench">
    <header class="workbench__header">
      <h1 class="workbench__title">创作工作台</h1>
      <p class="workbench__intro">
        从这里开始内容创作：写知识 → 发布前自动 AI 审校 → 发布 → 自动索引，形成完整闭环。
      </p>
    </header>

    <div class="workbench__path">
      <svg
        class="workbench__rail"
        viewBox="0 0 1200 760"
        fill="none"
        preserveAspectRatio="none"
        aria-hidden="true"
      >
        <path
          d="M300 150 C 620 60, 760 210, 1040 260"
          stroke="var(--xl-color-primary)"
          stroke-width="1.5"
          stroke-dasharray="2 8"
          stroke-linecap="round"
        />
        <path
          d="M1040 300 C 1040 480, 980 520, 930 620"
          stroke="var(--xl-color-primary)"
          stroke-width="1.5"
          stroke-dasharray="2 8"
          stroke-linecap="round"
        />
        <path
          d="M300 420 C 300 430, 320 470, 330 600"
          stroke="var(--xl-color-primary)"
          stroke-width="1.5"
          stroke-dasharray="2 8"
          stroke-linecap="round"
        />
      </svg>

      <!-- 01 知识管理（面积最大，作为人工创作起点） -->
      <article class="workbench__entry workbench__entry--01">
        <span class="workbench__num" aria-hidden="true">01</span>
        <RouterLink v-if="entries[0].enabled" class="workbench__entry-link" :to="entries[0].to">
          <div class="workbench__entry-top">
            <div class="workbench__icon workbench__icon--lg" aria-hidden="true">
              <el-icon><component :is="entries[0].icon" /></el-icon>
            </div>
            <div class="workbench__entry-head">
              <h2 class="workbench__entry-title">{{ entries[0].title }}</h2>
              <p class="workbench__entry-desc">{{ entries[0].description }}</p>
            </div>
            <span class="workbench__arrow" aria-hidden="true">›</span>
          </div>
        </RouterLink>
        <div class="workbench__chips">
          <span class="workbench__chip">新建知识</span>
          <span class="workbench__chip">草稿箱</span>
          <span class="workbench__chip">我的知识</span>
          <span class="workbench__chip">可见性设置</span>
        </div>
        <div class="workbench__quick">
          <span class="workbench__quick-label">快速入口</span>
          <span class="workbench__chip">产品文档</span>
          <span class="workbench__chip">技术指南</span>
          <span class="workbench__chip">最佳实践</span>
          <span class="workbench__chip">模板中心</span>
        </div>
      </article>

      <!-- 02 AI 写作（AI Teal 星重点标记） -->
      <article class="workbench__entry workbench__entry--02 workbench__entry--ai">
        <span class="workbench__num" aria-hidden="true">02</span>
        <RouterLink v-if="entries[1].enabled" class="workbench__entry-link" :to="entries[1].to">
          <div class="workbench__entry-top">
            <div class="workbench__star workbench__icon--lg" aria-hidden="true">
              <svg
                viewBox="0 0 24 24"
                width="1em"
                height="1em"
                fill="currentColor"
                xmlns="http://www.w3.org/2000/svg"
              >
                <path
                  d="M9.94 15.5a2 2 0 0 0-1.44-1.44l-6.13-1.58a.5.5 0 0 1 0-.96l6.13-1.58A2 2 0 0 0 9.94 8.5l1.58-6.13a.5.5 0 0 1 .96 0l1.58 6.13a2 2 0 0 0 1.44 1.44l6.13 1.58a.5.5 0 0 1 0 .96l-6.13 1.58a2 2 0 0 0-1.44 1.44l-1.58 6.13a.5.5 0 0 1-.96 0z"
                />
              </svg>
            </div>
            <div class="workbench__entry-head">
              <h2 class="workbench__entry-title">{{ entries[1].title }}</h2>
              <p class="workbench__entry-desc">小光根据主题或草稿生成完整知识</p>
            </div>
            <span class="workbench__arrow" aria-hidden="true">›</span>
          </div>
        </RouterLink>
        <div class="workbench__chips">
          <span class="workbench__chip">主题生成</span>
          <span class="workbench__chip">草稿续写</span>
          <span class="workbench__chip">内容润色</span>
        </div>
      </article>

      <!-- 03 审核中心 -->
      <article class="workbench__entry workbench__entry--03">
        <span class="workbench__num" aria-hidden="true">03</span>
        <RouterLink v-if="entries[2].enabled" class="workbench__entry-link" :to="entries[2].to">
          <div class="workbench__entry-top">
            <div class="workbench__icon" aria-hidden="true">
              <el-icon><component :is="entries[2].icon" /></el-icon>
            </div>
            <div class="workbench__entry-head">
              <h2 class="workbench__entry-title">{{ entries[2].title }}</h2>
              <p class="workbench__entry-desc">{{ entries[2].description }}</p>
            </div>
            <span class="workbench__arrow" aria-hidden="true">›</span>
          </div>
        </RouterLink>
        <div class="workbench__chips">
          <span class="workbench__chip">AI 审校</span>
          <span class="workbench__chip">人工审核</span>
          <span class="workbench__chip">审核记录</span>
        </div>
      </article>

      <!-- 04 发布管理 -->
      <article class="workbench__entry workbench__entry--04">
        <span class="workbench__num" aria-hidden="true">04</span>
        <RouterLink v-if="entries[3].enabled" class="workbench__entry-link" :to="entries[3].to">
          <div class="workbench__entry-top">
            <div class="workbench__icon" aria-hidden="true">
              <el-icon><component :is="entries[3].icon" /></el-icon>
            </div>
            <div class="workbench__entry-head">
              <h2 class="workbench__entry-title">{{ entries[3].title }}</h2>
              <p class="workbench__entry-desc">{{ entries[3].description }}</p>
            </div>
            <span class="workbench__arrow" aria-hidden="true">›</span>
          </div>
        </RouterLink>
        <div class="workbench__chips workbench__chips--after">
          <span class="workbench__chip workbench__chip--muted">发布后自动</span>
          <span class="workbench__chip">建立 RAG 索引</span>
          <span class="workbench__chip">更新知识图谱</span>
          <span class="workbench__chip">对外可搜索</span>
        </div>
      </article>
    </div>
  </main>
</template>

<style scoped>
.workbench {
  max-width: var(--xl-container);
  margin: 0 auto;
  padding: 40px var(--xl-content-pad) 64px;
}

.workbench__header {
  margin-bottom: 40px;
}

.workbench__title {
  margin: 0;
  font-size: var(--xl-fs-h1);
  font-weight: var(--xl-fs-h1-w);
  line-height: var(--xl-fs-h1-lh);
  letter-spacing: var(--xl-fs-h1-track);
  color: var(--xl-text-primary);
}

.workbench__intro {
  margin: var(--xl-space-3) 0 0;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
  line-height: var(--xl-fs-body-lh);
}

/* 创作路径：细光轨 + 四段入口，从左上走向右下 */
.workbench__path {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1.18fr) minmax(0, 1fr);
  gap: var(--xl-space-8);
  padding-top: var(--xl-space-6);
}

.workbench__rail {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
  pointer-events: none;
  opacity: 0.75;
}

.workbench__entry {
  position: relative;
  z-index: 1;
  padding: 22px 22px 18px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
  transition:
    border-color var(--xl-transition),
    box-shadow var(--xl-transition),
    transform var(--xl-transition);
}

.workbench__entry:hover {
  border-color: color-mix(in srgb, var(--xl-color-primary) 40%, var(--xl-border));
  box-shadow: var(--xl-shadow-md);
  transform: translateY(-2px);
}

.workbench__entry--01 {
  grid-area: 1 / 1;
}

.workbench__entry--02 {
  grid-area: 1 / 2;
  margin-top: var(--xl-space-8);
}

.workbench__entry--03 {
  grid-area: 2 / 1;
}

.workbench__entry--04 {
  grid-area: 2 / 2;
  margin-top: var(--xl-space-6);
}

.workbench__entry--ai {
  border-color: color-mix(in srgb, var(--xl-color-ai) 34%, var(--xl-border));
}

.workbench__num {
  position: absolute;
  top: -14px;
  left: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--xl-color-primary);
  color: #fff;
  font-size: var(--xl-fs-caption);
  font-weight: 700;
  box-shadow: var(--xl-shadow-sm);
}

.workbench__entry--ai .workbench__num {
  background: var(--xl-color-ai);
}

.workbench__entry-link {
  display: block;
  color: inherit;
  text-decoration: none;
}

.workbench__entry-link:hover {
  color: inherit;
}

.workbench__entry-link:hover .workbench__entry-title {
  color: var(--xl-color-primary);
}

.workbench__entry--ai .workbench__entry-link:hover .workbench__entry-title {
  color: var(--xl-color-ai);
}

.workbench__entry-top {
  display: flex;
  align-items: flex-start;
  gap: var(--xl-space-4);
}

.workbench__icon,
.workbench__star {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: var(--xl-radius);
  background: color-mix(in srgb, var(--xl-color-primary) 10%, transparent);
  color: var(--xl-color-primary);
  font-size: 22px;
}

.workbench__icon--lg {
  width: 52px;
  height: 52px;
  font-size: 28px;
  border-radius: var(--xl-radius-card);
}

.workbench__star {
  background: color-mix(in srgb, var(--xl-color-ai) 12%, transparent);
  color: var(--xl-color-ai);
}

.workbench__entry-head {
  min-width: 0;
}

.workbench__entry-title {
  margin: 0 0 4px;
  font-size: var(--xl-fs-title);
  font-weight: var(--xl-fs-title-w);
  color: var(--xl-text-primary);
  transition: color var(--xl-transition);
}

.workbench__entry--01 .workbench__entry-title {
  font-size: 22px;
}

.workbench__entry-desc {
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.workbench__arrow {
  margin-left: auto;
  align-self: center;
  color: var(--xl-text-muted);
  font-size: 26px;
  line-height: 1;
}

.workbench__chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--xl-space-2);
  margin-top: var(--xl-space-4);
}

.workbench__chips--after {
  margin-top: var(--xl-space-4);
  padding-top: var(--xl-space-3);
  border-top: 1px solid var(--xl-border);
}

.workbench__quick {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--xl-space-2);
  margin-top: var(--xl-space-4);
  padding-top: var(--xl-space-3);
  border-top: 1px solid var(--xl-border);
}

.workbench__quick-label {
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-caption);
  font-weight: 600;
}

.workbench__chip {
  padding: 3px 10px;
  border: 1px solid var(--xl-border);
  border-radius: 999px;
  background: var(--xl-bg-surface);
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-caption);
  line-height: 1.5;
  white-space: nowrap;
}

.workbench__chip--muted {
  color: var(--xl-text-muted);
  border-color: transparent;
}

@media (width <= 900px) {
  .workbench__path {
    grid-template-columns: 1fr;
  }

  .workbench__entry--02,
  .workbench__entry--04 {
    grid-area: auto;
    margin-top: 0;
  }

  .workbench__rail {
    display: none;
  }
}
</style>
