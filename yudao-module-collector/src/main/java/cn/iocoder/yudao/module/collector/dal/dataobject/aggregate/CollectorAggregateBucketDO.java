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
    private Integer badCount;
    private Double valueAvg;
    private Double valueMin;
    private Double valueMax;
    private Double valueSum;
    private Double valueFirst;
    private Double valueLast;

}
