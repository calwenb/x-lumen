package com.calwen.xlumen.publishing.dto;

import com.calwen.xlumen.knowledge.vo.RecycleBinItemVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 回收站分页视图（F-0305，KB-3 publishing 聚合层）：kb 与 knowledge 双类型条目合并分页。
 *
 * @author calwen
 * @date 2026/8/14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecycleBinPageVO {

    /** 总条数。 */
    private long total;

    /** 页码。 */
    private long pageNo;

    /** 每页条数。 */
    private long pageSize;

    /** 回收站条目列表。 */
    private List<RecycleBinItemVO> records;
}
