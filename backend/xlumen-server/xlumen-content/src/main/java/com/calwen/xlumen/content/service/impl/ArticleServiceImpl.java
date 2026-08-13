package com.calwen.xlumen.content.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.content.api.dto.ContentPageResult;
import com.calwen.xlumen.content.dto.ArticleListQueryDTO;
import com.calwen.xlumen.content.dto.CreateArticleDTO;
import com.calwen.xlumen.content.dto.DraftSaveDTO;
import com.calwen.xlumen.content.dto.UpdateArticleDTO;
import com.calwen.xlumen.content.entity.ArticleEntity;
import com.calwen.xlumen.content.enums.ArticleStatus;
import com.calwen.xlumen.content.mapper.ArticleMapper;
import com.calwen.xlumen.content.service.ArticleService;
import com.calwen.xlumen.content.vo.ArticleListItemVO;
import com.calwen.xlumen.content.vo.ArticleVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 文章服务实现（F-0301/F-0302/F-0307）：作者与空间来自 WorkspaceContext（JWT claims，F-0104 双层校验第二层）。
 * 版本乐观锁：MyBatis-Plus @Version 插件，updateById 影响行数 0 即冲突（HTTP 409，PRODUCT §6 禁止静默覆盖）。
 * 已发布版本正文不可修改（PRODUCT §4），修改需走旧文更新闭环（V2 F-1105）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Service
public class ArticleServiceImpl implements ArticleService {

    /** 可见性：公开（F-0307）。 */
    private static final int VISIBILITY_PUBLIC = 1;
    /** 自动保存新建草稿的默认标题。 */
    private static final String UNTITLED = "未命名草稿";

    @Resource
    private ArticleMapper articleMapper;

