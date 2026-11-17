package cn.iocoder.yudao.module.collector.controller.admin.alarm.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 采集报警分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CollectorAlarmPageReqVO extends PageParam {

    @Schema(description = "设备编号", example = "1")
    private Long deviceId;
    @Schema(description = "传感器编号", example = "2")
    private Long sensorId;
    @Schema(description = "通道编号", example = "3")
    private Long channelId;
    @Schema(description = "级别：2 预警、3 异常", example = "3")
    private Integer level;
    @Schema(description = "事件类型：raised/upgraded/downgraded/changed/cleared", example = "raised")
    private String eventType;
    @Schema(description = "触发时间范围", example = "[2026-11-17 00:00:00, 2026-11-17 23:59:59]")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] triggeredAt;

}
