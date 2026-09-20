<script setup lang="ts">
// 全站页脚：展示网站备案号。工信部要求备案号须在网站底部展示，并链接至工信部备案管理系统。
// 备案号是站点身份标识而非环境属性，dev/test/prod 共用同一个号，故不做 VITE_ 构建期变量注入。
const icpLicense = '桂ICP备2026020597号'
const miitUrl = 'https://beian.miit.gov.cn/'
</script>

<template>
  <footer class="site-footer">
    <a class="site-footer__license" :href="miitUrl" target="_blank" rel="noopener noreferrer">
      {{ icpLicense }}
    </a>
  </footer>
</template>

<style scoped>
.site-footer {
  margin-top: auto;
  padding: var(--xl-space-6) var(--xl-content-pad);
  border-top: 1px solid var(--xl-border);
  background: var(--xl-bg-surface);
  text-align: center;
}

.site-footer__license {
  color: var(--xl-text-muted);
  font-size: var(--xl-fs-caption);
  line-height: var(--xl-fs-caption-lh);
  text-decoration: none;
  transition: color var(--xl-transition);
}

.site-footer__license:hover {
  color: var(--xl-color-primary);
}
</style>
