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
}
