package com.calwen.xlumen.knowledge.service;

import com.calwen.xlumen.common.dto.PageQueryDTO;
import com.calwen.xlumen.knowledge.dto.PageResult;
import com.calwen.xlumen.knowledge.vo.RecycleBinItemVO;

/**
 * 回收站服务（F-0305）：知识库与知识统一回收站，type=kb|knowledge，deleted_at 降序，
 * 超期（默认 30 天）自动清理由调度任务按 deleted_at 扫描（不扩 8 状态机）。
 *
 * @author calwen
 * @date 2026/8/14
 */
public interface RecycleBinService {

    /**
     * 回收站分页列表（F-0305）：type=kb 查 kb_knowledge_base（status=1）；
     * type=knowledge 的条目数据在 cnt_knowledge（content 模块）——knowledge 模块依赖方向受限无法直查，
     * 暂返回空页，由 KB-3 content 改造实现 ContentApi.listRecycledKnowledge 后接入；空=全部（当前仅库）。
     *
     * @param type  类型（kb/knowledge/空=全部）
     * @param query 分页参数（pageNo 默认 1，pageSize 默认 20 上限 100）
     * @return 回收站条目分页
     */
    PageResult<RecycleBinItemVO> list(String type, PageQueryDTO query);

    /**
     * 恢复（F-0305）：kb 恢复 status=0 + deleted_at=null，并发布 KbRecycleStatusEvent(status=0)
     * 供 content 侧连带恢复库内知识（方案 §7.2 整体恢复）；幂等（已恢复正常库直接成功）。
     * knowledge 恢复（含冲突判定：原目录已删→挂库根，原库已彻底删除→409「原知识库不存在，无法恢复」）
     * 需 cnt_knowledge 数据，由 KB-3 content 改造经 ContentApi.getRecycledKnowledge/restoreKnowledge 接入。
     *
     * @param type 类型（kb/knowledge）
     * @param id   条目 ID
     */
    void restore(String type, Long id);

    /**
     * 彻底删除（F-0305 回收站清空）：二次确认参数 confirm 必须等于 "CONFIRM"（否则 409）。
     * kb 物理删除并发布 KbPurgedEvent 供 content 侧物理级联删知识（方案 §7.2）与索引清理；
     * knowledge 物理删除由 KB-3 content 改造经 ContentApi.purgeKnowledge 接入（索引清理 KnowledgeApi.removeKnowledge）。
     *
     * @param type    类型（kb/knowledge）
     * @param id      条目 ID
     * @param confirm 二次确认参数（固定值 CONFIRM）
     */
    void purge(String type, Long id, String confirm);
}
