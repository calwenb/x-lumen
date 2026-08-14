package com.calwen.xlumen.content.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 草稿自动保存入参（F-0302）：knowledgeId 为空时新建草稿，非空时按版本更新；
 * 服务端做内容幂等去重（内容未变跳过写库），前端 10s/失焦节流触发。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftSaveDTO {

    /** 知识 ID（空 = 新建草稿）。 */
    private Long knowledgeId;

    /** 标题（新建时可空，保存后前端回填 ID 与版本）。 */
    @Size(max = 200, message = "标题不能超过 200 字")
    private String title;

    /** 正文 Markdown。 */
    private String content;

    /** 分类（可空）。 */
    @Size(max = 64, message = "分类不能超过 64 字")
    private String category;

    /** 标签数组（可空）。 */
    private List<String> tags;

    /** 可见性：1 公开 0 私有。 */
    private Integer visibility;

    /** 版本号（更新已有草稿时携带，冲突 409）。 */
    private Long version;
}
