package com.calwen.xlumen.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 任务实体（ai_task，F-1302）：任务事实存 MySQL（决策 D6），进度存 Redis 短期状态。
 * input_json/result_json 为 JSON 列，经 JacksonTypeHandler 映射 String（各场景 Service 自行序列化）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Getter
@Setter
@TableName(value = "ai_task", autoResultMap = true)
public class AiTaskEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 发起用户 ID。 */
    private Long userId;

    /** 场景（AiScene 名）。 */
    private String scene;

    /** 状态（AiTaskStatus 名）。 */
    private String status;

    /** 任务入参快照（JSON 文本）。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private String inputJson;

    /** 任务结果（JSON 文本）。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private String resultJson;

    /** 失败原因（对外脱敏）。 */
    private String errorMsg;

    /** 已重试次数（上限 3）。 */
    private Integer retryCount;

    /** 业务幂等键（重复提交返回已有任务）。 */
    private String idempotencyKey;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
