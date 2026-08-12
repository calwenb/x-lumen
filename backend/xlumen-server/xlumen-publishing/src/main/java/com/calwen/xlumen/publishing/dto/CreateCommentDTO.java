package com.calwen.xlumen.publishing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 评论入参（F-0203）：需登录；内容 1~1000 字符。
 *
 * @author calwen
 * @date 2026/8/12
 */
public record CreateCommentDTO(

        @NotBlank(message = "评论内容不能为空")
        @Size(max = 1000, message = "评论内容不能超过 1000 字符")
        String content,

        Long parentId
) {
}
