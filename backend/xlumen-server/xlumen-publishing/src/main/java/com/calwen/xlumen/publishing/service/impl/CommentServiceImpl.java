package com.calwen.xlumen.publishing.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.content.api.ContentApi;
import com.calwen.xlumen.content.api.dto.EditorKnowledgeDTO;
import com.calwen.xlumen.identity.api.WorkspaceApi;
import com.calwen.xlumen.publishing.dto.CommentQueryDTO;
import com.calwen.xlumen.publishing.dto.CommentVO;
import com.calwen.xlumen.publishing.dto.CreateCommentDTO;
import com.calwen.xlumen.publishing.dto.PageResult;
import com.calwen.xlumen.publishing.entity.CommentEntity;
import com.calwen.xlumen.publishing.event.CommentAiEchoRequestedEvent;
import com.calwen.xlumen.publishing.mapper.CommentMapper;
import com.calwen.xlumen.publishing.service.CommentReactionService;
import com.calwen.xlumen.publishing.service.CommentService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评论服务实现：列表/发表 + 批量统计防 N+1（评论数与赞/踩计数同模式聚合）。
 * 登录态接口的 workspaceId/userId/userName 全部来自 WorkspaceContext（JWT claims）。
 * 发表评论命中 @小光 时进程内事件同步触发小光回复（@EventListener 同线程，生成失败不影响主流程）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Service
public class CommentServiceImpl implements CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentServiceImpl.class);

    private static final int STATUS_NORMAL = 1;

    /** @小光 触发语（兼容半角/全角 @）。 */
    private static final String AT_XIAO_GUANG = "@小光";

    private static final String AT_XIAO_GUANG_FULL = "＠小光";

    @Resource
    private CommentMapper commentMapper;

    @Resource
    private WorkspaceApi workspaceApi;

    @Resource
    private CommentReactionService commentReactionService;

    @Resource
    private ContentApi contentApi;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    public PageResult<CommentVO> listComments(Long knowledgeId, CommentQueryDTO query) {
        Page<CommentEntity> page = commentMapper.selectPage(new Page<>(query.getPageNo(), query.getPageSize()),
                Wrappers.<CommentEntity>lambdaQuery()
                        // 跨空间公开读（D9 改写）：评论按知识维度全局查询，不绑定默认空间
                        .eq(CommentEntity::getKnowledgeId, knowledgeId)
                        .eq(CommentEntity::getStatus, STATUS_NORMAL)
                        .orderByAsc(CommentEntity::getCreatedAt));
        // 赞/踩计数与当前用户反应批量聚合（IN 一次取回，避免 N+1，BACKEND.md §18；
        // 跨空间聚合 workspaceId 传 null，匿名用户 myReaction 全部为 null）
        List<Long> commentIds = page.getRecords().stream().map(CommentEntity::getId).toList();
        Long userId = WorkspaceContext.userId();
        Map<Long, Long> likeCounts = commentReactionService.countLikes(null, commentIds);
        Map<Long, Long> dislikeCounts = commentReactionService.countDislikes(null, commentIds);
        Map<Long, String> myReactions = commentReactionService.mapUserReactions(null, commentIds, userId);
        List<CommentVO> records = page.getRecords().stream()
                .map(c -> CommentVO.builder()
                        .id(c.getId()).knowledgeId(c.getKnowledgeId()).parentId(c.getParentId())
                        .userName(c.getUserName()).content(c.getContent())
                        .isAi(c.getIsAi() != null && c.getIsAi() == 1)
                        .citationsJson(c.getCitationsJson()).createdAt(c.getCreatedAt())
                        .likeCount(likeCounts.getOrDefault(c.getId(), 0L))
                        .dislikeCount(dislikeCounts.getOrDefault(c.getId(), 0L))
                        .myReaction(userId == null ? null : myReactions.get(c.getId())).build())
                .toList();
        return PageResult.<CommentVO>builder()
                .total(page.getTotal()).pageNo(page.getCurrent()).pageSize(page.getSize()).records(records).build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentVO createComment(Long knowledgeId, CreateCommentDTO dto) {
        Long userId = WorkspaceContext.userId();
        String userName = WorkspaceContext.username();
        if (userId == null || StrUtil.isBlank(userName)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        CommentEntity comment = new CommentEntity();
        comment.setId(IdUtil.getSnowflakeNextId());
        comment.setWorkspaceId(WorkspaceContext.workspaceId());
        comment.setKnowledgeId(knowledgeId);
        comment.setUserId(userId);
        comment.setUserName(userName);
        comment.setParentId(dto.getParentId());
        comment.setContent(dto.getContent().trim());
        comment.setStatus(STATUS_NORMAL);
        // DB 有 DEFAULT CURRENT_TIMESTAMP 但 MyBatis-Plus insert 不回填内存实体，
        // 不手动赋值则返回 VO 的 createdAt 为 null（前端「xx 天前」把 null 当 1970）。
        comment.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(comment);
        triggerAiEcho(comment);
        return CommentVO.builder()
                .id(comment.getId()).knowledgeId(comment.getKnowledgeId()).parentId(comment.getParentId())
                .userName(comment.getUserName()).content(comment.getContent())
                .isAi(false).createdAt(comment.getCreatedAt()).build();
    }

    /**
     * 命中 @小光（兼容半角/全角 @）→ 发布进程内事件触发小光回复。
     * 同步执行且全程吞异常：生成失败绝不影响评论主流程（先落评论行，回复失败仅少一条机器回复）。
     */
    private void triggerAiEcho(CommentEntity comment) {
        if (!isAiEchoRequest(comment.getContent())) {
            return;
        }
        try {
            EditorKnowledgeDTO knowledge = contentApi.getEditorKnowledge(
                    comment.getWorkspaceId(), comment.getKnowledgeId());
            eventPublisher.publishEvent(CommentAiEchoRequestedEvent.builder()
                    .workspaceId(comment.getWorkspaceId())
                    .knowledgeId(comment.getKnowledgeId())
                    .userId(comment.getUserId())
                    .knowledgeTitle(knowledge == null ? null : knowledge.getTitle())
                    .knowledgeContent(knowledge == null ? null : knowledge.getContent())
                    .commentId(comment.getId())
                    .commentContent(comment.getContent())
                    .username(comment.getUserName())
                    .build());
        } catch (Exception e) {
            log.warn("触发小光评论回复失败 commentId={}", comment.getId(), e);
        }
    }

    /** @小光 触发判定：半角/全角 @ 均兼容（正文已在发表时 trim，尾部空白不参与匹配）。 */
    private boolean isAiEchoRequest(String content) {
        if (StrUtil.isBlank(content)) {
            return false;
        }
        return content.contains(AT_XIAO_GUANG) || content.contains(AT_XIAO_GUANG_FULL);
    }

    @Override
    public Map<Long, Long> countComments(Long workspaceId, List<Long> knowledgeIds) {
        if (knowledgeIds == null || knowledgeIds.isEmpty()) {
            return Map.of();
        }
        List<CommentEntity> rows = commentMapper.selectList(Wrappers.<CommentEntity>lambdaQuery()
                .select(CommentEntity::getKnowledgeId)
                // workspaceId 可空=跨空间聚合（多用户公开读，D9 改写）
                .eq(workspaceId != null, CommentEntity::getWorkspaceId, workspaceId)
                .in(CommentEntity::getKnowledgeId, knowledgeIds)
                .eq(CommentEntity::getStatus, STATUS_NORMAL));
        return rows.stream().collect(Collectors.groupingBy(
                CommentEntity::getKnowledgeId, Collectors.counting()));
    }
}
