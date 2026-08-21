package com.calwen.xlumen.knowledge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检索测试入参（F-0404）：博主后台检索测试接口入参，需登录，检索范围含私有（ALL）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalTestRequestDTO {

    /** 查询文本。 */
    @NotBlank(message = "查询文本不能为空")
    private String query;

    /** 返回条数（默认 10，≤50）：缺省/空值走默认，避免 Jackson 将 null 反序列化为 int 报错（BUG-024）。 */
    @Min(value = 1, message = "返回条数至少为 1")
    @Max(value = 50, message = "返回条数最多为 50")
    private Integer topK;

    /** 返回条数兜底：null 时按默认 10。 */
    public int resolvedTopK() {
        return topK == null ? 10 : topK;
    }
}
