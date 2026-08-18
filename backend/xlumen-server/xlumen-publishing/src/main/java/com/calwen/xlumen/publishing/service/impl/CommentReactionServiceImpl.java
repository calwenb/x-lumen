package com.calwen.xlumen.publishing.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.publishing.dto.ReactionStateVO;
import com.calwen.xlumen.publishing.entity.CommentEntity;
import com.calwen.xlumen.publishing.entity.CommentReactionEntity;
import com.calwen.xlumen.publishing.mapper.CommentMapper;
import com.calwen.xlumen.publishing.mapper.CommentReactionMapper;
import com.calwen.xlumen.publishing.service.CommentReactionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评论反应服务实现（F-0213）：三态互斥 toggle（与知识反应同语义）+ 批量统计防 N+1。
 * 登录态接口的 workspaceId/userId 全部来自 WorkspaceContext（JWT claims，F-0104）。
 *
 * @author calwen
 * @date 2026/8/18
 */
@Service
public class CommentReactionServiceImpl implements CommentReactionService {

    /** 反应类型：1=点赞 2=点踩（eng_comment_reaction.reaction_type）。 */
    private static final int TYPE_LIKE = 1;
    private static final int TYPE_DISLIKE = 2;

    private static final int REACTION_ON = 1;
    private static final int REACTION_OFF = 0;

    private static final int COMMENT_NORMAL = 1;

    @Resource
    private CommentReactionMapper commentReactionMapper;

    @Resource
    private CommentMapper commentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String toggleLike(Long commentId) {
        return toggle(commentId, TYPE_LIKE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String toggleDislike(Long commentId) {
        return toggle(commentId, TYPE_DISLIKE);
    }

    /**
     * 三态互斥 toggle（F-0213，语义与知识反应一致）：先校验评论存在且正常（不存在/已删抛 404），
     * 再按 无反应->激活 / 同类型->取消 / 异类型->切换 处理。
     */
    private String toggle(Long commentId, int reactionType) {
        Long userId = WorkspaceContext.userId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        CommentEntity comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getStatus() == null || comment.getStatus() != COMMENT_NORMAL) {
            throw new BizException(ErrorCode.NOT_FOUND, "评论不存在");
        }
        Long workspaceId = WorkspaceContext.workspaceId();
        CommentReactionEntity reaction = commentReactionMapper.selectOne(Wrappers.<CommentReactionEntity>lambdaQuery()
                // workspaceId 可空=跨空间（公开读评论按知识维度全局查询，D9 改写）
                .eq(workspaceId != null, CommentReactionEntity::getWorkspaceId, workspaceId)
                .eq(CommentReactionEntity::getCommentId, commentId)
                .eq(CommentReactionEntity::getUserId, userId));
        if (reaction == null) {
            reaction = new CommentReactionEntity();
            reaction.setId(IdUtil.getSnowflakeNextId());
            reaction.setWorkspaceId(workspaceId);
            reaction.setCommentId(commentId);
            reaction.setUserId(userId);
            reaction.setReactionType(reactionType);
            reaction.setStatus(REACTION_ON);
            commentReactionMapper.insert(reaction);
            return reactionName(reactionType);
        }
        boolean active = reaction.getStatus() != null && reaction.getStatus() == REACTION_ON;
        boolean sameType = reaction.getReactionType() != null && reaction.getReactionType() == reactionType;
        if (active && sameType) {
            // 当前活动类型与指定类型相同：取消
            reaction.setStatus(REACTION_OFF);
            commentReactionMapper.updateById(reaction);
            return ReactionStateVO.NONE;
        }
        // 无活动反应或类型不同：激活/切换为指定类型
        reaction.setReactionType(reactionType);
        reaction.setStatus(REACTION_ON);
        commentReactionMapper.updateById(reaction);
        return reactionName(reactionType);
    }

    @Override
    public Map<Long, Long> countLikes(Long workspaceId, List<Long> commentIds) {
        return countByType(workspaceId, commentIds, TYPE_LIKE);
    }

    @Override
    public Map<Long, Long> countDislikes(Long workspaceId, List<Long> commentIds) {
        return countByType(workspaceId, commentIds, TYPE_DISLIKE);
    }

    /** 按反应类型批量统计（IN 一次取回，避免 N+1）。 */
    private Map<Long, Long> countByType(Long workspaceId, List<Long> commentIds, int reactionType) {
        if (commentIds == null || commentIds.isEmpty()) {
            return Map.of();
        }
        List<CommentReactionEntity> rows = commentReactionMapper.selectList(Wrappers.<CommentReactionEntity>lambdaQuery()
                .select(CommentReactionEntity::getCommentId)
                // workspaceId 可空=跨空间聚合（多用户公开读，D9 改写）
                .eq(workspaceId != null, CommentReactionEntity::getWorkspaceId, workspaceId)
                .in(CommentReactionEntity::getCommentId, commentIds)
                .eq(CommentReactionEntity::getReactionType, reactionType)
                .eq(CommentReactionEntity::getStatus, REACTION_ON));
        return rows.stream().collect(Collectors.groupingBy(
                CommentReactionEntity::getCommentId, Collectors.counting()));
    }

    @Override
    public Map<Long, String> mapUserReactions(Long workspaceId, List<Long> commentIds, Long userId) {
        if (userId == null || commentIds == null || commentIds.isEmpty()) {
            return Map.of();
        }
        List<CommentReactionEntity> rows = commentReactionMapper.selectList(Wrappers.<CommentReactionEntity>lambdaQuery()
                // workspaceId 可空=跨空间聚合（多用户公开读，D9 改写）
                .eq(workspaceId != null, CommentReactionEntity::getWorkspaceId, workspaceId)
                .in(CommentReactionEntity::getCommentId, commentIds)
                .eq(CommentReactionEntity::getUserId, userId)
                .eq(CommentReactionEntity::getStatus, REACTION_ON));
        return rows.stream().filter(r -> r.getReactionType() != null)
                .collect(Collectors.toMap(CommentReactionEntity::getCommentId,
                        r -> reactionName(r.getReactionType()), (a, b) -> a));
    }

    /** reaction_type 数值转对外反应名（ReactionStateVO 常量）。 */
    private String reactionName(int reactionType) {
        return reactionType == TYPE_DISLIKE ? ReactionStateVO.DISLIKE : ReactionStateVO.LIKE;
    }
}
