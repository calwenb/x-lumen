package com.calwen.xlumen.publishing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新日志写入入参（标题/正文/发布状态）。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangelogDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题过长")
    private String title;

    @NotBlank(message = "内容不能为空")
    private String content;

    private Boolean published;
}