package com.calwen.xlumen.publishing.service;

import com.calwen.xlumen.content.api.ContentApi;
import com.calwen.xlumen.content.api.dto.ContentPageResult;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.dto.PageResult;
import com.calwen.xlumen.knowledge.vo.RecycleBinItemVO;
import com.calwen.xlumen.publishing.dto.RecycleBinPageVO;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.dto.PageQueryDTO;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 回收站聚合服务（F-0305，KB-3）：knowledge 模块依赖方向受限（content→ai→knowledge 环）无法直连
 * content，回收站统一编排收敛到 publishing（同时依赖 content+knowledge，无环）：
 * kb 侧委托 KnowledgeApi（库回收），knowledge 侧委托 ContentApi（知识回收含恢复冲突判定）。
 *
 * @author calwen
 * @date 2026/8/14
 */
@Slf4j
@Service
public class RecycleBinFacadeService {

    /** 分页上限（与模块约定一致）。 */
    private static final long MAX_PAGE_SIZE = 100;

    @Resource
    private KnowledgeApi knowledgeApi;
    @Resource
    private ContentApi contentApi;

    /**
     * 回收站分页列表：type=kb|knowledge|空=全部，deleted_at 降序。
     *
     * @param type     类型（kb/knowledge，可空=全部）
     * @param query    分页参数
     * @return 回收站条目分页（knowledge 侧条目含原库/原目录名，kb 侧含库名）
     */
    public RecycleBinPageVO list(String type, PageQueryDTO query) {
        Long workspaceId = requireWorkspace();
        String normalized = type == null ? "" : type.trim();
        if (!normalized.isEmpty() && !"kb".equals(normalized) && !"knowledge".equals(normalized)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "回收站类型参数非法（kb/knowledge）");
        }
        long pageNo = Math.max(1L, query.getPageNo());
        long pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1L, query.getPageSize()));

        // kb 侧：库回收（knowledge 模块数据）
        PageResult<RecycleBinItemVO> kbPage = knowledgeApi.listRecycledKbs(workspaceId, pageNo, pageSize);
        // knowledge 侧：知识回收（content 模块数据）
        ContentPageResult<ContentApi.RecycledKnowledgeItem> knPage =
                contentApi.listRecycledKnowledge(workspaceId, pageNo, pageSize);

        List<RecycleBinItemVO> merged = new ArrayList<>();
        if (normalized.isEmpty() || "kb".equals(normalized)) {
            merged.addAll(kbPage.getRecords());
        }
        if (normalized.isEmpty() || "knowledge".equals(normalized)) {
            knPage.getRecords().forEach(k -> merged.add(RecycleBinItemVO.builder()
                    .type("knowledge")
                    .id(k.getId())
                    .name(k.getTitle())
                    .kbName(k.getKbName())
                    .deletedAt(k.getDeletedAt())
                    .directoryName(k.getDirectoryName())
                    .build()));
        }
        // 全部视图按 deleted_at 降序合并；单类型视图各自分页
        if (normalized.isEmpty()) {
            merged.sort(Comparator.comparing(RecycleBinItemVO::getDeletedAt,
                    Comparator.nullsFirst(Comparator.reverseOrder())));
            long total = merged.size();
            int from = (int) Math.min((pageNo - 1) * pageSize, merged.size());
            int to = (int) Math.min(from + pageSize, merged.size());
            List<RecycleBinItemVO> pageRecords = merged.subList(from, to);
            return RecycleBinPageVO.builder().total(total).pageNo(pageNo).pageSize(pageSize).records(pageRecords).build();
        }
        long total = "kb".equals(normalized)
                ? kbPage.getTotal()
                : knPage.getTotal();
        return RecycleBinPageVO.builder().total(total).pageNo(pageNo).pageSize(pageSize).records(merged).build();
    }

    /**
     * 恢复（F-0305）：kb 整体恢复（连带恢复库内知识）；知识恢复含冲突判定
     * （原目录已删→挂库根；原库已彻底删除→409「原知识库不存在，无法恢复」）。
     *
     * @param type 类型（kb/knowledge）
     * @param id   条目 ID
     */
    public void restore(String type, Long id) {
        Long workspaceId = requireWorkspace();
        String normalized = type == null ? "" : type.trim();
        if ("kb".equals(normalized)) {
            knowledgeApi.restoreRecycledKb(workspaceId, id);
            return;
        }
        if ("knowledge".equals(normalized)) {
            // 恢复冲突判定：原库已彻底删除则拒绝恢复
            ContentApi.RecycledKnowledgeItem item = contentApi.getRecycledKnowledge(workspaceId, id);
            if (item == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "知识不存在或不在回收站");
            }
            if (knowledgeApi.getKnowledgeBaseById(item.getKbId()) == null) {
                throw new BizException(ErrorCode.CONFLICT, "原知识库不存在，无法恢复");
            }
            // 原目录已删除→挂库根（directory_id=0）；目录仍存在→恢复原目录
            Long targetDirectory = resolveRestoreDirectory(item);
            contentApi.restoreKnowledge(workspaceId, id, targetDirectory);
            return;
        }
        throw new BizException(ErrorCode.INVALID_PARAM, "回收站类型参数非法（kb/knowledge）");
    }

    /**
     * 彻底删除（F-0305 回收站清空）：二次确认 confirm=CONFIRM；kb 物理级联删（KbPurgedEvent
     * content 侧级联删知识 + 索引清理）；knowledge 物理删除并清理索引。
     *
     * @param type    类型（kb/knowledge）
     * @param id      条目 ID
     * @param confirm 二次确认参数（固定值 CONFIRM）
     */
    public void purge(String type, Long id, String confirm) {
        if (!"CONFIRM".equals(confirm)) {
            throw new BizException(ErrorCode.CONFLICT, "彻底删除需要二次确认");
        }
        Long workspaceId = requireWorkspace();
        String normalized = type == null ? "" : type.trim();
        if ("kb".equals(normalized)) {
            knowledgeApi.purgeRecycledKb(workspaceId, id);
            return;
        }
        if ("knowledge".equals(normalized)) {
            // 索引清理先行（removeKnowledge 幂等），再物理删除
            try {
                knowledgeApi.removeKnowledge(workspaceId, id);
            } catch (Exception e) {
                log.warn("回收站知识彻底删除索引清理失败（降级继续删除）：knowledgeId={}", id, e);
            }
            contentApi.purgeKnowledge(workspaceId, id);
            return;
        }
        throw new BizException(ErrorCode.INVALID_PARAM, "回收站类型参数非法（kb/knowledge）");
    }

    /** 恢复目录判定：原目录已删除（库内无此目录）→挂库根（0）；否则恢复原目录。 */
    private Long resolveRestoreDirectory(ContentApi.RecycledKnowledgeItem item) {
        if (item.getDirectoryId() == null || item.getDirectoryId() == 0L) {
            return 0L;
        }
        boolean directoryExists = knowledgeApi.getDirectoryTree(item.getKbId()).stream()
                .anyMatch(d -> d.getId().equals(item.getDirectoryId()));
        return directoryExists ? item.getDirectoryId() : 0L;
    }

    /** 当前会话空间，未登录抛 401。 */
    private Long requireWorkspace() {
        Long workspaceId = WorkspaceContext.workspaceId();
        if (workspaceId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return workspaceId;
    }
}
