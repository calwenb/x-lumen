package com.calwen.xlumen.publishing.service;

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
     * @param pageNo   页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 发布记录分页
     */
    PageResult<ReleaseVO> listReleases(long pageNo, long pageSize);

    /**
     * 定时发布扫描（F-0905）：扫描 PENDING 且 publish_at<=now 的记录并幂等执行，由 PublishJob 每分钟调用。
     */
    void publishDue();
}
