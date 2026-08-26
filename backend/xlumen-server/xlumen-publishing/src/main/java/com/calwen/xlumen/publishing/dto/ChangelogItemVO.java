package com.calwen.xlumen.publishing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 更新日志条目视图。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangelogItemVO {

    private Long id;

    private String title;

    private String content;

    private Boolean published;

    private LocalDateTime publishedAt;

    private LocalDateTime updatedAt;
}