import { describe, expect, it } from 'vitest'

import { renderMarkdown } from '@/modules/publishing/utils/markdown'

// Markdown 渲染测试：highlight.js 高亮输出、未知语言回退、DOMPurify 清洗保持不变
describe('renderMarkdown', () => {
  it('可识别语言代码块输出 hljs 高亮标记', () => {
    const html = renderMarkdown('```js\nconst a = 1\n```')
    expect(html).toContain('<pre><code class="hljs language-js">')
    expect(html).toContain('<span class="hljs-keyword">const</span>')
    expect(html).toContain('<span class="hljs-number">1</span>')
  })

  it('未知语言代码块回退纯文本且转义特殊字符', () => {
    const html = renderMarkdown('```nlag\n<script>alert(1)</script>\n```')
    expect(html).toContain('<pre><code class="hljs">')
    // 代码文本需要被转义，不能出现原始 script 标签
    expect(html).toContain('&lt;script&gt;')
    expect(html).not.toContain('<script>')
  })

  it('原文内嵌 HTML 依旧被消毒清洗', () => {
    const html = renderMarkdown('<script>alert(1)</script>')
    expect(html).not.toContain('<script>')
  })
})
