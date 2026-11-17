package cn.iocoder.yudao.module.collector.dal.dataobject.alarm;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通道当前报警状态 DO
 */
@Data
@TableName("collector_alarm_state")
public class CollectorAlarmStateDO {

    /**
     * 通道编号
     */
    @TableId(type = IdType.INPUT)
    private Long channelId;
    /**
     * 设备编号
     */
    private Long deviceId;
    /**
     * 传感器编号
     */
    private Long sensorId;
    /**
     * 当前级别：0 正常、2 预警、3 异常
     */
    private Integer level;
    /**
     * 当前命中的四档阈值类型
     */
    private String thresholdType;
    /**
     * 当前级别开始时间
     */
    private LocalDateTime since;
    /**
     * 最近参与判定的工程值
     */
    private Double lastValue;
    /**
     * 最近判定使用的阈值
     */
    private Double lastThreshold;
    /**
     * 最近参与判定的采样时间
     */
    private LocalDateTime lastSampleTime;
    /**
     * 最近参与判定的采样编号
     */
    private Long lastSampleId;
    /**
     * 状态更新时间
     */
    private LocalDateTime updatedTime;

}
