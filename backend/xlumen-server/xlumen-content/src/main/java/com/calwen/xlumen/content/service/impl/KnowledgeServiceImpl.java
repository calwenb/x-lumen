package com.calwen.xlumen.content.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.dto.PageQueryDTO;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.content.api.dto.ContentPageResult;
import com.calwen.xlumen.content.dto.KnowledgeListQueryDTO;
import com.calwen.xlumen.content.dto.CreateKnowledgeDTO;
import com.calwen.xlumen.content.dto.DraftSaveDTO;
import com.calwen.xlumen.content.dto.UpdateKnowledgeDTO;
import com.calwen.xlumen.content.entity.KnowledgeEntity;
import com.calwen.xlumen.content.entity.KnowledgeVersionEntity;
import com.calwen.xlumen.content.enums.KnowledgeStatus;
import com.calwen.xlumen.content.mapper.KnowledgeMapper;
import com.calwen.xlumen.content.mapper.KnowledgeVersionMapper;
import com.calwen.xlumen.content.service.KnowledgeService;
import com.calwen.xlumen.content.vo.KnowledgeListItemVO;
import com.calwen.xlumen.content.vo.KnowledgeVersionVO;
import com.calwen.xlumen.content.vo.KnowledgeVO;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 知识服务实现（F-0301/F-0302/F-0307）：作者与空间来自 WorkspaceContext（JWT claims，F-0104 双层校验第二层）。
 * 版本乐观锁：MyBatis-Plus @Version 插件，updateById 影响行数 0 即冲突（HTTP 409，PRODUCT §6 禁止静默覆盖）。
 * 已发布版本正文不可修改（PRODUCT §4），修改需走旧文更新闭环（V2 F-1105）。
 * KB-3（决策 D16）：知识单库单目录归属（kb_id+directory_id），无文章级可见性；删除改回收站软删（F-0305）。
 * 归属校验（BACKEND.md §4 依赖 DAG：content→knowledge）：创建/自动保存经 KnowledgeApi.checkOwnership 校验
 * 知识库/目录归属，禁止无归属（kb_id=0）或跨空间孤儿知识入库。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    /** 回收站状态：回收中（F-0305 独立软删标记，不扩 8 状态机）。 */
    private static final int RECYCLE_STATUS_DELETED = 1;
    /** 自动保存新建草稿的默认标题。 */
    private static final String UNTITLED = "未命名草稿";

    @Resource
    private KnowledgeMapper knowledgeMapper;

    @Resource
    private KnowledgeVersionMapper knowledgeVersionMapper;

    @Resource
    private KnowledgeApi knowledgeApi;

    @Override
    public KnowledgeVO create(CreateKnowledgeDTO dto) {
        // 单库单目录归属校验（决策 D16）：kbId 必填且库/目录必须属于当前空间，禁止孤儿知识
        Long kbId = dto.getKbId();
        Long workspaceId = requireWorkspaceId();
        if (kbId == null || !knowledgeApi.checkOwnership(workspaceId, kbId, dto.getDirectoryId())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "请选择有效的知识库与目录");
        }
        KnowledgeEntity entity = new KnowledgeEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setAuthorId(requireUserId());
        entity.setAuthorName(WorkspaceContext.username());
        entity.setTitle(dto.getTitle());
        entity.setContent(dto.getContent() == null ? "" : dto.getContent());
        // 单库单目录归属（决策 D16）：directoryId 空默认挂库根（0）
        entity.setKbId(kbId);
        entity.setDirectoryId(dto.getDirectoryId() == null ? 0L : dto.getDirectoryId());
        entity.setTags(dto.getTags());
        entity.setStatus(KnowledgeStatus.DRAFT.getValue());
        entity.setVersion(0L);
        entity.setViewCount(0L);
        entity.setRecycleStatus(0);
        entity.setPublishedAt(null);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        knowledgeMapper.insert(entity);
        saveVersionSnapshot(entity);
        return toVO(entity);
    }

    @Override
    public KnowledgeVO update(Long knowledgeId, UpdateKnowledgeDTO dto) {
        KnowledgeEntity entity = getOwned(knowledgeId);
        checkEditable(entity);
        entity.setTitle(dto.getTitle());
        entity.setContent(dto.getContent() == null ? "" : dto.getContent());
        if (dto.getDirectoryId() != null) {
            // 同库内换目录（跨库移动不提供，决策 D16）：目录必须属于当前知识库，越界拒绝（BUG-013）
            if (!knowledgeApi.checkOwnership(requireWorkspaceId(), entity.getKbId(), dto.getDirectoryId())) {
                throw new BizException(ErrorCode.INVALID_PARAM, "目录不属于当前知识库");
            }
            entity.setDirectoryId(dto.getDirectoryId());
        }
        entity.setTags(dto.getTags());
        // 乐观锁：仅当携带版本号与当前一致才允许更新
        entity.setVersion(dto.getVersion());
        if (knowledgeMapper.updateById(entity) == 0) {
            throw new BizException(ErrorCode.CONFLICT, "知识已被修改，请刷新后重试");
        }
        saveVersionSnapshot(entity);
        return toVO(getOwned(knowledgeId));
    }

    @Override
    public KnowledgeVO autosave(DraftSaveDTO dto) {
        if (dto.getKnowledgeId() == null) {
            CreateKnowledgeDTO create = CreateKnowledgeDTO.builder()
                    .title(StrUtil.blankToDefault(dto.getTitle(), UNTITLED))
                    .content(dto.getContent())
                    .kbId(dto.getKbId())
                    .directoryId(dto.getDirectoryId())
                    .tags(dto.getTags()).build();
            return create(create);
        }
        KnowledgeEntity entity = getOwned(dto.getKnowledgeId());
        checkEditable(entity);
        String newContent = dto.getContent() == null ? "" : dto.getContent();
        Long newDirectoryId = dto.getDirectoryId() == null ? entity.getDirectoryId() : dto.getDirectoryId();
        List<String> newTags = dto.getTags() == null ? entity.getTags() : dto.getTags();
        // 幂等：正文、标题、目录和标签均未变化时才跳过，避免发布读取到旧元数据。
        if (Objects.equals(entity.getContent(), newContent)
                && Objects.equals(entity.getTitle(), dto.getTitle())
                && Objects.equals(entity.getDirectoryId(), newDirectoryId)
                && Objects.equals(entity.getTags(), newTags)) {
            return toVO(entity);
        }
        entity.setTitle(StrUtil.blankToDefault(dto.getTitle(), entity.getTitle()));
        entity.setContent(newContent);
        if (dto.getDirectoryId() != null) {
            // 同库内换目录（决策 D16）：目录必须属于当前知识库，越界拒绝（BUG-013）
            if (!knowledgeApi.checkOwnership(requireWorkspaceId(), entity.getKbId(), dto.getDirectoryId())) {
                throw new BizException(ErrorCode.INVALID_PARAM, "目录不属于当前知识库");
            }
            entity.setDirectoryId(newDirectoryId);
        }
        if (dto.getTags() != null) {
            entity.setTags(newTags);
        }
        entity.setVersion(dto.getVersion() == null ? entity.getVersion() : dto.getVersion());
        if (knowledgeMapper.updateById(entity) == 0) {
            throw new BizException(ErrorCode.CONFLICT, "草稿已被修改，请刷新后重试");
        }
        saveVersionSnapshot(entity);
        return toVO(getOwned(dto.getKnowledgeId()));
    }

    @Override
    public KnowledgeVO get(Long knowledgeId) {
        return toVO(getOwned(knowledgeId));
    }

    @Override
    public ContentPageResult<KnowledgeListItemVO> list(KnowledgeListQueryDTO query) {
        long pageNo = Math.max(1, query.getPageNo());
        long pageSize = Math.min(100, Math.max(1, query.getPageSize()));
        Page<KnowledgeEntity> page = knowledgeMapper.selectPage(new Page<>(pageNo, pageSize),
                Wrappers.<KnowledgeEntity>lambdaQuery()
                        .eq(KnowledgeEntity::getWorkspaceId, requireWorkspaceId())
                        .eq(KnowledgeEntity::getAuthorId, requireUserId())
                        // 过滤回收站（F-0305 软删不展示在知识管理列表）
                        .eq(KnowledgeEntity::getRecycleStatus, 0)
                        .eq(query.getStatus() != null, KnowledgeEntity::getStatus, query.getStatus())
                        .eq(query.getKbId() != null, KnowledgeEntity::getKbId, query.getKbId())
                        .eq(query.getDirectoryId() != null, KnowledgeEntity::getDirectoryId, query.getDirectoryId())
                        .like(StrUtil.isNotBlank(query.getKeyword()), KnowledgeEntity::getTitle, query.getKeyword())
                        .orderByDesc(KnowledgeEntity::getUpdatedAt));
        List<KnowledgeListItemVO> records = page.getRecords().stream().map(this::toListItem).toList();
        return ContentPageResult.<KnowledgeListItemVO>builder()
                .total(page.getTotal()).pageNo(page.getCurrent()).pageSize(page.getSize()).records(records).build();
    }

    @Override
    public void delete(Long knowledgeId) {
        KnowledgeEntity entity = getOwned(knowledgeId);
        KnowledgeStatus status = KnowledgeStatus.of(entity.getStatus());
        // BUG-016 配套：已下架（8）可删除（「删除已发布需先下架」闭环）
        if (status != KnowledgeStatus.IDEA && status != KnowledgeStatus.DRAFT && status != KnowledgeStatus.UNPUBLISHED) {
            throw new BizException(ErrorCode.CONFLICT, "仅构思/草稿/已下架可删除，已发布请先下架");
        }
        if (entity.getRecycleStatus() != null && entity.getRecycleStatus() == RECYCLE_STATUS_DELETED) {
            throw new BizException(ErrorCode.CONFLICT, "知识已在回收站");
        }
        // F-0305 回收站软删：标记 recycle_status + deleted_at，不物理删除（超期清理由回收站任务负责）
        knowledgeMapper.update(null, Wrappers.<KnowledgeEntity>lambdaUpdate()
                .eq(KnowledgeEntity::getId, entity.getId())
                .eq(KnowledgeEntity::getWorkspaceId, requireWorkspaceId())
                .set(KnowledgeEntity::getRecycleStatus, RECYCLE_STATUS_DELETED)
                .set(KnowledgeEntity::getDeletedAt, LocalDateTime.now()));
    }

    @Override
    public void restore(Long knowledgeId) {
        KnowledgeEntity entity = getOwned(knowledgeId);
        if (entity.getRecycleStatus() == null || entity.getRecycleStatus() != RECYCLE_STATUS_DELETED) {
            throw new BizException(ErrorCode.CONFLICT, "知识不在回收站");
        }
        // 仅清除软删标记；原目录/知识库已被彻底删除等冲突校验由 knowledge 模块回收站服务统一处理
        if (knowledgeMapper.restore(entity.getId(), requireWorkspaceId()) == 0) {
            throw new BizException(ErrorCode.CONFLICT, "恢复失败，请刷新后重试");
        }
    }

    @Override
    public ContentPageResult<KnowledgeVersionVO> listVersions(Long knowledgeId, PageQueryDTO query) {
        long pageNo = Math.max(1, query.getPageNo());
        long pageSize = Math.min(100, Math.max(1, query.getPageSize()));
        // 归属校验：不存在/越权 404（与 get 一致）
        getOwned(knowledgeId);
        Page<KnowledgeVersionEntity> page = knowledgeVersionMapper.selectPage(new Page<>(pageNo, pageSize),
                Wrappers.<KnowledgeVersionEntity>lambdaQuery()
                        .eq(KnowledgeVersionEntity::getKnowledgeId, knowledgeId)
                        .orderByDesc(KnowledgeVersionEntity::getVersion));
        List<KnowledgeVersionVO> records = page.getRecords().stream()
                .map(v -> KnowledgeVersionVO.builder()
                        .version(v.getVersion()).title(v.getTitle())
                        .content(v.getContent()).createdAt(v.getCreatedAt()).build())
                .toList();
        return ContentPageResult.<KnowledgeVersionVO>builder()
                .total(page.getTotal()).pageNo(page.getCurrent()).pageSize(page.getSize()).records(records).build();
    }

    /** 查询当前空间与作者名下的知识，不存在或越权 404。 */
    private KnowledgeEntity getOwned(Long knowledgeId) {
        KnowledgeEntity entity = knowledgeMapper.selectOne(Wrappers.<KnowledgeEntity>lambdaQuery()
                .eq(KnowledgeEntity::getId, knowledgeId)
                .eq(KnowledgeEntity::getWorkspaceId, requireWorkspaceId())
                .eq(KnowledgeEntity::getAuthorId, requireUserId()));
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识不存在");
        }
        return entity;
    }

    /** 仅构思/草稿可编辑（PRODUCT §4：已发布版本不可修改）。 */
    private void checkEditable(KnowledgeEntity entity) {
        KnowledgeStatus status = KnowledgeStatus.of(entity.getStatus());
        if (status != KnowledgeStatus.IDEA && status != KnowledgeStatus.DRAFT) {
            throw new BizException(ErrorCode.CONFLICT, "当前状态不可编辑（已发布版本不可修改）");
        }
    }

    /**
     * 版本快照（F-0303 历史版本）：落库后调用，记录本次保存后的标题/正文与版本号
     * （MyBatis-Plus @Version 插件 updateById 后会把新版本号回写实体）。
     */
    private void saveVersionSnapshot(KnowledgeEntity entity) {
        KnowledgeVersionEntity version = new KnowledgeVersionEntity();
        version.setId(IdUtil.getSnowflakeNextId());
        version.setWorkspaceId(entity.getWorkspaceId());
        version.setKnowledgeId(entity.getId());
        version.setVersion(entity.getVersion());
        version.setTitle(entity.getTitle());
        version.setContent(entity.getContent());
        version.setCreatedAt(LocalDateTime.now());
        knowledgeVersionMapper.insert(version);
    }

    private Long requireWorkspaceId() {
        Long workspaceId = WorkspaceContext.workspaceId();
        if (workspaceId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return workspaceId;
    }

    private Long requireUserId() {
        Long userId = WorkspaceContext.userId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return userId;
    }

    private KnowledgeVO toVO(KnowledgeEntity entity) {
        return KnowledgeVO.builder()
                .id(entity.getId()).title(entity.getTitle()).content(entity.getContent())
                .kbId(entity.getKbId()).directoryId(entity.getDirectoryId())
                .tags(entity.getTags()).status(entity.getStatus())
                .version(entity.getVersion()).viewCount(entity.getViewCount())
                .createdAt(entity.getCreatedAt()).updatedAt(entity.getUpdatedAt()).build();
    }

    private KnowledgeListItemVO toListItem(KnowledgeEntity entity) {
        return KnowledgeListItemVO.builder()
                .id(entity.getId()).title(entity.getTitle())
                .kbId(entity.getKbId()).directoryId(entity.getDirectoryId())
                .tags(entity.getTags()).status(entity.getStatus())
                .version(entity.getVersion()).viewCount(entity.getViewCount()).updatedAt(entity.getUpdatedAt()).build();
    }
}
