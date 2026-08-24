package com.calwen.xlumen.notification.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 站内消息视图（IDEA-024）：id 转 String 传输（雪花 Long 精度，BACKEND.md §5.3）。
 *
 * @author calwen
 * @date 2026/8/24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationVO {

    /** 消息 ID（字符串传输）。 */
    private String id;

    /** 类型（REVIEW=AI 审核结果等）。 */
    private String type;

    /** 标题。 */
    private String title;

    /** 摘要内容。 */
    private String content;

    /** 跳转链接。 */
    private String link;

    /** 是否已读。 */
    private boolean read;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}