package cn.iocoder.yudao.module.collector.dal.dataobject.alarm;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报警扫描单行游标 DO
 */
@Data
@TableName("collector_alarm_cursor")
public class CollectorAlarmCursorDO {

    /**
     * 固定为 1
     */
    @TableId(type = IdType.INPUT)
    private Integer id;
    /**
     * 已处理采样时间（含）
     */
    private LocalDateTime lastSampleTime;
    /**
     * 同一采样时间下已处理的最大采样编号（含）
     */
    private Long lastSampleId;
    /**
     * 游标更新时间
     */
    private LocalDateTime updatedTime;

}
