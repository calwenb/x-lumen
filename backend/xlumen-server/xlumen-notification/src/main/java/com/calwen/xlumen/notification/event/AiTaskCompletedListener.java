package com.calwen.xlumen.notification.event;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.calwen.xlumen.common.event.AiTaskCompletedEvent;
import com.calwen.xlumen.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * AI 任务完结事件监听：REVIEWER 场景任务完结时产生站内消息——
 * COMPLETED 按 severity 汇总（存在 error → 未通过；否则通过）；FAILED 提醒重试。
 * 消息链接指向审核中心（/studio/review）；知识标题/ID 取自任务入参快照。
 *
 * @author calwen
 * @date 2026/8/24
 */
@Component
public class AiTaskCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(AiTaskCompletedListener.class);

    /** 审核中心路由。 */
    private static final String REVIEW_CENTER_LINK = "/studio/review";

    private final NotificationService notificationService;

    public AiTaskCompletedListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @EventListener
    public void onTaskCompleted(AiTaskCompletedEvent event) {
        if (event == null || event.getUserId() == null || !"REVIEWER".equals(event.getScene())) {
            return;
        }
        try {
            JSONObject input = parseObj(event.getInputJson());
            String title = input.getStr("title");
            String knowledgeTitle = StrUtil.isBlank(title) ? "知识" : title.trim();
            String content;
            if ("COMPLETED".equals(event.getStatus())) {
                content = completedContent(knowledgeTitle, event.getResultJson());
            } else {
                content = "《" + knowledgeTitle + "》的 AI 审核任务执行失败"
                        + (StrUtil.isBlank(event.getErrorMsg()) ? "" : "：" + event.getErrorMsg())
                        + "。请检查模型配置后重试。";
            }
            notificationService.create(event.getWorkspaceId(), event.getUserId(), "REVIEW",
                    completedTitle(event.getStatus(), event.getResultJson()), content, REVIEW_CENTER_LINK);
        } catch (Exception e) {
            log.warn("AI 审核完成通知创建失败 taskId={}", event.getTaskId(), e);
        }
    }

    /** 标题：未通过/通过/失败。 */
    private String completedTitle(String status, String resultJson) {
        if (!"COMPLETED".equals(status)) {
            return "AI 审核失败";
        }
        return countSeverity(resultJson, "error") > 0 ? "AI 审核未通过" : "AI 审核通过";
    }

    /** 内容：未通过列出高危条数；通过汇总建议条数（自动模式已由发布链路自动发布，文案中性不重复承诺）。 */
    private String completedContent(String title, String resultJson) {
        long errors = countSeverity(resultJson, "error");
        if (errors > 0) {
            return "《" + title + "》存在 " + errors + " 条高危问题，已回退为草稿，请前往审核中心查看并修改后重新提交。";
        }
        long suggestions = countSeverity(resultJson, "warning") + countSeverity(resultJson, "info");
        return "《" + title + "》已通过 AI 审核（共 " + suggestions + " 条优化建议）。";
    }

    /** 统计某严重度条数（result_json 为审校问题数组）。 */
    private long countSeverity(String resultJson, String severity) {
        if (StrUtil.isBlank(resultJson)) {
            return 0;
        }
        try {
            JSONArray arr = JSONUtil.parseArray(resultJson);
            long count = 0;
            for (Object o : arr) {
                if (o instanceof JSONObject item && severity.equals(item.getStr("severity"))) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    private JSONObject parseObj(String json) {
        if (StrUtil.isBlank(json)) {
            return JSONUtil.createObj();
        }
        try {
            return JSONUtil.parseObj(json);
        } catch (Exception e) {
            return JSONUtil.createObj();
        }
    }
}