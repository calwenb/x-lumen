import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from './App.vue'
import router from './router'
import './styles/tokens.css'
import './styles/element-theme.css'
import './styles/index.css'

// 博客前台（含创作中心）应用入口：Pinia 全局会话 + Vue Router + Element Plus（FRONTEND.md §5/§7）
const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus)

app.mount('#app')
