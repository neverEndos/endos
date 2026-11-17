package cn.iocoder.yudao.module.collector.controller.admin.alarm.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 采集报警 Response VO")
@Data
public class CollectorAlarmRespVO {

    @Schema(description = "事件编号", example = "1")
    private Long id;
    @Schema(description = "设备编号", example = "1")
    private Long deviceId;
    @Schema(description = "设备名称", example = "RK3568 采集主机")
    private String deviceName;
    @Schema(description = "传感器编号", example = "2")
    private Long sensorId;
    @Schema(description = "传感器名称", example = "CS1000-1")
    private String sensorName;
    @Schema(description = "通道编号", example = "3")
    private Long channelId;
    @Schema(description = "通道名称", example = "油液温度")
    private String channelName;
    @Schema(description = "阈值类型", example = "threshold_high_error")
    private String alarmType;
    @Schema(description = "事件类型", example = "raised")
    private String eventType;
    @Schema(description = "级别：2 预警、3 异常", example = "3")
    private Integer level;
    @Schema(description = "状态：0 活动、1 已确认、2 已恢复", example = "0")
    private Integer status;
    @Schema(description = "报警描述")
    private String message;
    @Schema(description = "触发值", example = "95.2")
    private Double triggerValue;
    @Schema(description = "阈值", example = "90")
    private Double thresholdValue;
    @Schema(description = "触发时间")
    private LocalDateTime triggeredAt;
    @Schema(description = "最近触发时间")
    private LocalDateTime lastSeenAt;
    @Schema(description = "重复触发次数", example = "1")
    private Integer occurrenceCount;
    @Schema(description = "恢复时间")
    private LocalDateTime clearedAt;
    @Schema(description = "恢复原因")
    private String clearReason;

}
