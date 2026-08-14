package com.calwen.xlumen.knowledge.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 回收站条目视图（F-0305）：知识库与知识统一回收站，双 Tab 展示。
 * 类型 type=kb|knowledge；剩余天数由展示层按 deleted_at+30 天计算。
 *
 * @author calwen
 * @date 2026/8/14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecycleBinItemVO {

    /** 条目类型：kb 知识库 / knowledge 知识。 */
    private String type;

    /** 条目 ID（库 ID 或知识 ID）。 */
    private Long id;

    /** 名称（库名或知识标题）。 */
    private String name;

    /** 所属知识库名称（知识条目展示用，可空）。 */
    private String kbName;

    /** 进回收站时间。 */
    private LocalDateTime deletedAt;

    /** 原目录名（知识条目恢复提示用，可空）。 */
    private String directoryName;
}
