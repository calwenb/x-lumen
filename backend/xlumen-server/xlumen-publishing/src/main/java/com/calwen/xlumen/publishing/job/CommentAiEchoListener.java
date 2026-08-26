package com.calwen.xlumen.publishing.job;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.api.dto.SearchRequestDTO;
import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;
import com.calwen.xlumen.publishing.dto.CommentCitationVO;
import com.calwen.xlumen.publishing.entity.CommentEntity;
import com.calwen.xlumen.publishing.event.CommentAiEchoRequestedEvent;
import com.calwen.xlumen.publishing.mapper.CommentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 小光评论回复监听：消费 {@link CommentAiEchoRequestedEvent}，同步生成 AI 回复（进程内事件，
 * 发表评论链路同线程执行）。V2 先走「检索摘要」形态——不调模型，按评论问题检索知识片段并拼接组文，
 * 命中片段落 citations_json 引用溯源；检索为空返回兜底文案。模型生成留待提示词管配后升级。
 * 兜底原则：生成/落库/限流异常一律捕获不抛出，绝不阻断评论主流程。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Component
public class CommentAiEchoListener {

    private static final Logger log = LoggerFactory.getLogger(CommentAiEchoListener.class);

    /** 限流键：workspaceId/userId/knowledgeId（同一用户对同一知识 5 分钟内最多 1 次）。 */
    private static final String RATE_KEY = "xlumen:commentai:%d:%d:%d";

    /** 限流窗口：5 分钟。 */
    private static final Duration RATE_WINDOW = Duration.ofMinutes(5);

    /** 检索返回条数上限。 */
    private static final int TOP_K = 5;

    private static final int STATUS_NORMAL = 1;

    private static final int FLAG_AI = 1;

    /** 小光系统回复的用户 ID 约定（IAM 用户表无此 ID，仅作 is_ai 行归属标记）。 */
    private static final long AI_SYSTEM_USER_ID = 0L;

    private static final String AI_USER_NAME = "小光";

    private static final String REPLY_PREFIX = "基于知识库的回答：";

    private static final String REPLY_FALLBACK = "库内暂时没有找到相关内容，换个问法试试";

    /** 回复内容列上限（eng_comment.content VARCHAR(1000)），拼接后截断防插入失败。 */
    private static final int CONTENT_MAX_LENGTH = 1000;

    private final CommentMapper commentMapper;
    private final KnowledgeApi knowledgeApi;
    private final StringRedisTemplate stringRedisTemplate;

    public CommentAiEchoListener(CommentMapper commentMapper, KnowledgeApi knowledgeApi,
                                 StringRedisTemplate stringRedisTemplate) {
        this.commentMapper = commentMapper;
        this.knowledgeApi = knowledgeApi;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @EventListener
    public void onAiEchoRequested(CommentAiEchoRequestedEvent event) {
        if (event == null || event.getCommentId() == null || event.getWorkspaceId() == null
                || event.getKnowledgeId() == null || StrUtil.isBlank(event.getCommentContent())) {
            return;
        }
        try {
            if (!tryAcquire(event)) {
                log.debug("小光回复限流命中，静默忽略 commentId={}", event.getCommentId());
                return;
            }
            List<SearchResultDTO> hits = search(event);
            insertAiReply(event, buildReply(hits), citationsJson(hits));
        } catch (Exception e) {
            log.warn("小光评论回复生成失败 commentId={}", event.getCommentId(), e);
        }
    }

    /** 限流：setIfAbsent 抢窗口（5 分钟），已存在=超限放行阻止；Redis 异常降级放行。 */
    private boolean tryAcquire(CommentAiEchoRequestedEvent event) {
        long userId = event.getUserId() == null ? 0L : event.getUserId();
        String key = String.format(RATE_KEY, event.getWorkspaceId(), userId, event.getKnowledgeId());
        try {
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RATE_WINDOW);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.warn("小光回复限流降级放行 key={}", key, e);
            return true;
        }
    }

    /** 按评论问题检索知识片段：workspaceId 传 null（跨空间公开知识可见性由 kbIds+article_id 精确表达，
     *  避免评论者自身工作区过滤掉他库公开内容）；限定评论所属知识 + 触发用户可见库集合。 */
    private List<SearchResultDTO> search(CommentAiEchoRequestedEvent event) {
        List<Long> visibleKbIds = knowledgeApi.resolveVisibleKbIds(event.getUserId());
        if (visibleKbIds == null || visibleKbIds.isEmpty()) {
            return List.of();
        }
        SearchRequestDTO request = SearchRequestDTO.builder()
                .query(event.getCommentContent())
                .kbIds(visibleKbIds)
                .knowledgeId(event.getKnowledgeId())
                .topK(TOP_K)
                .build();
        List<SearchResultDTO> hits = knowledgeApi.search(request);
        return hits == null ? List.of() : hits;
    }

    /** 组文：前缀 + 命中片段按 chunk_text 拼接（空片段过滤，超长截断到列上限）。 */
    private String buildReply(List<SearchResultDTO> hits) {
        String fragments = hits.stream()
                .map(SearchResultDTO::getChunkText)
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining("\n\n"));
        if (StrUtil.isBlank(fragments)) {
            return REPLY_FALLBACK;
        }
        return truncateReply(REPLY_PREFIX + "\n\n" + fragments);
    }

    /** 引用溯源：命中片段结构化 JSON 数组（knowledgeId/title/headingAnchor/chunkText/score）。 */
    private String citationsJson(List<SearchResultDTO> hits) {
        if (hits.isEmpty()) {
            return "[]";
        }
        List<CommentCitationVO> citations = hits.stream()
                .map(h -> CommentCitationVO.builder()
                        .knowledgeId(h.getKnowledgeId())
                        .title(h.getTitle())
                        .headingAnchor(h.getHeadingAnchor())
                        .chunkText(h.getChunkText())
                        .score(h.getScore())
                        .build())
                .toList();
        return JSONUtil.toJsonStr(citations);
    }

    /** 小光回复落库：parent_id=触发评论，is_ai=1，user 标记为系统小光。 */
    private void insertAiReply(CommentAiEchoRequestedEvent event, String reply, String citationsJson) {
        CommentEntity replyEntity = new CommentEntity();
        replyEntity.setId(IdUtil.getSnowflakeNextId());
        replyEntity.setWorkspaceId(event.getWorkspaceId());
        replyEntity.setKnowledgeId(event.getKnowledgeId());
        replyEntity.setUserId(AI_SYSTEM_USER_ID);
        replyEntity.setUserName(AI_USER_NAME);
        replyEntity.setParentId(event.getCommentId());
        replyEntity.setContent(reply);
        replyEntity.setIsAi(FLAG_AI);
        replyEntity.setCitationsJson(citationsJson);
        replyEntity.setStatus(STATUS_NORMAL);
        replyEntity.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(replyEntity);
    }

    private String truncateReply(String text) {
        return text.length() <= CONTENT_MAX_LENGTH ? text : text.substring(0, CONTENT_MAX_LENGTH);
    }
}