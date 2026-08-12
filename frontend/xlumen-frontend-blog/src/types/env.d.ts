/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'

  // Vue 单文件组件类型声明（shim）
  const component: DefineComponent<object, object, unknown>
  export default component
}
