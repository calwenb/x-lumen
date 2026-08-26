package com.calwen.xlumen.publishing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.publishing.dto.ChangelogDTO;
import com.calwen.xlumen.publishing.dto.ChangelogItemVO;
import com.calwen.xlumen.publishing.dto.ChangelogPageVO;
import com.calwen.xlumen.publishing.entity.ChangelogEntity;
import com.calwen.xlumen.publishing.mapper.ChangelogMapper;
import com.calwen.xlumen.publishing.service.ChangelogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站点更新日志实现：发布切换时维护 published_at；公开页仅返回已发布。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Service
public class ChangelogServiceImpl implements ChangelogService {

    private final ChangelogMapper changelogMapper;

    public ChangelogServiceImpl(ChangelogMapper changelogMapper) {
        this.changelogMapper = changelogMapper;
    }

    @Override
    public ChangelogPageVO adminPage(Long workspaceId, long pageNo, long pageSize) {
        LambdaQueryWrapper<ChangelogEntity> query = new LambdaQueryWrapper<ChangelogEntity>()
                .eq(ChangelogEntity::getWorkspaceId, workspaceId)
                .orderByDesc(ChangelogEntity::getUpdatedAt);
        long total = changelogMapper.selectCount(query.clone());
        List<ChangelogEntity> rows = changelogMapper.selectList(query.last("LIMIT " + offset(pageNo, pageSize) + "," + pageSize));
        return ChangelogPageVO.builder()
                .records(rows.stream().map(this::toVO).toList())
                .total(total)
                .build();
    }

    @Override
    public Long create(Long workspaceId, ChangelogDTO dto) {
        ChangelogEntity entity = new ChangelogEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setTitle(dto.getTitle().trim());
        entity.setContent(dto.getContent());
        boolean published = Boolean.TRUE.equals(dto.getPublished());
        entity.setPublished(published ? 1 : 0);
        entity.setPublishedAt(published ? LocalDateTime.now() : null);
        changelogMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long workspaceId, Long id, ChangelogDTO dto) {
        ChangelogEntity entity = getOwned(workspaceId, id);
        entity.setTitle(dto.getTitle().trim());
        entity.setContent(dto.getContent());
        if (dto.getPublished() != null) {
            boolean published = dto.getPublished();
            entity.setPublished(published ? 1 : 0);
            entity.setPublishedAt(published
                    ? (entity.getPublishedAt() == null ? LocalDateTime.now() : entity.getPublishedAt())
                    : null);
        }
        changelogMapper.updateById(entity);
    }

    @Override
    public void delete(Long workspaceId, Long id) {
        ChangelogEntity entity = getOwned(workspaceId, id);
        changelogMapper.deleteById(entity.getId());
    }

    @Override
    public ChangelogPageVO publicPage(Long workspaceId, long pageNo, long pageSize) {
        LambdaQueryWrapper<ChangelogEntity> query = new LambdaQueryWrapper<ChangelogEntity>()
                .eq(workspaceId != null, ChangelogEntity::getWorkspaceId, workspaceId)
                .eq(ChangelogEntity::getPublished, 1)
                .orderByDesc(ChangelogEntity::getPublishedAt);
        long total = changelogMapper.selectCount(query.clone());
        List<ChangelogEntity> rows = changelogMapper.selectList(query.last("LIMIT " + offset(pageNo, pageSize) + "," + pageSize));
        return ChangelogPageVO.builder()
                .records(rows.stream().map(this::toVO).toList())
                .total(total)
                .build();
    }

    private ChangelogEntity getOwned(Long workspaceId, Long id) {
        ChangelogEntity entity = changelogMapper.selectOne(new LambdaQueryWrapper<ChangelogEntity>()
                .eq(ChangelogEntity::getId, id)
                .eq(ChangelogEntity::getWorkspaceId, workspaceId)
                .last("LIMIT 1"));
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "更新日志不存在");
        }
        return entity;
    }

    private long offset(long pageNo, long pageSize) {
        return Math.max(pageNo - 1, 0) * pageSize;
    }

    private ChangelogItemVO toVO(ChangelogEntity e) {
        return ChangelogItemVO.builder()
                .id(e.getId())
                .title(e.getTitle())
                .content(e.getContent())
                .published(Integer.valueOf(1).equals(e.getPublished()))
                .publishedAt(e.getPublishedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}