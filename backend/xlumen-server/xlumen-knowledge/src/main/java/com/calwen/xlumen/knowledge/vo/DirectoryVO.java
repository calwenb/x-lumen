package com.calwen.xlumen.knowledge.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 目录树视图（F-0309）：parent_id 多级平铺返回，子目录挂 children；
 * 列表按名称排序（数据库排序规则，不设拼音列）。
 *
 * @author calwen
 * @date 2026/8/14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectoryVO {

    /** 目录 ID（雪花 ID，字符串传输）。 */
    private Long id;

    /** 所属知识库 ID。 */
    private Long kbId;

    /** 父目录 ID（0=根目录）。 */
    private Long parentId;

    /** 目录名称。 */
    private String name;

    /** 目录内知识数（统计口径：未删除知识）。 */
    private Long knowledgeCount;

    /** 子目录（按名称排序）。 */
    private List<DirectoryVO> children;
}
