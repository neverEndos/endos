package cn.iocoder.yudao.module.collector.controller.admin.alarm;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.collector.controller.admin.alarm.vo.CollectorAlarmPageReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.alarm.vo.CollectorAlarmRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.alarm.vo.CollectorAlarmStateRespVO;
import cn.iocoder.yudao.module.collector.service.alarm.CollectorAlarmService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 采集报警")
@RestController
@RequestMapping("/collector/alarm")
public class CollectorAlarmController {

    @Resource
    private CollectorAlarmService alarmService;

    @GetMapping("/page")
    @Operation(summary = "获得报警记录分页")
    @PreAuthorize("@ss.hasPermission('collector:alarm:query')")
    public CommonResult<PageResult<CollectorAlarmRespVO>> getAlarmPage(@Valid CollectorAlarmPageReqVO pageReqVO) {
        return success(alarmService.getAlarmPage(pageReqVO));
    }

    @GetMapping("/state/list")
    @Operation(summary = "获得通道当前报警状态")
    @Parameter(name = "deviceId", description = "设备编号", example = "1")
    @PreAuthorize("@ss.hasPermission('collector:alarm:query')")
    public CommonResult<List<CollectorAlarmStateRespVO>> getAlarmStateList(
            @RequestParam(value = "deviceId", required = false) Long deviceId) {
        return success(alarmService.getAlarmStateList(deviceId));
    }

    @PostMapping("/acknowledge")
    @Operation(summary = "确认活动报警", description = "仅活动（status=0 且非恢复事件）的报警可确认；"
            + "确认后状态置为已确认，级别与恢复时间不变")
    @Parameter(name = "id", description = "报警事件编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('collector:alarm:acknowledge')")
    public CommonResult<Boolean> acknowledgeAlarm(@RequestParam("id") Long id) {
        alarmService.acknowledgeAlarm(id, SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

}
