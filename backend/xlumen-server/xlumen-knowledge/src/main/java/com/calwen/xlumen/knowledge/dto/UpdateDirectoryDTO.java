package com.calwen.xlumen.knowledge.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新目录入参（F-0309）：仅名称可改（父目录变更 V2 提供）。
 *
 * @author calwen
 * @date 2026/8/14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDirectoryDTO {

    /** 目录名称（1~64 字）。 */
    @Size(max = 64, message = "目录名称不能超过 64 字")
    private String name;
}
