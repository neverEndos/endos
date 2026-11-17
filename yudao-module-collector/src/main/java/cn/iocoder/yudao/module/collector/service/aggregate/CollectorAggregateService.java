package cn.iocoder.yudao.module.collector.service.aggregate;

import cn.iocoder.yudao.module.collector.enums.CollectorAggregateType;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * 采集数据多级聚合服务。
 */
public interface CollectorAggregateService {

    String DATA_PROFILE_FAST = "fast";

    /**
     * @param channelCount 实际参与处理的通道数
     * @param bucketCount 写入或更新的聚合桶数
     * @param rowCount 聚合读取的 raw 源行数；从下层上卷时由 sample_count 求和得到
     * @param durationMillis 整次调用耗时
     */
    record AggregateResult(String aggType, int channelCount, long bucketCount, long rowCount, long durationMillis) {
    }

    AggregateResult aggregateSecond(LocalDateTime from, LocalDateTime to, Collection<Long> channelIds);

    AggregateResult aggregateSecond(LocalDateTime from, LocalDateTime to, Collection<Long> channelIds, boolean fromRaw);

    AggregateResult aggregateMinute(LocalDateTime from, LocalDateTime to, Collection<Long> channelIds);

    AggregateResult aggregateMinute(LocalDateTime from, LocalDateTime to, Collection<Long> channelIds, boolean fromRaw);

    AggregateResult aggregateHour(LocalDateTime from, LocalDateTime to, Collection<Long> channelIds);

    AggregateResult aggregateHour(LocalDateTime from, LocalDateTime to, Collection<Long> channelIds, boolean fromRaw);

    AggregateResult aggregateDay(LocalDateTime from, LocalDateTime to, Collection<Long> channelIds);

    AggregateResult aggregateDay(LocalDateTime from, LocalDateTime to, Collection<Long> channelIds, boolean fromRaw);

    /** 手工回填：单次最多 7 天、50 个通道，同步执行。 */
    AggregateResult backfill(CollectorAggregateType aggType, LocalDateTime from, LocalDateTime to,
                             Collection<Long> channelIds, boolean fromRaw);

}
