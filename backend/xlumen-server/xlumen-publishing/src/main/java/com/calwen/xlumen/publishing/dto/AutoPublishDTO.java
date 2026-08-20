package com.calwen.xlumen.publishing.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 自动 AI 审核通过后的发布参数；publishAt 为空表示立即发布。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutoPublishDTO {

    private LocalDateTime publishAt;
}
