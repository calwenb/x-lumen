package com.calwen.xlumen.publishing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 更新日志分页视图。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangelogPageVO {

    private List<ChangelogItemVO> records;

    private long total;
}