package com.calwen.xlumen.ai.service;

import com.calwen.xlumen.ai.enums.AiScene;

/**
 * 场景配额服务：预占-结算-释放。每日配额来自 ai_scene_config.daily_quota（0=不限），
 * Redis 日键计数；Redis 不可用时跳过配额（fail-open，不阻断 AI）。
 *
 * @author calwen
 * @date 2026/8/26
 */
public interface QuotaService {

    /** 预占一次调用；超配额抛 TOO_MANY_REQUESTS。 */
    void reserve(Long workspaceId, AiScene scene);

    /** 结算（保留计数，供用量统计；当前为 no-op 占位）。 */
    void settle(Long workspaceId, AiScene scene);

    /** 释放一次失败调用（回退计数）。 */
    void release(Long workspaceId, AiScene scene);
}