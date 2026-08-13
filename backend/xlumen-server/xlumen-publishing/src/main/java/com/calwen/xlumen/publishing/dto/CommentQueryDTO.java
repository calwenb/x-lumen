package com.calwen.xlumen.publishing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评论列表查询参数（F-0203）：文章 ID 走路径变量，本类只承载分页参数（默认值即接口默认值）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentQueryDTO {

    /** 页码（从 1 开始，默认 1）。 */
    @Builder.Default
    private long pageNo = 1;

    /** 每页条数（默认 20，上限 100）。 */
    @Builder.Default
    private long pageSize = 20;
}
