package cn.iocoder.yudao.module.collector.controller.admin.alarm.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 通道当前报警状态 Response VO")
@Data
public class CollectorAlarmStateRespVO {

    @Schema(description = "当前活动报警事件编号；无活动事件时为空", example = "1024")
    private Long activeAlarmId;
    @Schema(description = "当前活动报警是否已确认", example = "true")
    private Boolean acknowledged;
    @Schema(description = "当前活动报警确认时间；未确认时为空")
    private LocalDateTime acknowledgedAt;
    @Schema(description = "通道编号", example = "3")
    private Long channelId;
    @Schema(description = "通道名称", example = "油液温度")
    private String channelName;
    @Schema(description = "设备编号", example = "1")
    private Long deviceId;
    @Schema(description = "设备名称", example = "RK3568 采集主机")
    private String deviceName;
    @Schema(description = "传感器编号", example = "2")
    private Long sensorId;
    @Schema(description = "传感器名称", example = "CS1000-1")
    private String sensorName;
    @Schema(description = "当前级别：0 正常、2 预警、3 异常", example = "3")
    private Integer level;
    @Schema(description = "当前阈值类型", example = "threshold_high_error")
    private String thresholdType;
    @Schema(description = "当前级别开始时间")
    private LocalDateTime since;
    @Schema(description = "最近工程值", example = "95.2")
    private Double lastValue;
    @Schema(description = "最近判定阈值", example = "90")
    private Double lastThreshold;
    @Schema(description = "最近采样时间")
    private LocalDateTime lastSampleTime;
    @Schema(description = "状态更新时间")
    private LocalDateTime updatedTime;

}
