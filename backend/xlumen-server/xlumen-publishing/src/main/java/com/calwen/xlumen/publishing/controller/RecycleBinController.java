package com.calwen.xlumen.publishing.controller;

import com.calwen.xlumen.common.dto.PageQueryDTO;
import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.publishing.dto.RecycleBinPageVO;
import com.calwen.xlumen.publishing.service.RecycleBinFacadeService;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 回收站接口（F-0305，B16 回收站，KB-3 聚合层在 publishing）：知识库/知识统一回收站，双 Tab；
 * 彻底删除为红色操作 + 二次确认（confirm=CONFIRM），恢复/删除幂等。
 * knowledge 模块依赖方向受限（content→ai→knowledge 环），回收站编排收敛到 publishing
 * （同时依赖 content+knowledge，无环），kb 侧委托 KnowledgeApi、knowledge 侧委托 ContentApi。
 *
 * @author calwen
 * @date 2026/8/14
 */
@RestController
@RequestMapping("/api/v1/recycle-bin")
@PreAuthorize("hasRole('OWNER')")
public class RecycleBinController {

    @Resource
    private RecycleBinFacadeService recycleBinFacadeService;

    /**
     * 回收站分页列表（F-0305）：type=kb|knowledge|空=全部，deleted_at 降序。
     *
     * @param type     类型（kb/knowledge，可空=全部）
     * @param pageNo   页码（默认 1）
     * @param pageSize 每页条数（默认 20，上限 100）
     * @return 回收站条目分页
     */
    @GetMapping
    public ApiResponse<RecycleBinPageVO> list(@RequestParam(required = false) String type,
                                              @RequestParam(defaultValue = "1") long pageNo,
                                              @RequestParam(defaultValue = "20") long pageSize) {
        PageQueryDTO query = PageQueryDTO.builder().pageNo(pageNo).pageSize(pageSize).build();
        return ApiResponse.success(recycleBinFacadeService.list(type, query));
    }

    /**
     * 恢复（F-0305）：kb 整体恢复（连带恢复库内知识）；知识恢复含冲突判定
     * （原目录已删→挂库根、原库已彻底删除→409）。
     *
     * @param type 类型（kb/knowledge）
     * @param id   条目 ID
     * @return 空响应
     */
    @PostMapping("/{type}/{id}/restore")
    public ApiResponse<Void> restore(@PathVariable String type, @PathVariable Long id) {
        recycleBinFacadeService.restore(type, id);
        return ApiResponse.success(null);
    }

    /**
     * 彻底删除（F-0305 回收站清空）：二次确认参数 confirm=CONFIRM；物理删除并联动清理索引。
     *
     * @param type    类型（kb/knowledge）
     * @param id      条目 ID
     * @param confirm 二次确认参数（固定值 CONFIRM）
     * @return 空响应
     */
    @DeleteMapping("/{type}/{id}")
    public ApiResponse<Void> purge(@PathVariable String type, @PathVariable Long id,
                                   @RequestParam(required = false) String confirm) {
        recycleBinFacadeService.purge(type, id, confirm);
        return ApiResponse.success(null);
    }
}
