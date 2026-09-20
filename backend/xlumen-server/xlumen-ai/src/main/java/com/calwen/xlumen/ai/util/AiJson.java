package com.calwen.xlumen.ai.util;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * AI 输出 JSON 提取工具（A 批去重）：统一处理模型输出的代码围栏剥离与 JSON 对象/数组提取，
 * WritingExecutor/ReviewExecutor/EnhanceServiceImpl 共用，避免三份近似实现。
 *
 * @author calwen
 * @date 2026/8/25
 */
public final class AiJson {

    private AiJson() {
    }

    /** 剥离 Markdown 代码围栏（``` 语言行与尾部 ```），并 trim。 */
    private static String stripFence(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.startsWith("```")) {
            int idx = s.indexOf('\n');
            s = idx >= 0 ? s.substring(idx + 1) : s;
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3);
            }
            s = s.trim();
        }
        return s;
    }

    /** 提取 JSON 对象：剥离围栏后截取首尾花括号解析；解析失败返回 null。 */
    public static JSONObject extractObject(String raw) {
        String s = stripFence(raw);
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            s = s.substring(start, end + 1);
        }
        try {
            return JSONUtil.parseObj(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** 提取 JSON 数组文本：剥离围栏后截取首尾方括号；无括号返回处理后的整段文本。 */
    public static String extractArrayText(String raw) {
        String s = stripFence(raw);
        int start = s.indexOf('[');
        int end = s.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        return s;
    }

    /** 提取并解析 JSON 数组；无数组或解析失败返回 null。 */
    public static JSONArray extractArray(String raw) {
        try {
            return JSONUtil.parseArray(extractArrayText(raw));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 宽容提取 JSON 数组：先按 {@link #extractArray} 严格解析；整段因模型输出被 maxTokens 截断而无法解析时，
     * 按字符串无关的括号平衡扫描，截取到最后一个完整的顶层元素（末尾补 {@code ]}）再解析，
     * 尽量抢救出截断点之前已经完整的对象。完全无数组或连一个完整元素都没有时返回 null。
     * 既有 {@link #extractArray} 语义不变，本方法只在需要容错调用时使用。
     */
    public static JSONArray extractArrayLenient(String raw) {
        JSONArray strict = extractArray(raw);
        if (strict != null) {
            return strict;
        }
        String s = extractArrayText(raw);
        int open = s.indexOf('[');
        if (open < 0) {
            return null;
        }
        int lastComplete = lastCompleteElementEnd(s, open);
        if (lastComplete < 0) {
            return null;
        }
        try {
            return JSONUtil.parseArray(s.substring(open, lastComplete + 1) + "]");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从数组起始括号开始扫描，返回最后一个完整顶层元素的结束下标（其后的 {@code }}）；无完整元素返回 -1。
     * 扫描感知 JSON 字符串与转义，不受元素内文本的括号干扰。
     */
    private static int lastCompleteElementEnd(String s, int open) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        int lastComplete = -1;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (c == '}' && depth == 1) {
                    // depth 归 1 = 该顶层元素闭合（数组括号本身占 1 层）
                    lastComplete = i;
                }
            }
        }
        return lastComplete;
    }
}
