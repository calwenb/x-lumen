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
 * 场景模型配置实体（ai_scene_config，F-0502）：workspace_id + scene 唯一。
 * 仅存供应商/模型/参数；API Key 不入表（唯一来源 config/.env，决策 D8）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Getter
@Setter
@TableName(value = "ai_scene_config", autoResultMap = true)
public class AiSceneConfigEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 场景（AiScene 名）。 */
    private String scene;

    /** 供应商（BAILIAN/DEEPSEEK/MOCK）。 */
    private String provider;

    /** 模型名。 */
    private String model;

    /** 场景参数（JSON 文本，可空）。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private String paramsJson;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
