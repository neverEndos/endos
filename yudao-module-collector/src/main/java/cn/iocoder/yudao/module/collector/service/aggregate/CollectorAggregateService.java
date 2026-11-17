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

    /**
     * 手工回填，同步执行；先清空目标区间再重建，源数据删除或修正后不会残留旧桶。
     *
     * <p>单次上限：second 1 天/10 通道，minute 7 天/50 通道，hour、day 90 天/50 通道。
     * 大范围回填应优先使用 fromRaw=false 分层调用或分批，避免同步请求超时。</p>
     *
     * <p>可信顺序为 second → minute → hour → day；未重建下层时，上层会按覆盖度检测回退 raw，
     * 但显式级联仍建议从 second 或 minute 起调用。</p>
     */
    AggregateResult backfill(CollectorAggregateType aggType, LocalDateTime from, LocalDateTime to,
                             Collection<Long> channelIds, boolean fromRaw);

    /**
     * 手工回填并可选级联重建上层（second→minute→hour→day、minute→hour→day、hour→day）。
     * 级联层同样先清桶再重建，上层默认从已重建的下层上卷，并继续执行覆盖度回退。
     */
    AggregateResult backfill(CollectorAggregateType aggType, LocalDateTime from, LocalDateTime to,
                             Collection<Long> channelIds, boolean fromRaw, boolean cascade);

}
