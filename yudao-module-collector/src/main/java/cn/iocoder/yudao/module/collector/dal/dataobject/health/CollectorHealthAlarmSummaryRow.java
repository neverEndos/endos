package cn.iocoder.yudao.module.collector.dal.dataobject.health;

import lombok.Data;

/**
 * 采集运行详情 - 设备活动报警汇总行（{@code level > 0} 的通道数）。
 */
@Data
public class CollectorHealthAlarmSummaryRow {

    /** 当前活动报警通道数 */
    private Integer activeCount;
    /** 当前活动报警的最高级别；无活动报警时为 0 */
    private Integer highestLevel;

}
