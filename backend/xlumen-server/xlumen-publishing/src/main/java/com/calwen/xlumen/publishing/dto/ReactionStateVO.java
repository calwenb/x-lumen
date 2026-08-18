package com.calwen.xlumen.publishing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 反应状态视图（F-0212/F-0213）：toggle 后当前用户对资源的活动反应。
 * LIKE=已点赞、DISLIKE=已点踩、NONE=无活动反应（已取消）。
 *
 * @author calwen
 * @date 2026/8/18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactionStateVO {

    /** 反应：LIKE|DISLIKE|NONE。 */
    private String reaction;

    /** 点赞（reaction_type=1）。 */
    public static final String LIKE = "LIKE";

    /** 点踩（reaction_type=2）。 */
    public static final String DISLIKE = "DISLIKE";

    /** 无活动反应（已取消）。 */
    public static final String NONE = "NONE";
}
