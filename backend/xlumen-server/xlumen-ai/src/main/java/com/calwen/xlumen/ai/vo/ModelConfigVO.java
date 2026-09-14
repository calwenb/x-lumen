package com.calwen.xlumen.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 场景模型配置视图：列表展示，永不返回密钥。
 * 同时给出「覆盖配置」（ai_scene_config 行，可为空）与「生效配置」（运行时真正使用的值）
 * 及其来源，避免管理面展示值与实际调用值脱节。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigVO {

    /** 生效来源：数据库存在覆盖行。 */
    public static final String SOURCE_DB = "DB";

    /** 生效来源：无数据库行，使用环境配置文件（profile YAML）的场景默认。 */
    public static final String SOURCE_ENV = "ENV";

    /** 场景（AiScene 名）。 */
    private String scene;

    /** 覆盖供应商（无数据库行时为 null）。 */
    private String provider;

    /** 覆盖模型（无数据库行时为 null）。 */
    private String model;

    /** 场景参数（JSON 文本，可空）。 */
    private String paramsJson;

    /** 场景提示词（可空；WRITING 为多槽位 JSON）。 */
    private String prompt;

    /** 每日调用配额（0=不限）。 */
    private Integer dailyQuota;

    /** 覆盖行更新时间（无数据库行时为 null）。 */
    private LocalDateTime updatedAt;

    /** 运行时生效的供应商（数据库覆盖优先，否则环境默认）。 */
    private String effectiveProvider;

    /** 运行时生效的模型（数据库覆盖优先，否则环境默认）。 */
    private String effectiveModel;

    /** 生效来源：{@link #SOURCE_DB} 数据库覆盖 / {@link #SOURCE_ENV} 环境默认。 */
    private String source;
}
