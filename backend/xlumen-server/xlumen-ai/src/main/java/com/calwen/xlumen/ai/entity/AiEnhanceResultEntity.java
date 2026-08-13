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
 * AI 增值结果实体（ai_enhance_result，F-0801/F-0802）：摘要/SEO 结构化结果。
 * result_json 为 JSON 列，经 JacksonTypeHandler 映射 String。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Getter
@Setter
@TableName(value = "ai_enhance_result", autoResultMap = true)
public class AiEnhanceResultEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 文章 ID（可空，供独立增强）。 */
    private Long articleId;

    /** 场景：SUMMARY|SEO。 */
    private String scene;

    /** 结构化结果（JSON 文本）。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private String resultJson;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
