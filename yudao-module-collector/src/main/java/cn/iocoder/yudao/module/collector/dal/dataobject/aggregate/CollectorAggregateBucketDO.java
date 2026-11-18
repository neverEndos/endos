package cn.iocoder.yudao.module.collector.dal.dataobject.aggregate;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聚合桶通用行；day 表写入时由 Mapper 将 bucket_time 映射到 bucket_date。
 */
@Data
public class CollectorAggregateBucketDO {

    private Long channelId;
    private Long deviceId;
    private LocalDateTime bucketTime;
    private Integer sampleCount;
    private Integer goodCount;
    /** quality=0 且 value 非 NULL 的行数；上卷时为下层 valid_count 之和。 */
    private Integer validCount;
    private Integer badCount;
    /** 上卷检测用：本目标桶实际参与的上层子桶行数；raw 聚合时为 NULL。 */
    private Integer sourceBucketCount;
    private Double valueAvg;
    private Double valueMin;
    private Double valueMax;
    private Double valueSum;
    private Double valueFirst;
    private Double valueLast;

}
