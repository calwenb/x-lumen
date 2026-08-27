<script setup lang="ts">
// 404 页（B27）：访问不存在的路由时兜底，给出返回首页入口，避免空白页。
</script>

<template>
  <main class="notfound">
    <span class="notfound__code" aria-hidden="true">404</span>
    <h1 class="notfound__title">页面不存在</h1>
    <p class="notfound__text">你访问的地址不存在或已被移除，请检查地址或返回首页继续浏览。</p>
    <router-link class="notfound__home" :to="{ name: 'home' }">返回首页</router-link>
  </main>
</template>

<style scoped>
.notfound {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  padding: var(--xl-space-8) var(--xl-content-pad);
  box-sizing: border-box;
  text-align: center;
}

.notfound__code {
  color: var(--xl-color-primary);
  font-size: clamp(64px, 12vw, 120px);
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0.06em;
}

.notfound__title {
  margin: var(--xl-space-6) 0 var(--xl-space-3);
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-h1);
  font-weight: var(--xl-fs-h1-w);
  line-height: var(--xl-fs-h1-lh);
}

.notfound__text {
  max-width: 480px;
  margin: 0 0 var(--xl-space-6);
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
  line-height: 1.8;
}

.notfound__home {
  display: inline-flex;
  align-items: center;
  padding: var(--xl-space-3) var(--xl-space-6);
  border-radius: var(--xl-radius-sm);
  background: var(--xl-color-primary);
  color: #fff;
  font-size: var(--xl-fs-body);
  font-weight: 600;
  text-decoration: none;
  transition: opacity 0.2s ease;
}

.notfound__home:hover {
  opacity: 0.88;
}
</style>