    @Override
    public ArticleVO create(CreateArticleDTO dto) {
        ArticleEntity entity = new ArticleEntity();
        entity.setWorkspaceId(requireWorkspaceId());
        entity.setAuthorId(requireUserId());
        entity.setAuthorName(WorkspaceContext.username());
        entity.setTitle(dto.getTitle());
        entity.setContent(dto.getContent() == null ? "" : dto.getContent());
        entity.setCategory(StrUtil.nullToEmpty(dto.getCategory()));
        entity.setTags(dto.getTags());
        entity.setStatus(ArticleStatus.DRAFT.getValue());
        entity.setVisibility(dto.getVisibility() == null ? VISIBILITY_PUBLIC : dto.getVisibility());
        entity.setVersion(0L);
        entity.setViewCount(0L);
        entity.setPublishedAt(null);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        articleMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    public ArticleVO update(Long articleId, UpdateArticleDTO dto) {
        ArticleEntity entity = getOwned(articleId);
        checkEditable(entity);
        entity.setTitle(dto.getTitle());
        entity.setContent(dto.getContent() == null ? "" : dto.getContent());
        entity.setCategory(StrUtil.nullToEmpty(dto.getCategory()));
        entity.setTags(dto.getTags());
        if (dto.getVisibility() != null) {
            entity.setVisibility(dto.getVisibility());
        }
        // 乐观锁：仅当携带版本号与当前一致才允许更新
        entity.setVersion(dto.getVersion());
        if (articleMapper.updateById(entity) == 0) {
            throw new BizException(ErrorCode.CONFLICT, "文章已被修改，请刷新后重试");
        }
        return toVO(getOwned(articleId));
    }

    @Override
    public ArticleVO autosave(DraftSaveDTO dto) {
        if (dto.getArticleId() == null) {
            CreateArticleDTO create = CreateArticleDTO.builder()
                    .title(StrUtil.blankToDefault(dto.getTitle(), UNTITLED))
                    .content(dto.getContent()).category(dto.getCategory())
                    .tags(dto.getTags()).visibility(dto.getVisibility()).build();
            return create(create);
        }
        ArticleEntity entity = getOwned(dto.getArticleId());
        checkEditable(entity);
        String newContent = dto.getContent() == null ? "" : dto.getContent();
        // 幂等：内容/标题均未变化时跳过写库（前端节流触发，避免无效版本增长）
        if (Objects.equals(entity.getContent(), newContent)
                && Objects.equals(entity.getTitle(), dto.getTitle())) {
            return toVO(entity);
        }
        entity.setTitle(StrUtil.blankToDefault(dto.getTitle(), entity.getTitle()));
        entity.setContent(newContent);
        if (dto.getCategory() != null) {
            entity.setCategory(dto.getCategory());
        }
        if (dto.getTags() != null) {
            entity.setTags(dto.getTags());
        }
        if (dto.getVisibility() != null) {
            entity.setVisibility(dto.getVisibility());
        }
        entity.setVersion(dto.getVersion() == null ? entity.getVersion() : dto.getVersion());
        if (articleMapper.updateById(entity) == 0) {
            throw new BizException(ErrorCode.CONFLICT, "草稿已被修改，请刷新后重试");
        }
        return toVO(getOwned(dto.getArticleId()));
    }

    @Override
    public ArticleVO get(Long articleId) {
        return toVO(getOwned(articleId));
    }

    @Override
    public ContentPageResult<ArticleListItemVO> list(ArticleListQueryDTO query) {
        long pageNo = Math.max(1, query.getPageNo());
        long pageSize = Math.min(100, Math.max(1, query.getPageSize()));
        Page<ArticleEntity> page = articleMapper.selectPage(new Page<>(pageNo, pageSize),
                Wrappers.<ArticleEntity>lambdaQuery()
                        .eq(ArticleEntity::getWorkspaceId, requireWorkspaceId())
                        .eq(ArticleEntity::getAuthorId, requireUserId())
                        .eq(query.getStatus() != null, ArticleEntity::getStatus, query.getStatus())
                        .eq(query.getVisibility() != null, ArticleEntity::getVisibility, query.getVisibility())
                        .like(StrUtil.isNotBlank(query.getKeyword()), ArticleEntity::getTitle, query.getKeyword())
                        .orderByDesc(ArticleEntity::getUpdatedAt));
        List<ArticleListItemVO> records = page.getRecords().stream().map(this::toListItem).toList();
        return ContentPageResult.<ArticleListItemVO>builder()
                .total(page.getTotal()).pageNo(page.getCurrent()).pageSize(page.getSize()).records(records).build();
    }

    @Override
    public void delete(Long articleId) {
        ArticleEntity entity = getOwned(articleId);
        ArticleStatus status = ArticleStatus.of(entity.getStatus());
        if (status != ArticleStatus.IDEA && status != ArticleStatus.DRAFT) {
            throw new BizException(ErrorCode.CONFLICT, "仅构思/草稿可删除，已发布文章请先下架");
        }
        articleMapper.deleteById(entity.getId());
    }

    /** 查询当前空间与作者名下的文章，不存在或越权 404。 */
    private ArticleEntity getOwned(Long articleId) {
        ArticleEntity entity = articleMapper.selectOne(Wrappers.<ArticleEntity>lambdaQuery()
                .eq(ArticleEntity::getId, articleId)
                .eq(ArticleEntity::getWorkspaceId, requireWorkspaceId())
                .eq(ArticleEntity::getAuthorId, requireUserId()));
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        return entity;
    }

    /** 仅构思/草稿可编辑（PRODUCT §4：已发布版本不可修改）。 */
    private void checkEditable(ArticleEntity entity) {
        ArticleStatus status = ArticleStatus.of(entity.getStatus());
        if (status != ArticleStatus.IDEA && status != ArticleStatus.DRAFT) {
            throw new BizException(ErrorCode.CONFLICT, "当前状态不可编辑（已发布版本不可修改）");
        }
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

    private ArticleVO toVO(ArticleEntity entity) {
        return ArticleVO.builder()
                .id(entity.getId()).title(entity.getTitle()).content(entity.getContent())
                .category(entity.getCategory()).tags(entity.getTags())
                .visibility(entity.getVisibility()).status(entity.getStatus())
                .version(entity.getVersion()).viewCount(entity.getViewCount())
                .createdAt(entity.getCreatedAt()).updatedAt(entity.getUpdatedAt()).build();
    }

    private ArticleListItemVO toListItem(ArticleEntity entity) {
        return ArticleListItemVO.builder()
                .id(entity.getId()).title(entity.getTitle()).category(entity.getCategory())
                .tags(entity.getTags()).visibility(entity.getVisibility()).status(entity.getStatus())
                .version(entity.getVersion()).viewCount(entity.getViewCount()).updatedAt(entity.getUpdatedAt()).build();
    }
}
