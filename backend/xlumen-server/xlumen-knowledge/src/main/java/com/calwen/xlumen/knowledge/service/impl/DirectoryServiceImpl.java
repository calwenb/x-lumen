package com.calwen.xlumen.knowledge.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.knowledge.dto.CreateDirectoryDTO;
import com.calwen.xlumen.knowledge.dto.UpdateDirectoryDTO;
import com.calwen.xlumen.knowledge.entity.KbDirectoryEntity;
import com.calwen.xlumen.knowledge.entity.KbKnowledgeBaseEntity;
import com.calwen.xlumen.knowledge.event.KbDirectoryDeletedEvent;
import com.calwen.xlumen.knowledge.mapper.KbDirectoryMapper;
import com.calwen.xlumen.knowledge.mapper.KbKnowledgeBaseMapper;
import com.calwen.xlumen.knowledge.service.DirectoryService;
import com.calwen.xlumen.knowledge.vo.DirectoryVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 目录服务实现（F-0309，决策 D16）：目录树组装按名称排序（数据库排序规则），
 * 删除目录时知识上挂父目录的写入联动通过 KbDirectoryDeletedEvent 交给 content 侧（cnt_knowledge 属 content 模块）。
 *
 * @author calwen
 * @date 2026/8/14
 */
@Slf4j
@Service
public class DirectoryServiceImpl implements DirectoryService {

    @Resource
    private KbDirectoryMapper directoryMapper;
    @Resource
    private KbKnowledgeBaseMapper kbMapper;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    public List<DirectoryVO> tree(Long kbId) {
        requireKb(kbId);
        List<KbDirectoryEntity> dirs = directoryMapper.selectList(Wrappers.<KbDirectoryEntity>lambdaQuery()
                .eq(KbDirectoryEntity::getKbId, kbId)
                .orderByAsc(KbDirectoryEntity::getName));
        // 按父目录分组（组内保持 SQL 名称排序）；一级目录平铺返回，子目录挂 children
        Map<Long, List<DirectoryVO>> byParent = dirs.stream()
                .map(this::toVO)
                .collect(Collectors.groupingBy(DirectoryVO::getParentId, LinkedHashMap::new, Collectors.toList()));
        byParent.values().forEach(list -> list.forEach(vo -> vo.setChildren(byParent.getOrDefault(vo.getId(), List.of()))));
        return byParent.getOrDefault(0L, List.of());
    }

