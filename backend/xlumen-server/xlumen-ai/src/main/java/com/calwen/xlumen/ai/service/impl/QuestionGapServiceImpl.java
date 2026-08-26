package com.calwen.xlumen.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.calwen.xlumen.ai.entity.QuestionGapEntity;
import com.calwen.xlumen.ai.mapper.QuestionGapMapper;
import com.calwen.xlumen.ai.service.QuestionGapService;
import com.calwen.xlumen.ai.vo.QuestionGapPageVO;
import com.calwen.xlumen.ai.vo.QuestionGapVO;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 问答知识缺口实现：question_gap 记录（来源区分未命中引用/建议追问），
 * 分页时间倒序，置为已处理需工作空间归属一致。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Service
public class QuestionGapServiceImpl implements QuestionGapService {

    private final QuestionGapMapper questionGapMapper;

    public QuestionGapServiceImpl(QuestionGapMapper questionGapMapper) {
        this.questionGapMapper = questionGapMapper;
    }

    @Override
    public void recordUnmatched(Long workspaceId, Long userId, Long conversationId, String question) {
        if (workspaceId == null || userId == null || StrUtil.isBlank(question)) {
            return;
        }
        insert(workspaceId, userId, conversationId, question.trim(), SOURCE_ANSWER_UNMATCHED);
    }

    @Override
    public void recordFollowups(Long workspaceId, Long userId, Long conversationId, List<String> followups) {
        if (workspaceId == null || userId == null || followups == null || followups.isEmpty()) {
            return;
        }
        for (String followup : followups) {
            if (StrUtil.isNotBlank(followup)) {
                insert(workspaceId, userId, conversationId, followup.trim(), SOURCE_FOLLOWUP_SUGGESTED);
            }
        }
    }

    @Override
    public QuestionGapPageVO page(Long workspaceId, String status, long pageNo, long pageSize) {
        long size = Math.min(Math.max(pageSize, 1), 100);
        long offset = Math.max(pageNo - 1, 0) * size;
        LambdaQueryWrapper<QuestionGapEntity> query = new LambdaQueryWrapper<QuestionGapEntity>()
                .eq(workspaceId != null, QuestionGapEntity::getWorkspaceId, workspaceId)
                .eq(StrUtil.isNotBlank(status), QuestionGapEntity::getStatus, status);
        long total = questionGapMapper.selectCount(query.clone());
        List<QuestionGapEntity> rows = questionGapMapper.selectList(query.clone()
                .orderByDesc(QuestionGapEntity::getCreatedAt)
                .last("LIMIT " + offset + "," + size));
        return QuestionGapPageVO.builder()
                .records(rows.stream().map(this::toVO).toList())
                .total(total)
                .build();
    }

    @Override
    public void markHandled(Long workspaceId, Long id) {
        QuestionGapEntity entity = questionGapMapper.selectById(id);
        if (entity == null || !workspaceId.equals(entity.getWorkspaceId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "缺口记录不存在");
        }
        if (STATUS_HANDLED.equals(entity.getStatus())) {
            return;
        }
        entity.setStatus(STATUS_HANDLED);
        questionGapMapper.updateById(entity);
    }

    private void insert(Long workspaceId, Long userId, Long conversationId, String question, String source) {
        QuestionGapEntity entity = new QuestionGapEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setUserId(userId);
        entity.setConversationId(conversationId);
        entity.setQuestion(StrUtil.sub(question, 0, 1000));
        entity.setSource(source);
        entity.setStatus(STATUS_UNHANDLED);
        questionGapMapper.insert(entity);
    }

    private QuestionGapVO toVO(QuestionGapEntity e) {
        return QuestionGapVO.builder()
                .id(e.getId())
                .conversationId(e.getConversationId())
                .question(e.getQuestion())
                .source(e.getSource())
                .status(e.getStatus())
                .createdAt(e.getCreatedAt())
                .build();
    }
}