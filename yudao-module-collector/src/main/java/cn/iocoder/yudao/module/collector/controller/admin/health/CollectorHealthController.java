package cn.iocoder.yudao.module.collector.controller.admin.health;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.collector.controller.admin.health.vo.CollectorHealthOverviewRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.health.vo.CollectorHealthSummaryRespVO;
import cn.iocoder.yudao.module.collector.service.health.CollectorHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 采集运行详情")
@RestController
@RequestMapping("/collector/health")
public class CollectorHealthController {

    @Resource
    private CollectorHealthService healthService;

    @GetMapping("/summary")
    @Operation(summary = "获得采集运行摘要",
            description = "返回心跳、当前设备状态与活动报警数量；全部只读，不触发任何维护任务")
    @Parameter(name = "deviceId", description = "设备编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('collector:health:query')")
    public CommonResult<CollectorHealthSummaryRespVO> getSummary(
            @RequestParam("deviceId") Long deviceId) {
        return success(healthService.getSummary(deviceId));
    }

    @GetMapping("/overview")
    @Operation(summary = "获得采集运行详情",
            description = "摘要 + 活动报警明细 + 聚合水位 + 保留策略 + raw 分区预建情况；全部只读")
    @Parameter(name = "deviceId", description = "设备编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('collector:health:query')")
    public CommonResult<CollectorHealthOverviewRespVO> getOverview(
            @RequestParam("deviceId") Long deviceId) {
        return success(healthService.getOverview(deviceId));
    }

}
