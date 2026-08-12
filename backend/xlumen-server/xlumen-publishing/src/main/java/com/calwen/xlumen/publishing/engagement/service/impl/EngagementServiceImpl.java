package com.calwen.xlumen.publishing.engagement.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.publishing.dto.CommentVO;
import com.calwen.xlumen.publishing.dto.CreateCommentDTO;
import com.calwen.xlumen.publishing.dto.PageResult;
import com.calwen.xlumen.publishing.engagement.entity.CommentEntity;
import com.calwen.xlumen.publishing.engagement.entity.LikeEntity;
import com.calwen.xlumen.publishing.engagement.mapper.CommentMapper;
import com.calwen.xlumen.publishing.engagement.mapper.LikeMapper;
import com.calwen.xlumen.publishing.engagement.service.EngagementService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 互动服务实现（F-0203）：评论/点赞归属校验（双层校验第二层）+ 批量统计防 N+1。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Service
public class EngagementServiceImpl implements EngagementService {

    private static final int STATUS_NORMAL = 1;
    private static final int LIKE_ON = 1;
    private static final int LIKE_OFF = 0;

    @Resource
    private CommentMapper commentMapper;

    @Resource
    private LikeMapper likeMapper;

    @Override
    public PageResult<CommentVO> listComments(Long workspaceId, Long articleId, long pageNo, long pageSize) {
        Page<CommentEntity> page = commentMapper.selectPage(new Page<>(pageNo, pageSize),
                Wrappers.<CommentEntity>lambdaQuery()
                        .eq(CommentEntity::getWorkspaceId, workspaceId)
                        .eq(CommentEntity::getArticleId, articleId)
                        .eq(CommentEntity::getStatus, STATUS_NORMAL)
                        .orderByAsc(CommentEntity::getCreatedAt));
        List<CommentVO> records = page.getRecords().stream()
                .map(c -> new CommentVO(c.getId(), c.getArticleId(), c.getParentId(),
                        c.getUserName(), c.getContent(), c.getCreatedAt()))
                .toList();
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentVO createComment(Long workspaceId, Long articleId, Long userId, String userName, CreateCommentDTO dto) {
        if (userId == null || StrUtil.isBlank(userName)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        CommentEntity comment = new CommentEntity();
        comment.setId(IdUtil.getSnowflakeNextId());
        comment.setWorkspaceId(workspaceId);
        comment.setArticleId(articleId);
        comment.setUserId(userId);
        comment.setUserName(userName);
        comment.setParentId(dto.parentId());
        comment.setContent(dto.content().trim());
        comment.setStatus(STATUS_NORMAL);
        commentMapper.insert(comment);
        return new CommentVO(comment.getId(), comment.getArticleId(), comment.getParentId(),
                comment.getUserName(), comment.getContent(), comment.getCreatedAt());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleLike(Long workspaceId, Long articleId, Long userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
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
    public Map<Long, Long> countComments(Long workspaceId, List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Map.of();
        }
        List<CommentEntity> rows = commentMapper.selectList(Wrappers.<CommentEntity>lambdaQuery()
                .select(CommentEntity::getArticleId)
                .eq(CommentEntity::getWorkspaceId, workspaceId)
                .in(CommentEntity::getArticleId, articleIds)
                .eq(CommentEntity::getStatus, STATUS_NORMAL));
        return rows.stream().collect(Collectors.groupingBy(
                CommentEntity::getArticleId, Collectors.counting()));
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
