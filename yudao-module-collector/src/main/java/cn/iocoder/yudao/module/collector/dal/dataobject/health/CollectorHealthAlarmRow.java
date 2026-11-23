package cn.iocoder.yudao.module.collector.dal.dataobject.health;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集运行详情 - 活动报警明细行（一次 JOIN 带出设备 / 传感器 / 通道名称，避免逐通道查询）。
 */
@Data
public class CollectorHealthAlarmRow {

    /** 通道编号 */
    private Long channelId;
    /** 设备编号 */
    private Long deviceId;
    /** 传感器编号 */
    private Long sensorId;
    /** 当前级别：2 预警、3 异常（level = 0 不进入本行） */
    private Integer level;
    /** 当前命中的阈值类型 */
    private String thresholdType;
    /** 当前级别开始时间 */
    private LocalDateTime since;
    /** 最近参与判定的工程值 */
    private Double lastValue;
    /** 最近判定使用的阈值 */
    private Double lastThreshold;
    /** 最近参与判定的采样时间 */
    private LocalDateTime lastSampleTime;
    /** 设备名称 */
    private String deviceName;
    /** 传感器名称 */
    private String sensorName;
    /** 通道名称 */
    private String channelName;

}
