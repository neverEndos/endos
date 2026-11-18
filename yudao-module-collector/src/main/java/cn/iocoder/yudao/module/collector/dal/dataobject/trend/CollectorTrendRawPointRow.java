package cn.iocoder.yudao.module.collector.dal.dataobject.trend;

import lombok.Data;

import java.time.LocalDateTime;

/** 原始点抽样行；每个抽样步长桶只保留最早的一条有效采样。 */
@Data
public class CollectorTrendRawPointRow {

    private Long channelId;
    private LocalDateTime bucketTime;
    private Double value;

}
