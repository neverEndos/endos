package cn.iocoder.yudao.module.collector.controller.admin.aggregate;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.collector.controller.admin.aggregate.vo.CollectorAggregateBackfillReqVO;
import cn.iocoder.yudao.module.collector.enums.CollectorAggregateType;
import cn.iocoder.yudao.module.collector.service.aggregate.CollectorAggregateService;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.AGGREGATE_FORCE_CLEAN_FORBIDDEN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** forceClean 独立权限校验；普通回填不应额外要求该权限。 */
public class CollectorAggregateControllerTest {

    private static final String FORCE_CLEAN_PERMISSION = "collector:aggregate:force-clean";

    private CollectorAggregateController controller;
    private CollectorAggregateService aggregateService;
    private SecurityFrameworkService securityFrameworkService;

    @Before
    public void setUp() throws Exception {
        controller = new CollectorAggregateController();
        aggregateService = mock(CollectorAggregateService.class);
        securityFrameworkService = mock(SecurityFrameworkService.class);
        Field serviceField = CollectorAggregateController.class.getDeclaredField("aggregateService");
        serviceField.setAccessible(true);
        serviceField.set(controller, aggregateService);
        Field securityField = CollectorAggregateController.class
                .getDeclaredField("securityFrameworkService");
        securityField.setAccessible(true);
        securityField.set(controller, securityFrameworkService);

        when(aggregateService.backfill(any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(new CollectorAggregateService.AggregateResult(
                        "minute", 1, 1, 1, 0, 0, 0, List.of()));
    }

    @Test
    public void backfill_forceCleanWithoutPermissionIsRejectedBeforeService() {
        when(securityFrameworkService.hasPermission(FORCE_CLEAN_PERMISSION)).thenReturn(false);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> controller.backfill(request(true)));

        assertEquals(AGGREGATE_FORCE_CLEAN_FORBIDDEN.getCode(), exception.getCode());
        verify(aggregateService, never()).backfill(any(), any(), any(), any(),
                anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    public void backfill_forceCleanWithPermissionCallsServiceWithForceCleanTrue() {
        when(securityFrameworkService.hasPermission(FORCE_CLEAN_PERMISSION)).thenReturn(true);

        controller.backfill(request(true));

        verify(aggregateService).backfill(eq(CollectorAggregateType.MINUTE), any(), any(), any(),
                eq(false), eq(false), eq(true));
    }

    @Test
    public void backfill_normalRebuildDoesNotRequireForceCleanPermission() {
        when(securityFrameworkService.hasPermission(FORCE_CLEAN_PERMISSION)).thenReturn(false);

        controller.backfill(request(false));

        verify(aggregateService).backfill(eq(CollectorAggregateType.MINUTE), any(), any(), any(),
                eq(false), eq(false), eq(false));
    }

    private CollectorAggregateBackfillReqVO request(boolean forceClean) {
        CollectorAggregateBackfillReqVO reqVO = new CollectorAggregateBackfillReqVO();
        reqVO.setAggType("minute");
        reqVO.setFrom(LocalDateTime.of(2026, 1, 1, 0, 0));
        reqVO.setTo(LocalDateTime.of(2026, 1, 1, 0, 1));
        reqVO.setChannelIds(List.of(1L));
        reqVO.setForceClean(forceClean);
        return reqVO;
    }

}
