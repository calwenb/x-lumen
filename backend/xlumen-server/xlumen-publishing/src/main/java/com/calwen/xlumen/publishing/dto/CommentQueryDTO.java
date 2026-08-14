package com.calwen.xlumen.publishing.dto;

import com.calwen.xlumen.common.dto.PageQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 评论列表查询参数（F-0203）：文章 ID 走路径变量，分页参数继承 {@link PageQueryDTO}（默认值即接口默认值）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CommentQueryDTO extends PageQueryDTO {
}
