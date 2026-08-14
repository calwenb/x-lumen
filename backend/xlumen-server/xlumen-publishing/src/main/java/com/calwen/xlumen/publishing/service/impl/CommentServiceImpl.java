package com.calwen.xlumen.publishing.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.identity.api.WorkspaceApi;
import com.calwen.xlumen.publishing.dto.CommentQueryDTO;
import com.calwen.xlumen.publishing.dto.CommentVO;
import com.calwen.xlumen.publishing.dto.CreateCommentDTO;
import com.calwen.xlumen.publishing.dto.PageResult;
import com.calwen.xlumen.publishing.entity.CommentEntity;
import com.calwen.xlumen.publishing.mapper.CommentMapper;
import com.calwen.xlumen.publishing.service.CommentService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评论服务实现（F-0203）：列表/发表 + 批量统计防 N+1。
 * 登录态接口的 workspaceId/userId/userName 全部来自 WorkspaceContext（JWT claims，F-0104）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Service
public class CommentServiceImpl implements CommentService {

    private static final int STATUS_NORMAL = 1;

    @Resource
    private CommentMapper commentMapper;

    @Resource
    private WorkspaceApi workspaceApi;

    @Override
    public PageResult<CommentVO> listComments(Long knowledgeId, CommentQueryDTO query) {
        Page<CommentEntity> page = commentMapper.selectPage(new Page<>(query.getPageNo(), query.getPageSize()),
                Wrappers.<CommentEntity>lambdaQuery()
                        // 跨空间公开读（D9 改写）：评论按知识维度全局查询，不绑定默认空间
                        .eq(CommentEntity::getKnowledgeId, knowledgeId)
                        .eq(CommentEntity::getStatus, STATUS_NORMAL)
                        .orderByAsc(CommentEntity::getCreatedAt));
        List<CommentVO> records = page.getRecords().stream()
                .map(c -> CommentVO.builder()
                        .id(c.getId()).knowledgeId(c.getKnowledgeId()).parentId(c.getParentId())
                        .userName(c.getUserName()).content(c.getContent()).createdAt(c.getCreatedAt()).build())
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
        commentMapper.insert(comment);
        return CommentVO.builder()
                .id(comment.getId()).knowledgeId(comment.getKnowledgeId()).parentId(comment.getParentId())
                .userName(comment.getUserName()).content(comment.getContent()).createdAt(comment.getCreatedAt()).build();
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
