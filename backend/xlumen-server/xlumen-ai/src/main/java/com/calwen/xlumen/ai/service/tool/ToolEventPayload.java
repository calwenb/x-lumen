package com.calwen.xlumen.ai.service.tool;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * 工具信封工具（OPT-2/D20 迁移：原 ToolRegistry 静态信封方法独立成类，适配层与工具共用）。
 * 成功信封 {ok:true,data}；错误信封 {ok:false,error}。
 *
 * @author calwen
 * @date 2026/8/24
 */
public final class ToolEventPayload {

    private ToolEventPayload() {
    }

    /** 成功信封。 */
    public static String okEnvelope(Object data) {
        return JSONUtil.createObj().set("ok", true).set("data", data).toString();
    }

    /** 错误信封。 */
    public static String errorEnvelope(String error) {
        return JSONUtil.createObj().set("ok", false).set("error", error).toString();
    }

    /** 从信封提取 ok 标记。 */
    public static boolean isOk(String envelope) {
        if (envelope == null) {
            return false;
        }
        try {
            JSONObject obj = JSONUtil.parseObj(envelope);
            return Boolean.TRUE.equals(obj.getBool("ok"));
        } catch (Exception e) {
            return false;
        }
    }
}