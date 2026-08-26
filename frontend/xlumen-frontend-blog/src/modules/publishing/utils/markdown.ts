// publishing 模块：Markdown 渲染（B02，PRODUCT §10 要求渲染必须执行 XSS 清洗）。
// markdown-it 负责语法转换，highlight.js 做围栏代码块高亮，DOMPurify 清洗输出 HTML；
// 依赖随路由级拆包按需下载（FRONTEND.md §13，本模块被详情页/编辑器预览/问答消息复用）。
import DOMPurify from 'dompurify'
import hljs from 'highlight.js'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({
  html: false, // 原文禁止内嵌 HTML，降低 XSS 面
  linkify: true,
  breaks: true,
  highlight: highlightCode,
})

/** 围栏代码块高亮：语言可识别时用 hljs 上色，否则转义后按纯文本输出。 */
function highlightCode(code: string, lang: string): string {
  const language = lang.trim()
  if (language && hljs.getLanguage(language)) {
    try {
      const highlighted = hljs.highlight(code, { language }).value
      return `<pre><code class="hljs language-${md.utils.escapeHtml(language)}">${highlighted}</code></pre>`
    } catch {
      // 高亮抛错（罕见）时回退纯文本，保证渲染不中断
    }
  }
  return `<pre><code class="hljs">${md.utils.escapeHtml(code)}</code></pre>`
}

// 渲染时为 h2~h4 附加 id（标题文本），供目录导航锚点定位（与 extractToc 同源）。
const originalHeadingOpen =
  md.renderer.rules.heading_open ??
  ((tokens, idx, _options, _env, self) => self.renderToken(tokens, idx, _options))

md.renderer.rules.heading_open = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const inline = tokens[idx + 1]
  const text = inline?.children
    ?.filter((child) => child.type === 'text' || child.type === 'code_inline')
    .map((child) => child.content)
    .join('')
  if (token && text) {
    token.attrSet('id', text)
  }
  return originalHeadingOpen(tokens, idx, options, env, self)
}

/** 渲染 Markdown 为安全 HTML（先渲染后清洗，双保险）。 */
export function renderMarkdown(source: string): string {
  return DOMPurify.sanitize(md.render(source))
}

/** 目录条目：标题级别 + 锚点。 */
export interface TocItem {
  level: number
  text: string
  anchor: string
}

/**
 * 提取 Markdown 标题生成目录（B02 目录导航）：h2~h4，锚点与渲染出的标题 id 一致。
 */
export function extractToc(source: string): TocItem[] {
  const items: TocItem[] = []
  const lines = source.split(/\r?\n/)
  for (const line of lines) {
    const trimmed = line.trim()
    const match = /^(#{2,4})\s+(.+)$/.exec(trimmed)
    if (!match) continue
    const level = match[1]?.length ?? 2
    const text = match[2]?.trim() ?? ''
    if (!text) continue
    items.push({ level, text, anchor: text })
  }
  return items
}
