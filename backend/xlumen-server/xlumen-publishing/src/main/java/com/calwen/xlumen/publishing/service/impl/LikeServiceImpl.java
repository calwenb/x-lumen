package com.calwen.xlumen.publishing.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
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
 * 点赞服务实现（F-0203）：切换幂等 + 批量统计防 N+1。
 * 登录态接口的 workspaceId/userId 全部来自 WorkspaceContext（JWT claims，F-0104）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Service
public class LikeServiceImpl implements LikeService {

    private static final int LIKE_ON = 1;
    private static final int LIKE_OFF = 0;

    @Resource
    private LikeMapper likeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleLike(Long articleId) {
        Long userId = WorkspaceContext.userId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        Long workspaceId = WorkspaceContext.workspaceId();
        LikeEntity like = likeMapper.selectOne(Wrappers.<LikeEntity>lambdaQuery()
                .eq(LikeEntity::getWorkspaceId, workspaceId)
                .eq(LikeEntity::getArticleId, articleId)
                .eq(LikeEntity::getUserId, userId));
        if (like == null) {
            like = new LikeEntity();
            like.setId(IdUtil.getSnowflakeNextId());
            like.setWorkspaceId(workspaceId);
            like.setArticleId(articleId);
            like.setUserId(userId);
            like.setStatus(LIKE_ON);
            likeMapper.insert(like);
            return true;
        }
        boolean next = like.getStatus() == null || like.getStatus() != LIKE_ON;
        like.setStatus(next ? LIKE_ON : LIKE_OFF);
        likeMapper.updateById(like);
        return next;
    }

    @Override
    public boolean isLiked(Long articleId) {
        return isLiked(WorkspaceContext.workspaceId(), articleId, WorkspaceContext.userId());
    }

    @Override
    public boolean isLiked(Long workspaceId, Long articleId, Long userId) {
        if (userId == null) {
            return false;
        }
        LikeEntity like = likeMapper.selectOne(Wrappers.<LikeEntity>lambdaQuery()
                .eq(LikeEntity::getWorkspaceId, workspaceId)
                .eq(LikeEntity::getArticleId, articleId)
                .eq(LikeEntity::getUserId, userId));
        return like != null && like.getStatus() != null && like.getStatus() == LIKE_ON;
    }

    @Override
    public Map<Long, Long> countLikes(Long workspaceId, List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Map.of();
        }
        List<LikeEntity> rows = likeMapper.selectList(Wrappers.<LikeEntity>lambdaQuery()
                .select(LikeEntity::getArticleId)
                .eq(LikeEntity::getWorkspaceId, workspaceId)
                .in(LikeEntity::getArticleId, articleIds)
                .eq(LikeEntity::getStatus, LIKE_ON));
        return rows.stream().collect(Collectors.groupingBy(
                LikeEntity::getArticleId, Collectors.counting()));
    }
}
