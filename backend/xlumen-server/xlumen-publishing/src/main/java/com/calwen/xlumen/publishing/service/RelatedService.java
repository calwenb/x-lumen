package com.calwen.xlumen.publishing.service;

import com.calwen.xlumen.publishing.dto.RelatedKnowledgeVO;

import java.util.List;

/**
 * 相关知识推荐：同标签候选优先，不足用语义检索兜底补足 5 条。
 *
 * @author calwen
 * @date 2026/8/26
 */
public interface RelatedService {

    List<RelatedKnowledgeVO> related(Long knowledgeId);
}