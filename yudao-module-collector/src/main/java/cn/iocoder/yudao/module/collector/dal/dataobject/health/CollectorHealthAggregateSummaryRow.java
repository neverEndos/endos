package cn.iocoder.yudao.module.collector.dal.dataobject.health;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集运行详情 - 聚合水位按 {@code agg_type} 分组汇总行。
 *
 * <p>一行对应一个聚合类型；无水位行时该类型不会出现在结果中，由 Service 补 0 行并标记未初始化。</p>
 */
@Data
public class CollectorHealthAggregateSummaryRow {

    /** 聚合类型：second / minute / hour / day */
    private String aggType;
    /** 水位行数（通道数） */
    private Integer rowCount;
    /** {@code status <> 0} 的行数；status 1/2 为预留，当前流程未写入 */
    private Integer abnormalCount;
    /** 最大重试次数；retry_count 为预留字段，当前恒为 0 */
    private Integer maxRetryCount;
    /** 最近一次成功时间 */
    private LocalDateTime lastSuccessTime;
    /** 最小水位时间 */
    private LocalDateTime minWatermarkTime;
    /** 最大水位时间 */
    private LocalDateTime maxWatermarkTime;

}
