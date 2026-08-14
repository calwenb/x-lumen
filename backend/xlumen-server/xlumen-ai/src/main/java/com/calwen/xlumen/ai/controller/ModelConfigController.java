package com.calwen.xlumen.ai.controller;

import com.calwen.xlumen.ai.dto.ModelConfigTestDTO;
import com.calwen.xlumen.ai.dto.ModelConfigUpdateDTO;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.ModelGateway;
import com.calwen.xlumen.ai.service.SceneConfigService;
import com.calwen.xlumen.ai.vo.ModelConfigVO;
import com.calwen.xlumen.ai.vo.TestResultVO;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.common.web.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 场景模型配置接口（F-0502，管理面 A03）：仅博主（OWNER）可管理，密钥永不返回。
 *
 * @author calwen
 * @date 2026/8/13
 */
@RestController
@RequestMapping("/api/v1/admin/model-configs")
@PreAuthorize("hasRole('OWNER')")
public class ModelConfigController {

    private final SceneConfigService sceneConfigService;
    private final ModelGateway modelGateway;

    public ModelConfigController(SceneConfigService sceneConfigService, ModelGateway modelGateway) {
        this.sceneConfigService = sceneConfigService;
        this.modelGateway = modelGateway;
    }

    /**
     * 场景配置列表（密钥永不返回）。
     */
    @GetMapping
    public ApiResponse<List<ModelConfigVO>> list() {
        return ApiResponse.success(sceneConfigService.list(WorkspaceContext.workspaceId()));
    }

    /**
     * 新增或更新场景配置（provider/model/paramsJson）。
     */
    @PutMapping("/{scene}")
    public ApiResponse<Void> update(@PathVariable String scene,
                                    @Valid @RequestBody ModelConfigUpdateDTO dto) {
        sceneConfigService.update(WorkspaceContext.workspaceId(), parseScene(scene),
                dto.getProvider(), dto.getModel(), dto.getParamsJson());
        return ApiResponse.success(null);
    }

    /**
     * 模型连通性测试：发一句 ping，返回 {ok, message}。
     */
    @PostMapping("/test")
    public ApiResponse<TestResultVO> test(@Valid @RequestBody ModelConfigTestDTO dto) {
        try {
            boolean ok = modelGateway.test(dto.getProvider(), dto.getModel());
            return ApiResponse.success(TestResultVO.builder().ok(ok).message("连接成功").build());
        } catch (BizException e) {
            return ApiResponse.success(TestResultVO.builder().ok(false).message(e.getMessage()).build());
        }
    }

    private AiScene parseScene(String scene) {
        try {
            return AiScene.valueOf(scene.toUpperCase());
        } catch (Exception e) {
            throw new BizException(ErrorCode.INVALID_PARAM, "未知场景：" + scene);
        }
    }
}
