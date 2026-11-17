package cn.iocoder.yudao.module.collector.controller.admin.dashboard;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.collector.controller.admin.dashboard.vo.CollectorDashboardLatestRespVO;
import cn.iocoder.yudao.module.collector.service.dashboard.CollectorDashboardService;
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

@Tag(name = "管理后台 - 采集器看板")
@RestController
@RequestMapping("/collector/dashboard")
public class CollectorDashboardController {

    @Resource
    private CollectorDashboardService dashboardService;

    @GetMapping("/latest")
    @Operation(summary = "获得设备最新值看板",
            description = "每次返回完整看板数据，不使用 ETag/304；在线与陈旧状态会随服务器时间变化")
    @Parameter(name = "deviceId", description = "设备编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('collector:dashboard:query')")
    public CommonResult<CollectorDashboardLatestRespVO> getLatest(
            @RequestParam("deviceId") Long deviceId) {
        return success(dashboardService.getLatest(deviceId));
    }

}
