package com.calwen.xlumen.notification.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 站内消息列表分页视图：附带未读数，供前端铃铛角标一次取齐。
 *
 * @author calwen
 * @date 2026/8/24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPageVO {

    /** 总记录数。 */
    private long total;

    /** 当前页数据。 */
    private List<NotificationVO> records;

    /** 未读数量。 */
    private long unreadCount;
}