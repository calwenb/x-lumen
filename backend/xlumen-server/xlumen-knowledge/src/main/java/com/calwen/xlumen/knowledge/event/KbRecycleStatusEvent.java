package com.calwen.xlumen.knowledge.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库回收状态变更事件（F-0305，进程内事件）：知识库删除进回收站（status=1）与恢复（status=0）时发布。
 * 消费方：content 模块（KB-3 content 改造，content→knowledge 依赖方向无环）监听后连带软删/恢复
 * 库内知识（cnt_knowledge.recycle_status + deleted_at，方案 §7.2 删库连带进回收站），并联动索引清理；
 * publishing 侧可据此失效公开读缓存。发布方（knowledge 模块）不依赖任何消费方，事件丢失仅影响连带动作。
 *
 * @author calwen
 * @date 2026/8/14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbRecycleStatusEvent {

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 知识库 ID。 */
    private Long kbId;

    /** 回收状态：1 进回收站 / 0 恢复。 */
    private Integer status;
}
