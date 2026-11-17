package cn.iocoder.yudao.module.collector.dal.dataobject.alarm;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集报警事件 DO
 */
@Data
@TableName("collector_alarm")
@KeySequence("collector_alarm_seq")
public class CollectorAlarmDO {

    @TableId
    private Long id;
    /** 设备编号 */
    private Long deviceId;
    /** 传感器编号 */
    private Long sensorId;
    /** 通道编号 */
    private Long channelId;
    /** 四档阈值类型 */
    private String alarmType;
    /** 事件类型：raised/upgraded/downgraded/cleared */
    private String eventType;
    /** 级别：2 预警、3 异常 */
    private Integer level;
    /** 活动报警去重键 */
    private String dedupeKey;
    /** 状态：0 活动、1 已确认、2 已恢复 */
    private Integer status;
    /** 报警描述 */
    private String message;
    /** 触发工程值 */
    private Double triggerValue;
    /** 触发阈值 */
    private Double thresholdValue;
    /** 首次触发时间 */
    private LocalDateTime triggeredAt;
    /** 最近一次触发时间 */
    private LocalDateTime lastSeenAt;
    /** 重复触发次数 */
    private Integer occurrenceCount;
    /** 确认时间（v1 不使用） */
    private LocalDateTime acknowledgedAt;
    /** 确认人（v1 不使用） */
    private Long acknowledgedBy;
    /** 恢复时间 */
    private LocalDateTime clearedAt;
    /** 恢复原因 */
    private String clearReason;

}
