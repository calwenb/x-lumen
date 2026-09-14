package com.calwen.xlumen.identity.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.identity.dto.AuditLogQueryDTO;
import com.calwen.xlumen.identity.entity.ActivityLogEntity;
import com.calwen.xlumen.identity.mapper.ActivityLogMapper;
import com.calwen.xlumen.identity.vo.AuditLogVO;
import com.calwen.xlumen.identity.vo.PageVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审计日志查询单测：动作筛选按子串匹配（LIKE %关键字%），工作空间条件恒在；
 * 分页 total 随筛选结果返回。
 *
 * @author calwen
 * @date 2026/9/14
 */
class ActivityLogServiceImplTest {

    private static final Long WORKSPACE_ID = 100L;

    @Mock
    private ActivityLogMapper activityLogMapper;

    @InjectMocks
    private ActivityLogServiceImpl activityLogService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        WorkspaceContext.set(WORKSPACE_ID, 1L, "tester");
        // 脱离 Spring 容器构造 LambdaQueryWrapper 时需先注册实体元信息，否则 Lambda 解析无缓存。
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ActivityLogEntity.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        WorkspaceContext.clear();
        mocks.close();
    }

    /** 捕获传给 mapper 的查询条件，断言动作筛选与工作空间过滤的 SQL 片段。 */
    private Wrapper<ActivityLogEntity> capturedWrapper(String action) {
        Page<ActivityLogEntity> page = new Page<>(1, 20);
        ActivityLogEntity entity = new ActivityLogEntity();
        entity.setId(1L);
        entity.setWorkspaceId(WORKSPACE_ID);
        entity.setAction("WORKSPACE_SETTINGS_UPDATE");
        entity.setOperatorName("tester");
        entity.setCreatedAt(LocalDateTime.now());
        page.setRecords(List.of(entity));
        page.setTotal(1);
        when(activityLogMapper.selectPage(any(), any())).thenReturn(page);

        AuditLogQueryDTO query = new AuditLogQueryDTO();
        query.setPageNo(1);
        query.setPageSize(20);
        query.setAction(action);
        PageVO<AuditLogVO> result = activityLogService.listAuditLogs(query);
        assertThat(result.getTotal()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<ActivityLogEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(activityLogMapper).selectPage(any(), captor.capture());
        return captor.getValue();
    }

    @Test
    void listAuditLogs_actionKeyword_matchesBySubstringLike() {
        Wrapper<ActivityLogEntity> wrapper = capturedWrapper("WORKSPACE");

        String segment = wrapper.getSqlSegment().toUpperCase();
        assertThat(segment).contains("LIKE").contains("ACTION").contains("WORKSPACE_ID");
        @SuppressWarnings("unchecked")
        LambdaQueryWrapper<ActivityLogEntity> lambda = (LambdaQueryWrapper<ActivityLogEntity>) wrapper;
        assertThat(lambda.getParamNameValuePairs().values()).contains("%WORKSPACE%");
    }

    @Test
    void listAuditLogs_blankAction_omitsActionCondition() {
        Wrapper<ActivityLogEntity> wrapper = capturedWrapper("  ");

        String segment = wrapper.getSqlSegment().toUpperCase();
        assertThat(segment).doesNotContain("LIKE").contains("WORKSPACE_ID");
    }
}
