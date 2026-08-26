import { onMounted, ref } from 'vue'

const THEME_KEY = 'xlumen.theme'

const isDark = ref(false)

function apply(theme: 'light' | 'dark'): void {
  isDark.value = theme === 'dark'
  document.documentElement.classList.toggle('dark', isDark.value)
}

function initTheme(): void {
  const saved = localStorage.getItem(THEME_KEY)
  const dark =
    saved === 'dark' ||
    (saved !== 'light' && window.matchMedia('(prefers-color-scheme: dark)').matches)
  apply(dark ? 'dark' : 'light')
}

export function useTheme() {
  onMounted(initTheme)
  const toggleTheme = (): void => {
    apply(isDark.value ? 'light' : 'dark')
    localStorage.setItem(THEME_KEY, isDark.value ? 'dark' : 'light')
  }
  return { isDark, toggleTheme }
}