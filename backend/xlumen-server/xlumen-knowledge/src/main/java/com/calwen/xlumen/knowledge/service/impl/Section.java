package com.calwen.xlumen.knowledge.service.impl;

/**
 * 内部段落模型：标题锚点 + 段落正文（按标题边界分段的知识切片中间态）。
 *
 * @author calwen
 * @date 2026/8/14
 */
record Section(String heading, String body) {
}
