package com.calwen.xlumen.knowledge.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.knowledge.api.dto.IndexRequestDTO;
import com.calwen.xlumen.knowledge.config.KnowledgeAiProperties;
import com.calwen.xlumen.knowledge.dto.Chunk;
import com.calwen.xlumen.knowledge.entity.KbChunkEntity;
import com.calwen.xlumen.knowledge.entity.KbIndexVersionEntity;
import com.calwen.xlumen.knowledge.mapper.KbChunkMapper;
import com.calwen.xlumen.knowledge.mapper.KbIndexVersionMapper;
import com.calwen.xlumen.knowledge.service.ChunkingService;
import com.calwen.xlumen.knowledge.service.EmbeddingService;
import com.calwen.xlumen.knowledge.service.IndexPipelineService;
import com.calwen.xlumen.knowledge.service.VectorStore;
import com.calwen.xlumen.knowledge.vo.IndexStatusVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * 索引流水线实现（F-0402/F-0403）：清洗→切片→幂等检查→Embedding→清旧向量→写新向量→
 * 写切片元数据→激活新版本→旧版本置 STALE。幂等：正文 hash 相同且已有 ACTIVATING/ACTIVE
 * 版本则跳过；Embedding/写向量失败标记索引任务失败（STALE），不影响发布本身。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Slf4j
@Service
public class IndexPipelineServiceImpl implements IndexPipelineService {

    /** 索引版本状态：索引中。 */
    public static final String STATUS_ACTIVATING = "ACTIVATING";
    /** 索引版本状态：已激活。 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 索引版本状态：已失效。 */
    public static final String STATUS_STALE = "STALE";
    /** 向量索引名（Milvus 集合名）。 */
    private static final String INDEX_NAME = MilvusVectorStore.COLLECTION_NAME;

    @Resource
    private ChunkingService chunkingService;
    @Resource
    private EmbeddingService embeddingService;
    @Resource
    private VectorStore vectorStore;
    @Resource
    private KbChunkMapper kbChunkMapper;
    @Resource
    private KbIndexVersionMapper kbIndexVersionMapper;
    @Resource
    private KnowledgeAiProperties aiProperties;

    @Override
    public void indexKnowledge(IndexRequestDTO request) {
        // 1. 清洗
        String content = clean(request.getContent());
        if (StrUtil.isBlank(content)) {
            log.warn("正文为空，跳过索引：knowledgeId={}, version={}", request.getKnowledgeId(), request.getVersion());
            return;
        }
        // 2. 切片
        List<Chunk> chunks = chunkingService.chunk(content);
        if (chunks.isEmpty()) {
            log.warn("切片为空，跳过索引：knowledgeId={}, version={}", request.getKnowledgeId(), request.getVersion());
            return;
        }
        // 3. 幂等检查：正文 hash 相同且已有 ACTIVATING/ACTIVE 版本则跳过
        String contentHash = sha256(content);
        if (alreadyIndexed(request, contentHash)) {
            log.info("正文未变化，跳过重复索引：knowledgeId={}, version={}", request.getKnowledgeId(), request.getVersion());
            return;
        }
        // 4. 写 ACTIVATING 版本记录（占位，失败可追溯）
        KbIndexVersionEntity versionEntity = createActivatingVersion(request);
        try {
            // 5. Embedding（32 片/批）
            List<List<Float>> embeddings = embeddingService.embed(
                    chunks.stream().map(Chunk::getChunkText).toList());
            for (int i = 0; i < chunks.size(); i++) {
                chunks.get(i).setEmbedding(embeddings.get(i));
            }
            // 6. 清旧版本向量 → 写新版本向量（delete 按知识全量删除，等价于删除旧版本向量）
            vectorStore.delete(request.getWorkspaceId(), request.getKnowledgeId());
            vectorStore.index(request, chunks);
            // 7. 写切片元数据
            persistChunks(request, chunks, contentHash);
            // 8. 激活新版本
            activate(versionEntity);
            // 9. 旧版本置 STALE（向量已在上一步清理）
            staleOldVersions(request, versionEntity.getId());
        } catch (Exception e) {
            markFailed(versionEntity, e);
            throw e;
        }
    }

