<script setup lang="ts">
// 管理后台首页（占位）：配置管理功能随 M13（空间/成员/角色、审计、模型配置）落地
</script>

<template>
  <main class="dashboard">
    <h1 class="dashboard__title">xLumen 管理后台</h1>
    <p class="dashboard__subtitle">仅管理员可见：空间/成员/角色、模型配置、审计日志</p>
    <p class="dashboard__hint">代码骨架（M01）已就绪，配置管理功能将在后续里程碑落地。</p>
  </main>
</template>

<style scoped>
.dashboard {
  max-width: 960px;
  margin: 0 auto;
  padding: var(--xl-space-8) var(--xl-space-4);
}

.dashboard__title {
  color: var(--xl-text-primary);
  font-size: 28px;
}

.dashboard__subtitle {
  color: var(--xl-text-secondary);
}

.dashboard__hint {
  color: var(--xl-text-muted);
}
</style>
