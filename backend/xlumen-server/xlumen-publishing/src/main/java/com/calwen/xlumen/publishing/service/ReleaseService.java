package com.calwen.xlumen.publishing.service;

import com.calwen.xlumen.common.dto.PageQueryDTO;
import com.calwen.xlumen.publishing.dto.CreateReleaseDTO;
import com.calwen.xlumen.publishing.dto.PageResult;
import com.calwen.xlumen.publishing.vo.ReleaseVO;

/**
 * 发布服务（F-0904/F-0905）：创建发布记录并立即/定时发布，状态流转规则集中本服务。
 *
 * @author calwen
 * @date 2026/8/13
 */
public interface ReleaseService {

    /**
     * 创建发布（F-0904）：文章须 APPROVED(4)；立即发布（publishAt 空）直接执行，否则留待定时任务。
     *
     * @param dto 发布入参
     * @return 发布记录视图
     */
    ReleaseVO release(CreateReleaseDTO dto);

    /**
     * 分页查询当前空间的发布记录。
     *
     * @param query 分页参数（默认值即接口默认值）
     * @return 发布记录分页
     */
    PageResult<ReleaseVO> listReleases(PageQueryDTO query);

    /**
     * 定时发布扫描（F-0905）：扫描 PENDING 且 publish_at<=now 的记录并幂等执行，由 PublishJob 每分钟调用。
     */
    void publishDue();

    /**
     * 下架知识（F-0906，BUG-016 补全）：仅已发布（6）可下架，乐观锁版本冲突 409；
     * 迁移 UNPUBLISHED(8) 并出索引、失效热点缓存、写审计。与 V2 F-1105 回滚发布对接。
     *
     * @param knowledgeId 知识 ID
     */
    void unpublish(Long knowledgeId);
}