    @Override
    public void removeKnowledge(Long workspaceId, Long knowledgeId) {
        vectorStore.delete(workspaceId, knowledgeId);
        kbChunkMapper.update(null, Wrappers.<KbChunkEntity>lambdaUpdate()
                .eq(KbChunkEntity::getWorkspaceId, workspaceId)
                .eq(KbChunkEntity::getKnowledgeId, knowledgeId)
                .set(KbChunkEntity::getStatus, 0));
        kbIndexVersionMapper.update(null, Wrappers.<KbIndexVersionEntity>lambdaUpdate()
                .eq(KbIndexVersionEntity::getWorkspaceId, workspaceId)
                .eq(KbIndexVersionEntity::getKnowledgeId, knowledgeId)
                .ne(KbIndexVersionEntity::getStatus, STATUS_STALE)
                .set(KbIndexVersionEntity::getStatus, STATUS_STALE)
                .set(KbIndexVersionEntity::getUpdatedAt, LocalDateTime.now()));
    }

    @Override
    public IndexStatusVO getIndexStatus(Long workspaceId, Long knowledgeId) {
        KbIndexVersionEntity latest = kbIndexVersionMapper.selectOne(Wrappers.<KbIndexVersionEntity>lambdaQuery()
                .eq(KbIndexVersionEntity::getWorkspaceId, workspaceId)
                .eq(KbIndexVersionEntity::getKnowledgeId, knowledgeId)
                .orderByDesc(KbIndexVersionEntity::getUpdatedAt)
                .last("LIMIT 1"));
        if (latest == null) {
            return null;
        }
        Long chunkCount = kbChunkMapper.selectCount(Wrappers.<KbChunkEntity>lambdaQuery()
                .eq(KbChunkEntity::getWorkspaceId, workspaceId)
                .eq(KbChunkEntity::getKnowledgeId, knowledgeId)
                .eq(KbChunkEntity::getVersion, latest.getVersion())
                .eq(KbChunkEntity::getStatus, 1));
        return IndexStatusVO.builder()
                .knowledgeId(knowledgeId)
                .version(latest.getVersion())
                .status(latest.getStatus())
                .chunkCount(chunkCount == null ? 0 : chunkCount.intValue())
                .indexedAt(latest.getUpdatedAt())
                .build();
    }

    /** 正文清洗：统一换行并去除首尾空白。 */
    private String clean(String content) {
        if (content == null) {
            return "";
        }
        return content.replace("\r\n", "\n").trim();
    }

    /** 幂等检查：同 hash 已落库且对应版本仍为 ACTIVATING/ACTIVE 则跳过。 */
    private boolean alreadyIndexed(IndexRequestDTO request, String contentHash) {
        KbChunkEntity existing = kbChunkMapper.selectOne(Wrappers.<KbChunkEntity>lambdaQuery()
                .eq(KbChunkEntity::getWorkspaceId, request.getWorkspaceId())
                .eq(KbChunkEntity::getKnowledgeId, request.getKnowledgeId())
                .eq(KbChunkEntity::getContentHash, contentHash)
                .last("LIMIT 1"));
        if (existing == null) {
            return false;
        }
        Long activeCount = kbIndexVersionMapper.selectCount(Wrappers.<KbIndexVersionEntity>lambdaQuery()
                .eq(KbIndexVersionEntity::getWorkspaceId, request.getWorkspaceId())
                .eq(KbIndexVersionEntity::getKnowledgeId, request.getKnowledgeId())
                .eq(KbIndexVersionEntity::getVersion, existing.getVersion())
                .in(KbIndexVersionEntity::getStatus, STATUS_ACTIVATING, STATUS_ACTIVE));
        return activeCount != null && activeCount > 0;
    }

