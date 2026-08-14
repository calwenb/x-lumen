package com.calwen.xlumen.knowledge.controller;

import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.knowledge.dto.CreateDirectoryDTO;
import com.calwen.xlumen.knowledge.dto.UpdateDirectoryDTO;
import com.calwen.xlumen.knowledge.service.DirectoryService;
import com.calwen.xlumen.knowledge.vo.DirectoryVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 目录管理接口（F-0309，B22 目录树 CRUD）：需登录；归属与同级同名冲突校验在 Service。
 * 删除目录时目录下知识上挂父目录（根级目录删除挂库根）。
 *
 * @author calwen
 * @date 2026/8/14
 */
@RestController
@RequestMapping("/api/v1/knowledge-bases/{kbId}/directories")
@PreAuthorize("hasRole('OWNER')")
public class DirectoryController {

    @Resource
    private DirectoryService directoryService;

    /**
     * 目录树（F-0309）：一级目录平铺返回，子目录挂 children，均按名称排序（数据库排序规则）。
     *
     * @param kbId 知识库 ID
     * @return 目录树列表
     */
    @GetMapping
    public ApiResponse<List<DirectoryVO>> tree(@PathVariable Long kbId) {
        return ApiResponse.success(directoryService.tree(kbId));
    }

    /**
     * 创建目录（F-0309）：parentId 必须属于同一知识库（0=库根）；同级同名冲突 409。
     *
     * @param kbId 知识库 ID
     * @param dto  创建入参
     * @return 创建后的目录
     */
    @PostMapping
    public ApiResponse<DirectoryVO> create(@PathVariable Long kbId,
                                           @Valid @RequestBody CreateDirectoryDTO dto) {
        return ApiResponse.success(directoryService.create(kbId, dto));
    }

    /**
     * 更新目录（F-0309）：仅名称可改（父目录变更 V2 提供）。
     *
     * @param kbId        知识库 ID
     * @param directoryId 目录 ID
     * @param dto         更新入参
     * @return 空响应
     */
    @PutMapping("/{directoryId}")
    public ApiResponse<Void> update(@PathVariable Long kbId, @PathVariable Long directoryId,
                                    @Valid @RequestBody UpdateDirectoryDTO dto) {
        directoryService.update(kbId, directoryId, dto);
        return ApiResponse.success(null);
    }

    /**
     * 删除目录（F-0309）：目录及全部子目录删除，目录下知识上挂父目录（根级删除挂库根）。
     *
     * @param kbId        知识库 ID
     * @param directoryId 目录 ID
     * @return 空响应
     */
    @DeleteMapping("/{directoryId}")
    public ApiResponse<Void> delete(@PathVariable Long kbId, @PathVariable Long directoryId) {
        directoryService.delete(kbId, directoryId);
        return ApiResponse.success(null);
    }
}
