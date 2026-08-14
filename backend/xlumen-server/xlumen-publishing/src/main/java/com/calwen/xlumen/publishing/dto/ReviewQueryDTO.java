package com.calwen.xlumen.publishing.dto;

import com.calwen.xlumen.common.dto.PageQueryDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 审核列表查询参数（F-0902）：状态筛选，分页参数继承 {@link PageQueryDTO}（默认值即接口默认值）。
 * Spring MVC 自动绑定 GET 查询参数。
 *
 * @author calwen
 * @date 2026/8/14
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReviewQueryDTO extends PageQueryDTO {

    /** 状态筛选（ReviewStatus 值，可空 = 全部）。 */
    private String status;
}