    /** 创建 ACTIVATING 版本记录。 */
    private KbIndexVersionEntity createActivatingVersion(IndexRequestDTO request) {
        KbIndexVersionEntity entity = new KbIndexVersionEntity();
        entity.setId(IdUtil.getSnowflakeNextId());
        entity.setWorkspaceId(request.getWorkspaceId());
        entity.setKnowledgeId(request.getKnowledgeId());
        entity.setVersion(request.getVersion());
        entity.setIndexName(INDEX_NAME);
        entity.setEmbeddingModel(aiProperties.getBailianModelEmbedding());
        entity.setStatus(STATUS_ACTIVATING);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        kbIndexVersionMapper.insert(entity);
        return entity;
    }

    /** 写切片元数据：vector_id 在 Milvus 模式回填条目 ID，Noop 降级留空。 */
    private void persistChunks(IndexRequestDTO request, List<Chunk> chunks, String contentHash) {
        boolean milvusActive = vectorStore instanceof MilvusVectorStore;
        for (Chunk chunk : chunks) {
            KbChunkEntity entity = new KbChunkEntity();
            entity.setId(IdUtil.getSnowflakeNextId());
            entity.setWorkspaceId(request.getWorkspaceId());
            entity.setKnowledgeId(request.getKnowledgeId());
            entity.setVersion(request.getVersion());
            entity.setChunkSeq(chunk.getSeq());
            entity.setHeadingAnchor(StrUtil.blankToDefault(chunk.getHeadingAnchor(), ""));
            entity.setContentHash(contentHash);
            entity.setVectorId(milvusActive
                    ? MilvusVectorStore.vectorId(request.getKnowledgeId(), request.getVersion(), chunk.getSeq()) : null);
            entity.setChunkText(chunk.getChunkText());
            entity.setStatus(1);
            entity.setCreatedAt(LocalDateTime.now());
            kbChunkMapper.insert(entity);
        }
    }

    /** 激活新版本（ACTIVATING → ACTIVE）。 */
    private void activate(KbIndexVersionEntity entity) {
        entity.setStatus(STATUS_ACTIVE);
        entity.setUpdatedAt(LocalDateTime.now());
        kbIndexVersionMapper.updateById(entity);
    }

    /** 其余非 STALE 版本置 STALE（向量已清空，仅保留版本历史指针）。 */
    private void staleOldVersions(IndexRequestDTO request, Long currentId) {
        List<KbIndexVersionEntity> oldVersions = kbIndexVersionMapper.selectList(
                Wrappers.<KbIndexVersionEntity>lambdaQuery()
                        .eq(KbIndexVersionEntity::getWorkspaceId, request.getWorkspaceId())
                        .eq(KbIndexVersionEntity::getKnowledgeId, request.getKnowledgeId())
                        .ne(KbIndexVersionEntity::getId, currentId)
                        .ne(KbIndexVersionEntity::getStatus, STATUS_STALE));
        for (KbIndexVersionEntity old : oldVersions) {
            old.setStatus(STATUS_STALE);
            old.setUpdatedAt(LocalDateTime.now());
            kbIndexVersionMapper.updateById(old);
        }
    }

    /** 索引失败：ACTIVATING 记录置 STALE，供管理面查询重试。 */
    private void markFailed(KbIndexVersionEntity entity, Exception e) {
        log.error("索引任务失败：knowledgeId={}, version={}", entity.getKnowledgeId(), entity.getVersion(), e);
        try {
            entity.setStatus(STATUS_STALE);
            entity.setUpdatedAt(LocalDateTime.now());
            kbIndexVersionMapper.updateById(entity);
        } catch (Exception updateEx) {
            log.error("索引失败状态回写失败", updateEx);
        }
    }

    /** SHA-256 十六进制摘要（幂等键）。 */
    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "服务暂时不可用");
        }
    }
}
