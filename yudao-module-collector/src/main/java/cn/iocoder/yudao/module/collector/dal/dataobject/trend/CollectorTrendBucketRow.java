package cn.iocoder.yudao.module.collector.dal.dataobject.trend;

import lombok.Data;

import java.time.LocalDateTime;

/** 趋势查询的聚合桶行；raw 与各聚合层共用同一组指标列。 */
@Data
public class CollectorTrendBucketRow {

    private Long channelId;
    private LocalDateTime bucketTime;
    /** 按有效样本加权后的均值。 */
    private Double avg;
    private Double min;
    private Double max;
    /** 有效样本数：quality=0 且 value 非 NULL，与 valid_count 口径一致。 */
    private Integer count;

}
