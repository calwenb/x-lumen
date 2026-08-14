package com.calwen.xlumen.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建目录入参（F-0309）：parentId 为 0 表示库根下的一级目录。
 *
 * @author calwen
 * @date 2026/8/14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDirectoryDTO {

    /** 父目录 ID（0=根目录）。 */
    private Long parentId;

    /** 目录名称（1~64 字）。 */
    @NotBlank(message = "目录名称不能为空")
    @Size(max = 64, message = "目录名称不能超过 64 字")
    private String name;
}
