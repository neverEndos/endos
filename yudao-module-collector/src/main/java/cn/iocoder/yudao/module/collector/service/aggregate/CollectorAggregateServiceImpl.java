package cn.iocoder.yudao.module.collector.service.aggregate;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.collector.dal.dataobject.aggregate.CollectorAggregateBucketDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.channel.CollectorChannelDO;
import cn.iocoder.yudao.module.collector.dal.mysql.aggregate.CollectorAggregateMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.channel.CollectorChannelMapper;
import cn.iocoder.yudao.module.collector.enums.CollectorAggregateType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.AGGREGATE_BACKFILL_INVALID;

@Service
public class CollectorAggregateServiceImpl implements CollectorAggregateService {

    public static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Duration MAX_BACKFILL_RANGE = Duration.ofDays(7);
    private static final int MAX_BACKFILL_CHANNELS = 50;

    private final CollectorChannelMapper channelMapper;
    private final CollectorAggregateMapper aggregateMapper;
    private final TransactionTemplate transactionTemplate;

    /** 桶结束时间必须早于当前墙钟减去该宽限秒数，避免聚合仍在写入的桶。 */
    @Value("${yudao.collector.aggregate.grace-seconds:5}")
    private long graceSeconds = 5;

    public CollectorAggregateServiceImpl(CollectorChannelMapper channelMapper,
                                         CollectorAggregateMapper aggregateMapper,
                                         PlatformTransactionManager transactionManager) {
        this.channelMapper = channelMapper;
        this.aggregateMapper = aggregateMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public AggregateResult aggregateSecond(LocalDateTime from, LocalDateTime to, Collection<Long> channelIds) {
        return aggregateSecond(from, to, channelIds, false);
    }

    @Override
    public AggregateResult aggregateSecond(LocalDateTime from, LocalDateTime to, Collection<Long> channelIds,
                                           boolean fromRaw) {
        return aggregate(CollectorAggregateType.SECOND, from, to, channelIds, fromRaw);
    }

    @Override
    public AggregateResult aggregateMinute(LocalDateTime from, LocalDateTime to, Collection<Long> channelIds) {
        return aggregateMinute(from, to, channelIds, false);
    }

    @Override
    public AggregateResult aggregateMinute(LocalDateTime from, LocalDateTime to, Collection<Long> channelIds,
                                           boolean fromRaw) {
        return aggregate(CollectorAggregateType.MINUTE, from, to, channelIds, fromRaw);
    }

    @Override
    public AggregateResult aggregateHour(LocalDateTime from, LocalDateTime to, Collection<Long> channelIds) {
        return aggregateHour(from, to, channelIds, false);
    }

    @Override
    public AggregateResult aggregateHour(LocalDateTime from, LocalDateTime to, Collection<Long> channelIds,
                                         boolean fromRaw) {
        return aggregate(CollectorAggregateType.HOUR, from, to, channelIds, fromRaw);
    }

    @Override
    public AggregateResult aggregateDay(LocalDateTime from, LocalDateTime to, Collection<Long> channelIds) {
        return aggregateDay(from, to, channelIds, false);
    }

    @Override
    public AggregateResult aggregateDay(LocalDateTime from, LocalDateTime to, Collection<Long> channelIds,
                                        boolean fromRaw) {
        return aggregate(CollectorAggregateType.DAY, from, to, channelIds, fromRaw);
    }

    @Override
    public AggregateResult backfill(CollectorAggregateType aggType, LocalDateTime from, LocalDateTime to,
                                    Collection<Long> channelIds, boolean fromRaw) {
        validateRange(from, to);
        if (Duration.between(from, to).compareTo(MAX_BACKFILL_RANGE) > 0) {
            throw exception(AGGREGATE_BACKFILL_INVALID, "单次回填范围不能超过 7 天");
        }
        if (channelIds != null && new LinkedHashSet<>(channelIds).size() > MAX_BACKFILL_CHANNELS) {
            throw exception(AGGREGATE_BACKFILL_INVALID, "单次回填通道数不能超过 50");
        }
        List<CollectorChannelDO> channels = selectChannels(aggType, channelIds);
        if (channels.size() > MAX_BACKFILL_CHANNELS) {
            throw exception(AGGREGATE_BACKFILL_INVALID, "缺省选择全部通道时也不能超过 50 个，请指定 channelIds 分批回填");
        }
        return doAggregate(aggType, from, to, channels, fromRaw);
    }

    private AggregateResult aggregate(CollectorAggregateType aggType, LocalDateTime from, LocalDateTime to,
                                      Collection<Long> channelIds, boolean fromRaw) {
        validateRange(from, to);
        return doAggregate(aggType, from, to, selectChannels(aggType, channelIds), fromRaw);
    }

    private void validateRange(LocalDateTime from, LocalDateTime to) {
        Objects.requireNonNull(from, "from 不能为空");
        Objects.requireNonNull(to, "to 不能为空");
        if (!from.isBefore(to)) {
            throw exception(AGGREGATE_BACKFILL_INVALID, "结束时间必须晚于开始时间");
        }
    }

    private List<CollectorChannelDO> selectChannels(CollectorAggregateType aggType, Collection<Long> channelIds) {
        LambdaQueryWrapperX<CollectorChannelDO> query = new LambdaQueryWrapperX<>();
        if (channelIds != null && !channelIds.isEmpty()) {
            query.in(CollectorChannelDO::getId, new LinkedHashSet<>(channelIds));
        }
        query.orderByAsc(CollectorChannelDO::getId);
        List<CollectorChannelDO> channels = channelMapper.selectList(query);
        if (aggType == CollectorAggregateType.SECOND) {
            return channels.stream().filter(channel -> DATA_PROFILE_FAST.equals(channel.getDataProfile())).toList();
        }
        return channels;
    }

    private AggregateResult doAggregate(CollectorAggregateType aggType, LocalDateTime requestedFrom,
                                        LocalDateTime requestedTo, List<CollectorChannelDO> channels, boolean fromRaw) {
        long begin = System.nanoTime();
        LocalDateTime from = aggType.floor(requestedFrom);
        LocalDateTime closedTo = aggType.floor(LocalDateTime.now(SHANGHAI_ZONE).minusSeconds(Math.max(0, graceSeconds)));
        LocalDateTime to = aggType.ceil(requestedTo);
        if (to.isAfter(closedTo)) {
            to = closedTo;
        }
        if (!from.isBefore(to) || channels.isEmpty()) {
            return new AggregateResult(aggType.getCode(), channels.size(), 0, 0, elapsedMillis(begin));
        }

        long bucketCount = 0;
        long rowCount = 0;
        for (CollectorChannelDO channel : channels) {
            Source source = resolveSource(aggType, channel, fromRaw);
            LocalDateTime cursor = from;
            while (cursor.isBefore(to)) {
                LocalDateTime batchTo = aggType.plusWindow(cursor);
                if (batchTo.isAfter(to)) {
                    batchTo = to;
                }
                BatchResult batch = aggregateBatch(aggType, source, channel, cursor, batchTo, from, to);
                bucketCount += batch.bucketCount();
                rowCount += batch.rowCount();
                cursor = batchTo;
            }
        }
        return new AggregateResult(aggType.getCode(), channels.size(), bucketCount, rowCount, elapsedMillis(begin));
    }

    /** 每个通道、每个时间窗单独事务；聚合行与对应水位要么一起提交，要么一起回滚。 */
    private BatchResult aggregateBatch(CollectorAggregateType aggType, Source source, CollectorChannelDO channel,
                                       LocalDateTime batchFrom, LocalDateTime batchTo,
                                       LocalDateTime backfillFrom, LocalDateTime backfillTo) {
        return transactionTemplate.execute(status -> {
            List<CollectorAggregateBucketDO> buckets = aggregateMapper.selectBuckets(
                    source.table(), source.timeColumn(), aggType.getBucketInterval(), source.raw(),
                    channel.getId(), batchFrom, batchTo);
            if (!buckets.isEmpty()) {
                aggregateMapper.upsertBuckets(aggType.getTargetTable(), aggType == CollectorAggregateType.DAY, buckets);
            }
            long rows = buckets.stream().mapToLong(CollectorAggregateBucketDO::getSampleCount).sum();
            // 空窗口也推进水位，避免无数据通道每次重复扫描相同历史范围。
            aggregateMapper.upsertWatermark(aggType.getCode(), channel.getId(), channel.getDeviceId(),
                    batchTo, backfillFrom, backfillTo);
            return new BatchResult(buckets.size(), rows);
        });
    }

    private Source resolveSource(CollectorAggregateType aggType, CollectorChannelDO channel, boolean fromRaw) {
        if (fromRaw || aggType == CollectorAggregateType.SECOND
                || (aggType == CollectorAggregateType.MINUTE && !DATA_PROFILE_FAST.equals(channel.getDataProfile()))) {
            return new Source("collector_sample_raw", "collect_time", true);
        }
        return switch (aggType) {
            case MINUTE -> new Source("collector_aggregate_second", "bucket_time", false);
            case HOUR -> new Source("collector_aggregate_minute", "bucket_time", false);
            case DAY -> new Source("collector_aggregate_hour", "bucket_time", false);
            case SECOND -> throw new IllegalStateException("秒聚合只能从 raw 计算");
        };
    }

    private long elapsedMillis(long begin) {
        return (System.nanoTime() - begin) / 1_000_000;
    }

    private record Source(String table, String timeColumn, boolean raw) {
    }

    private record BatchResult(long bucketCount, long rowCount) {
    }

}
