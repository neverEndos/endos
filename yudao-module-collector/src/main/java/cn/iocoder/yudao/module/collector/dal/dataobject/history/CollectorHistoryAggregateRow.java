package cn.iocoder.yudao.module.collector.dal.dataobject.history;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聚合桶行（历史查询 second/minute/hour/day 粒度）。
 *
 * <p>day 表的 {@code bucket_date} 在 SQL 中统一转成当天 00:00 的 timestamp 再映射到
 * {@link #bucketTime}，因此各粒度可用同一套 epoch 毫秒换算逻辑。
 */
@Data
public class CollectorHistoryAggregateRow {

    private Long channelId;

    private LocalDateTime bucketTime;

    private Integer sampleCount;

    private Integer goodCount;

    private Integer badCount;

    private Double valueAvg;

    private Double valueMin;

    private Double valueMax;

    private Double valueFirst;

    private Double valueLast;

}