    @Override
    public DirectoryVO create(Long kbId, CreateDirectoryDTO dto) {
        requireKb(kbId);
        Long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        if (parentId != 0L) {
            KbDirectoryEntity parent = directoryMapper.selectById(parentId);
            if (parent == null || !kbId.equals(parent.getKbId())) {
                throw new BizException(ErrorCode.INVALID_PARAM, "父目录不存在或不属于当前知识库");
            }
        }
        Long count = directoryMapper.selectCount(Wrappers.<KbDirectoryEntity>lambdaQuery()
                .eq(KbDirectoryEntity::getKbId, kbId)
                .eq(KbDirectoryEntity::getParentId, parentId)
                .eq(KbDirectoryEntity::getName, dto.getName().trim()));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.CONFLICT, "同级已存在同名目录");
        }
        LocalDateTime now = LocalDateTime.now();
        KbDirectoryEntity entity = new KbDirectoryEntity();
        entity.setId(IdUtil.getSnowflakeNextId());
        entity.setKbId(kbId);
        entity.setParentId(parentId);
        entity.setName(dto.getName().trim());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        directoryMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    public void update(Long kbId, Long directoryId, UpdateDirectoryDTO dto) {
        KbDirectoryEntity dir = getOwnedDirectory(kbId, directoryId);
        if (StrUtil.isNotBlank(dto.getName())) {
            String name = dto.getName().trim();
            if (!name.equals(dir.getName())) {
                Long count = directoryMapper.selectCount(Wrappers.<KbDirectoryEntity>lambdaQuery()
                        .eq(KbDirectoryEntity::getKbId, kbId)
                        .eq(KbDirectoryEntity::getParentId, dir.getParentId())
                        .eq(KbDirectoryEntity::getName, name)
                        .ne(KbDirectoryEntity::getId, directoryId));
                if (count != null && count > 0) {
                    throw new BizException(ErrorCode.CONFLICT, "同级已存在同名目录");
                }
                dir.setName(name);
            }
        }
        dir.setUpdatedAt(LocalDateTime.now());
        directoryMapper.updateById(dir);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long kbId, Long directoryId) {
        KbDirectoryEntity dir = getOwnedDirectory(kbId, directoryId);
        KbKnowledgeBaseEntity kb = kbMapper.selectById(kbId);
        // 子树 ID（含自身，父→子顺序）
        List<Long> subtreeIds = collectSubtreeIds(kbId, directoryId);
        Long newParentId = dir.getParentId() == null ? 0L : dir.getParentId();
        directoryMapper.deleteByIds(subtreeIds);
        // 知识上挂父目录：cnt_knowledge.directory_id 属 content 模块（knowledge 依赖方向受限无法直改），
        // 由 content 侧监听 KbDirectoryDeletedEvent 迁移（KB-3 content 改造）。
        eventPublisher.publishEvent(KbDirectoryDeletedEvent.builder()
                .workspaceId(kb.getWorkspaceId())
                .kbId(kbId)
                .directoryIds(subtreeIds)
                .newDirectoryId(newParentId)
                .build());
        log.info("目录已删除（子树知识上挂待 content 侧监听处理）：workspaceId={}, kbId={}, directoryId={}, subtreeIds={}",
                kb.getWorkspaceId(), kbId, directoryId, subtreeIds);
    }

    @Override
    public boolean belongsTo(Long kbId, Long directoryId) {
        if (directoryId == null || directoryId == 0L) {
            // 0=库根：仅校验库（由调用方先校验 kb 存在与归属）
            return true;
        }
        KbDirectoryEntity dir = directoryMapper.selectById(directoryId);
        return dir != null && kbId.equals(dir.getKbId());
    }

    /** 广度优先收集子树目录 ID（含自身，父→子顺序）。 */
    private List<Long> collectSubtreeIds(Long kbId, Long directoryId) {
        List<KbDirectoryEntity> all = directoryMapper.selectList(Wrappers.<KbDirectoryEntity>lambdaQuery()
                .eq(KbDirectoryEntity::getKbId, kbId));
        Map<Long, List<Long>> children = all.stream().collect(Collectors.groupingBy(KbDirectoryEntity::getParentId,
                Collectors.mapping(KbDirectoryEntity::getId, Collectors.toList())));
        List<Long> result = new ArrayList<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(directoryId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            result.add(current);
            queue.addAll(children.getOrDefault(current, List.of()));
        }
        return result;
    }

    /** 按库校验存在与会话空间归属，不存在/跨空间统一 404。 */
    private KbKnowledgeBaseEntity requireKb(Long kbId) {
        KbKnowledgeBaseEntity kb = kbMapper.selectById(kbId);
        if (kb == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
        Long workspaceId = WorkspaceContext.workspaceId();
        if (workspaceId != null && !workspaceId.equals(kb.getWorkspaceId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
        return kb;
    }

    /** 按目录取实体并校验属于指定库（先经 requireKb 完成跨空间校验），不存在统一 404。 */
    private KbDirectoryEntity getOwnedDirectory(Long kbId, Long directoryId) {
        requireKb(kbId);
        KbDirectoryEntity dir = directoryMapper.selectById(directoryId);
        if (dir == null || !kbId.equals(dir.getKbId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "目录不存在");
        }
        return dir;
    }

    /** 实体转视图；knowledgeCount 当前恒为 0（content 侧统计待 KB-3 content 改造补全）。 */
    private DirectoryVO toVO(KbDirectoryEntity d) {
        return DirectoryVO.builder()
                .id(d.getId())
                .kbId(d.getKbId())
                .parentId(d.getParentId())
                .name(d.getName())
                .knowledgeCount(0L)
                .children(new ArrayList<>())
                .build();
    }
}
