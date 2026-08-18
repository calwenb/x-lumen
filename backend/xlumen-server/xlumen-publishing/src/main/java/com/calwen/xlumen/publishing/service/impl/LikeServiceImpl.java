package com.calwen.xlumen.publishing.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.publishing.dto.ReactionStateVO;
import com.calwen.xlumen.publishing.entity.LikeEntity;
import com.calwen.xlumen.publishing.mapper.LikeMapper;
import com.calwen.xlumen.publishing.service.LikeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识反应服务实现（F-0203/F-0212）：三态互斥 toggle（赞/踩共用一行，唯一键幂等）+ 批量统计防 N+1。
 * 登录态接口的 workspaceId/userId 全部来自 WorkspaceContext（JWT claims，F-0104）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Service
public class LikeServiceImpl implements LikeService {

    /** 反应类型：1=点赞 2=点踩（eng_like.reaction_type）。 */
    private static final int TYPE_LIKE = 1;
    private static final int TYPE_DISLIKE = 2;

    private static final int REACTION_ON = 1;
    private static final int REACTION_OFF = 0;

    @Resource
    private LikeMapper likeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String toggleLike(Long knowledgeId) {
        return toggle(knowledgeId, TYPE_LIKE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String toggleDislike(Long knowledgeId) {
        return toggle(knowledgeId, TYPE_DISLIKE);
    }

    /**
     * 三态互斥 toggle（F-0212）：一个用户对一篇知识只有一个活动反应。
     * 无记录或已取消 -> 激活指定类型；活动类型相同 -> 取消；活动类型不同 -> 切换为指定类型。
     */
    private String toggle(Long knowledgeId, int reactionType) {
        Long userId = WorkspaceContext.userId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        Long workspaceId = WorkspaceContext.workspaceId();
        LikeEntity like = likeMapper.selectOne(Wrappers.<LikeEntity>lambdaQuery()
                // workspaceId 可空=跨空间（详情页反应态判定，D9 改写）
                .eq(workspaceId != null, LikeEntity::getWorkspaceId, workspaceId)
                .eq(LikeEntity::getKnowledgeId, knowledgeId)
                .eq(LikeEntity::getUserId, userId));
        if (like == null) {
            like = new LikeEntity();
            like.setId(IdUtil.getSnowflakeNextId());
            like.setWorkspaceId(workspaceId);
            like.setKnowledgeId(knowledgeId);
            like.setUserId(userId);
            like.setReactionType(reactionType);
            like.setStatus(REACTION_ON);
            likeMapper.insert(like);
            return reactionName(reactionType);
        }
        boolean active = like.getStatus() != null && like.getStatus() == REACTION_ON;
        boolean sameType = like.getReactionType() != null && like.getReactionType() == reactionType;
        if (active && sameType) {
            // 当前活动类型与指定类型相同：取消
            like.setStatus(REACTION_OFF);
            likeMapper.updateById(like);
            return ReactionStateVO.NONE;
        }
        // 无活动反应或类型不同：激活/切换为指定类型
        like.setReactionType(reactionType);
        like.setStatus(REACTION_ON);
        likeMapper.updateById(like);
        return reactionName(reactionType);
    }

    @Override
    public String currentReaction(Long knowledgeId) {
        Long userId = WorkspaceContext.userId();
        if (userId == null) {
            return ReactionStateVO.NONE;
        }
        Long workspaceId = WorkspaceContext.workspaceId();
        LikeEntity like = likeMapper.selectOne(Wrappers.<LikeEntity>lambdaQuery()
                .eq(workspaceId != null, LikeEntity::getWorkspaceId, workspaceId)
                .eq(LikeEntity::getKnowledgeId, knowledgeId)
                .eq(LikeEntity::getUserId, userId));
        if (like == null || like.getStatus() == null || like.getStatus() != REACTION_ON
                || like.getReactionType() == null) {
            return ReactionStateVO.NONE;
        }
        return reactionName(like.getReactionType());
    }

    @Override
    public boolean isLiked(Long knowledgeId) {
        return isLiked(WorkspaceContext.workspaceId(), knowledgeId, WorkspaceContext.userId());
    }

    @Override
    public boolean isLiked(Long workspaceId, Long knowledgeId, Long userId) {
        if (userId == null) {
            return false;
        }
        LikeEntity like = likeMapper.selectOne(Wrappers.<LikeEntity>lambdaQuery()
                .eq(LikeEntity::getWorkspaceId, workspaceId)
                .eq(LikeEntity::getKnowledgeId, knowledgeId)
                .eq(LikeEntity::getUserId, userId));
        return like != null && like.getStatus() != null && like.getStatus() == REACTION_ON
                && like.getReactionType() != null && like.getReactionType() == TYPE_LIKE;
    }

    @Override
    public Map<Long, Long> countLikes(Long workspaceId, List<Long> knowledgeIds) {
        return countByType(workspaceId, knowledgeIds, TYPE_LIKE);
    }

    @Override
    public Map<Long, Long> countDislikes(Long workspaceId, List<Long> knowledgeIds) {
        return countByType(workspaceId, knowledgeIds, TYPE_DISLIKE);
    }

    /** 按反应类型批量统计（IN 一次取回，避免 N+1）。 */
    private Map<Long, Long> countByType(Long workspaceId, List<Long> knowledgeIds, int reactionType) {
        if (knowledgeIds == null || knowledgeIds.isEmpty()) {
            return Map.of();
        }
        List<LikeEntity> rows = likeMapper.selectList(Wrappers.<LikeEntity>lambdaQuery()
                .select(LikeEntity::getKnowledgeId)
                // workspaceId 可空=跨空间聚合（多用户公开读，D9 改写）
                .eq(workspaceId != null, LikeEntity::getWorkspaceId, workspaceId)
                .in(LikeEntity::getKnowledgeId, knowledgeIds)
                .eq(LikeEntity::getReactionType, reactionType)
                .eq(LikeEntity::getStatus, REACTION_ON));
        return rows.stream().collect(Collectors.groupingBy(
                LikeEntity::getKnowledgeId, Collectors.counting()));
    }

    /** reaction_type 数值转对外反应名（ReactionStateVO 常量）。 */
    private String reactionName(int reactionType) {
        return reactionType == TYPE_DISLIKE ? ReactionStateVO.DISLIKE : ReactionStateVO.LIKE;
    }
}
