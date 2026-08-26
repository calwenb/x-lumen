package com.calwen.xlumen.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 调用追踪实体（ai_call_log）：每次 LLM 调用的模型/Prompt 版本/Token/费用/耗时/成败/降级。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Getter
@Setter
@TableName("ai_call_log")
public class AiCallLogEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 发起用户 ID（访客为空）。 */
    private Long userId;

    /** 场景（AiScene 名）。 */
    private String scene;

    /** 关联任务 ID（异步任务路径，可空）。 */
    private Long taskId;

    /** 调用形态：CHAT/CHAT_STREAM/CHAT_TOOLS/CHAT_STREAM_TOOLS。 */
    private String requestType;

    /** 供应商（BAILIAN/DEEPSEEK/空=脚本模型）。 */
    private String provider;

    /** 模型名。 */
    private String model;

    /** 生效 System Prompt 的 SHA1（前 12 位，版本溯源）。 */
    private String promptHash;

    /** 输入 Token 数。 */
    private Integer tokensIn;

    /** 输出 Token 数。 */
    private Integer tokensOut;

    /** 费用估算（元）。 */
    private BigDecimal estCost;

    /** 是否成功。 */
    private Boolean success;

    /** 是否降级（脚本模型/无密钥回退）。 */
    private Boolean degraded;

    /** 耗时（毫秒）。 */
    private Integer latencyMs;

    /** 失败原因（脱敏截断）。 */
    private String errorMsg;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}