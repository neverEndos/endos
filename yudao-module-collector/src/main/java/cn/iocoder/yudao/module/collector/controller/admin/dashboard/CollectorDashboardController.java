package cn.iocoder.yudao.module.collector.controller.admin.dashboard;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.collector.controller.admin.dashboard.vo.CollectorDashboardLatestRespVO;
import cn.iocoder.yudao.module.collector.service.dashboard.CollectorDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
    @Operation(summary = "获得设备最新值看板；If-None-Match 命中时返回 HTTP 304")
    @Parameter(name = "deviceId", description = "设备编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('collector:dashboard:query')")
    public ResponseEntity<CommonResult<CollectorDashboardLatestRespVO>> getLatest(
            @RequestParam("deviceId") Long deviceId,
            @Parameter(hidden = true)
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        CollectorDashboardLatestRespVO result = dashboardService.getLatest(deviceId);
        String versionToken = result.getVersionToken();
        if (matchesVersionToken(ifNoneMatch, versionToken)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(versionToken)
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(versionToken)
                .body(success(result));
    }

    /** 同时兼容强 ETag、弱 ETag、逗号列表与通配符。 */
    private static boolean matchesVersionToken(String ifNoneMatch, String versionToken) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        for (String item : ifNoneMatch.split(",")) {
            String candidate = item.trim();
            if ("*".equals(candidate)) {
                return true;
            }
            if (candidate.startsWith("W/")) {
                candidate = candidate.substring(2).trim();
            }
            if (candidate.length() >= 2 && candidate.startsWith("\"") && candidate.endsWith("\"")) {
                candidate = candidate.substring(1, candidate.length() - 1);
            }
            if (versionToken.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

}
