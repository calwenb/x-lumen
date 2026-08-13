package com.calwen.xlumen.publishing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评论入参（F-0203）：需登录；内容 1~1000 字符，parentId 为空表示顶级评论。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCommentDTO {

    /** 评论内容（1~1000 字符）。 */
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 1000, message = "评论内容不能超过 1000 字符")
    private String content;

    /** 回复的评论 ID（NULL 为顶级评论）。 */
    private Long parentId;
}
