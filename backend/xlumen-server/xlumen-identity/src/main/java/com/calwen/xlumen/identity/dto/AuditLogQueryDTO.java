package com.calwen.xlumen.identity.dto;

import com.calwen.xlumen.common.dto.PageQueryDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 审计日志查询参数（F-1202）：action 可空（为空查全部），分页参数继承 {@link PageQueryDTO}。
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
public class AuditLogQueryDTO extends PageQueryDTO {

    /** 操作类型筛选（可空 = 全部）。 */
    private String action;
}
