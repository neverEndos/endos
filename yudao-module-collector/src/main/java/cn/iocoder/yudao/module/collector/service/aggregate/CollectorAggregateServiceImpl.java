package cn.iocoder.yudao.module.collector.service.aggregate;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.collector.dal.dataobject.aggregate.CollectorAggregateBucketDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.channel.CollectorChannelDO;
import cn.iocoder.yudao.module.collector.dal.mysql.aggregate.CollectorAggregateMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.channel.CollectorChannelMapper;
import cn.iocoder.yudao.module.collector.enums.CollectorAggregateType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.AGGREGATE_BACKFILL_INVALID;

@Slf4j
@Service
public class CollectorAggregateServiceImpl implements CollectorAggregateService {

    public static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String RAW_TABLE = "collector_sample_raw";
    private static final String RAW_TIME_COLUMN = "collect_time";

    private final CollectorChannelMapper channelMapper;
    private final CollectorAggregateMapper aggregateMapper;
    private final TransactionTemplate transactionTemplate;

    /** 桶结束时间必须早于当前墙钟减去该宽限秒数，避免聚合仍在写入的桶。 */
    @Value("${yudao.collector.aggregate.grace-seconds:5}")
    private long graceSeconds = 5;

    /** raw 保留天数；fromRaw 回填超过该范围时 raw 可能已被清理，必须拒绝。 */
    @Value("${yudao.collector.aggregate.raw-retention-days:30}")
    private long rawRetentionDays = 30;

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
        return backfill(aggType, from, to, channelIds, fromRaw, false, false);
    }

    @Override
    public AggregateResult backfill(CollectorAggregateType aggType, LocalDateTime from, LocalDateTime to,
                                    Collection<Long> channelIds, boolean fromRaw, boolean cascade) {
        return backfill(aggType, from, to, channelIds, fromRaw, cascade, false);
    }

    @Override
    public AggregateResult backfill(CollectorAggregateType aggType, LocalDateTime from, LocalDateTime to,
                                    Collection<Long> channelIds, boolean fromRaw, boolean cascade,
                                    boolean forceClean) {
        validateRange(from, to);
        BackfillLimit limit = limitOf(aggType);
        validateBackfillLimit(aggType, limit, from, to, channelIds);
        validateRawRetention(from, to, fromRaw);
        List<CollectorChannelDO> channels = selectChannels(aggType, channelIds);
        validateSelectedChannelCount(aggType, limit, channels.size());

        // 级联所有层的通道数必须在任何写入前一次性校验，避免下层已提交后上层才报错。
        List<CascadeTarget> cascadeTargets = new ArrayList<>();
        if (cascade) {
            for (CollectorAggregateType level : cascadeChain(aggType)) {
                List<CollectorChannelDO> levelChannels = selectChannels(level, channelIds);
                validateSelectedChannelCount(level, limitOf(level), levelChannels.size());
                cascadeTargets.add(new CascadeTarget(level, levelChannels));
            }
        }

        // 显式回填在源非空时先清空目标区间，确保源数据删除或修正后不会留下陈旧桶。
        AggregateResult result = doAggregate(aggType, from, to, channels, fromRaw, true, forceClean);
        for (CascadeTarget target : cascadeTargets) {
            // 下层已按本次请求重建，级联上层默认走覆盖度检测与 raw 回退，避免误用旧缺口。
            doAggregate(target.type(), from, to, target.channels(), false, true, forceClean);
        }
        return result;
    }

    private AggregateResult aggregate(CollectorAggregateType aggType, LocalDateTime from, LocalDateTime to,
                                      Collection<Long> channelIds, boolean fromRaw) {
        validateRange(from, to);
        return doAggregate(aggType, from, to, selectChannels(aggType, channelIds), fromRaw, false, false);
    }

    private void validateRange(LocalDateTime from, LocalDateTime to) {
        if (from == null) {
            throw exception(AGGREGATE_BACKFILL_INVALID, "开始时间不能为空");
        }
        if (to == null) {
            throw exception(AGGREGATE_BACKFILL_INVALID, "结束时间不能为空");
        }
        if (!from.isBefore(to)) {
            throw exception(AGGREGATE_BACKFILL_INVALID, "结束时间必须晚于开始时间");
        }
    }

    private void validateRawRetention(LocalDateTime from, LocalDateTime to, boolean fromRaw) {
        if (!fromRaw || rawRetentionDays <= 0) {
            return;
        }
        Duration range = Duration.between(from, to);
        Duration retention = Duration.ofDays(rawRetentionDays);
        if (range.compareTo(retention) > 0) {
            throw exception(AGGREGATE_BACKFILL_INVALID, String.format(
                    "fromRaw 回填范围不能超过 raw 保留期 %d 天（当前请求约 %s），raw 已过期，请改用分层回填",
                    rawRetentionDays, formatDuration(range)));
        }
    }

    private void validateBackfillLimit(CollectorAggregateType aggType, BackfillLimit limit,
                                       LocalDateTime from, LocalDateTime to, Collection<Long> channelIds) {
        Duration range = Duration.between(from, to);
        if (range.compareTo(limit.maxRange()) > 0) {
            throw exception(AGGREGATE_BACKFILL_INVALID, String.format(
                    "%s 单次回填不能超过 %s（当前请求约 %s），请缩小范围或分批回填",
                    aggType.getCode(), limit.maxRangeText(), formatDuration(range)));
        }
        if (channelIds != null && new LinkedHashSet<>(channelIds).size() > limit.maxChannels()) {
            throw exception(AGGREGATE_BACKFILL_INVALID, String.format(
                    "%s 单次回填通道数不能超过 %d，请指定 channelIds 分批回填",
                    aggType.getCode(), limit.maxChannels()));
        }
    }

    private void validateSelectedChannelCount(CollectorAggregateType aggType, BackfillLimit limit, int selected) {
        if (selected > limit.maxChannels()) {
            throw exception(AGGREGATE_BACKFILL_INVALID, String.format(
                    "%s 实际选中 %d 个通道，超过单次上限 %d，请指定 channelIds 分批回填",
                    aggType.getCode(), selected, limit.maxChannels()));
        }
    }

    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.minusDays(days).toHours();
        if (days == 0) {
            return Math.max(1, hours) + " 小时";
        }
        return hours == 0 ? days + " 天" : days + " 天 " + hours + " 小时";
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
                                        LocalDateTime requestedTo, List<CollectorChannelDO> channels,
                                        boolean fromRaw, boolean replace, boolean forceClean) {
        long begin = System.nanoTime();
        LocalDateTime from = aggType.floor(requestedFrom);
        LocalDateTime closedTo = aggType.floor(LocalDateTime.now(SHANGHAI_ZONE).minusSeconds(Math.max(0, graceSeconds)));
        LocalDateTime to = aggType.ceil(requestedTo);
        if (to.isAfter(closedTo)) {
            to = closedTo;
        }
        if (!from.isBefore(to) || channels.isEmpty()) {
            return new AggregateResult(aggType.getCode(), channels.size(), 0, 0, 0, elapsedMillis(begin));
        }

        long bucketCount = 0;
        long rowCount = 0;
        long skippedEmptyBatches = 0;
        for (CollectorChannelDO channel : channels) {
            Source source = resolveSource(aggType, channel, fromRaw);
            LocalDateTime cursor = from;
            while (cursor.isBefore(to)) {
                LocalDateTime batchTo = aggType.plusWindow(cursor);
                if (batchTo.isAfter(to)) {
                    batchTo = to;
                }
                BatchResult batch = aggregateBatch(aggType, source, channel, cursor, batchTo, from, to,
                        replace, forceClean);
                bucketCount += batch.bucketCount();
                rowCount += batch.rowCount();
                skippedEmptyBatches += batch.skippedEmptyBatch() ? 1 : 0;
                cursor = batchTo;
            }
        }
        return new AggregateResult(aggType.getCode(), channels.size(), bucketCount, rowCount,
                skippedEmptyBatches, elapsedMillis(begin));
    }

    /**
     * 每个通道、每个时间窗单独事务；源查询、清桶、聚合行与对应水位要么一起提交，要么一起回滚。
     * 必须先查源再删目标：源为空时不删除旧聚合，避免 raw 已过期时静默清空正确历史。
     */
    private BatchResult aggregateBatch(CollectorAggregateType aggType, Source source, CollectorChannelDO channel,
                                       LocalDateTime batchFrom, LocalDateTime batchTo,
                                       LocalDateTime backfillFrom, LocalDateTime backfillTo,
                                       boolean replace, boolean forceClean) {
        return transactionTemplate.execute(status -> {
            List<CollectorAggregateBucketDO> buckets = aggregateMapper.selectBuckets(
                    source.table(), source.timeColumn(), aggType.getBucketInterval(), source.raw(),
                    channel.getId(), batchFrom, batchTo);
            if (!source.raw()) {
                // 覆盖度回退的 raw 查询也发生在删除之前，必须把最终可用源一并算入。
                buckets = replaceIncompleteBuckets(aggType, channel, batchFrom, batchTo, buckets);
            }
            long rows = buckets.stream().mapToLong(CollectorAggregateBucketDO::getSampleCount).sum();
            boolean skippedEmptyBatch = false;
            boolean emptySource = rows == 0;
            if (emptySource && replace && !forceClean) {
                skippedEmptyBatch = true;
                log.warn("[aggregateBatch][源为空，保留旧聚合] aggType={}，channelId={}，from={}，to={}",
                        aggType.getCode(), channel.getId(), batchFrom, batchTo);
            } else {
                if (replace) {
                    aggregateMapper.deleteBuckets(aggType.getTargetTable(), aggType == CollectorAggregateType.DAY,
                            channel.getId(), batchFrom, batchTo);
                    if (emptySource) {
                        log.warn("[aggregateBatch][forceClean 以空结果清空目标区间] aggType={}，channelId={}，from={}，to={}",
                                aggType.getCode(), channel.getId(), batchFrom, batchTo);
                    }
                }
                if (!buckets.isEmpty()) {
                    aggregateMapper.upsertBuckets(aggType.getTargetTable(),
                            aggType == CollectorAggregateType.DAY, buckets);
                }
            }
            // 空窗口也推进水位，避免无数据通道每次重复扫描相同历史范围；跳过删除不影响审计区间。
            aggregateMapper.upsertWatermark(aggType.getCode(), channel.getId(), channel.getDeviceId(),
                    batchTo, backfillFrom, backfillTo);
            return new BatchResult(buckets.size(), rows, skippedEmptyBatch);
        });
    }

    /**
     * 上卷前按目标桶检查下层连续桶数；缺口桶（切换画像、下层任务失败或尚未执行）整桶回退 raw，
     * 避免把不完整下层数据静默上卷。目标桶必须完整落在本次查询范围内，否则同样回退。
     *
     * <p>覆盖度只看下层桶“行数”而不看内容新旧；下层已有桶但数据迟到且超过任务窗口时不会自动
     * 识别，需要手工回填对应区间。</p>
     */
    private List<CollectorAggregateBucketDO> replaceIncompleteBuckets(
            CollectorAggregateType aggType, CollectorChannelDO channel,
            LocalDateTime batchFrom, LocalDateTime batchTo, List<CollectorAggregateBucketDO> rollupBuckets) {
        int expectedChildBuckets = expectedChildBuckets(aggType);
        Map<LocalDateTime, CollectorAggregateBucketDO> rollupByBucket = new TreeMap<>();
        for (CollectorAggregateBucketDO bucket : rollupBuckets) {
            rollupByBucket.put(bucket.getBucketTime(), bucket);
        }

        // 必须枚举目标时间轴，而不是只遍历已有上卷行：下层完全缺桶时根本没有行可判断。
        // 连续缺口合并成一次 raw 查询，避免稀疏通道逐桶产生大量 SQL。
        List<CollectorAggregateBucketDO> merged = new ArrayList<>(rollupBuckets.size());
        LocalDateTime cursor = batchFrom;
        LocalDateTime gapFrom = null;
        LocalDateTime gapTo = null;
        while (cursor.isBefore(batchTo)) {
            LocalDateTime candidateEnd = aggType.plus(cursor);
            LocalDateTime bucketEnd = candidateEnd.isAfter(batchTo) ? batchTo : candidateEnd;
            CollectorAggregateBucketDO rollup = rollupByBucket.get(cursor);
            boolean complete = rollup != null && candidateEnd.equals(bucketEnd)
                    && rollup.getSourceBucketCount() != null
                    && rollup.getSourceBucketCount() >= expectedChildBuckets;
            if (complete) {
                if (gapFrom != null) {
                    merged.addAll(selectRawBuckets(aggType, channel, gapFrom, gapTo));
                    gapFrom = null;
                }
                merged.add(rollup);
            } else {
                if (gapFrom == null) {
                    gapFrom = cursor;
                }
                gapTo = bucketEnd;
            }
            cursor = bucketEnd;
        }
        if (gapFrom != null) {
            merged.addAll(selectRawBuckets(aggType, channel, gapFrom, gapTo));
        }
        return merged;
    }

    private List<CollectorAggregateBucketDO> selectRawBuckets(CollectorAggregateType aggType,
                                                               CollectorChannelDO channel,
                                                               LocalDateTime from, LocalDateTime to) {
        return aggregateMapper.selectBuckets(RAW_TABLE, RAW_TIME_COLUMN, aggType.getBucketInterval(), true,
                channel.getId(), from, to);
    }

    private int expectedChildBuckets(CollectorAggregateType parent) {
        return switch (parent) {
            case SECOND -> 0;
            case MINUTE -> 60;
            case HOUR -> 60;
            case DAY -> 24;
        };
    }

    private Source resolveSource(CollectorAggregateType aggType, CollectorChannelDO channel, boolean fromRaw) {
        if (fromRaw || aggType == CollectorAggregateType.SECOND
                || (aggType == CollectorAggregateType.MINUTE && !DATA_PROFILE_FAST.equals(channel.getDataProfile()))) {
            return new Source(RAW_TABLE, RAW_TIME_COLUMN, true);
        }
        return switch (aggType) {
            case MINUTE -> new Source("collector_aggregate_second", "bucket_time", false);
            case HOUR -> new Source("collector_aggregate_minute", "bucket_time", false);
            case DAY -> new Source("collector_aggregate_hour", "bucket_time", false);
            case SECOND -> throw new IllegalStateException("秒聚合只能从 raw 计算");
        };
    }

    private BackfillLimit limitOf(CollectorAggregateType aggType) {
        return switch (aggType) {
            case SECOND -> new BackfillLimit(Duration.ofHours(6), 5, "6 小时");
            case MINUTE -> new BackfillLimit(Duration.ofDays(2), 20, "2 天");
            case HOUR, DAY -> new BackfillLimit(Duration.ofDays(30), 50, "30 天");
        };
    }

    private List<CollectorAggregateType> cascadeChain(CollectorAggregateType aggType) {
        return switch (aggType) {
            case SECOND -> List.of(CollectorAggregateType.MINUTE, CollectorAggregateType.HOUR,
                    CollectorAggregateType.DAY);
            case MINUTE -> List.of(CollectorAggregateType.HOUR, CollectorAggregateType.DAY);
            case HOUR -> List.of(CollectorAggregateType.DAY);
            case DAY -> List.of();
        };
    }

    private long elapsedMillis(long begin) {
        return (System.nanoTime() - begin) / 1_000_000;
    }

    private record Source(String table, String timeColumn, boolean raw) {
    }

    private record BatchResult(long bucketCount, long rowCount, boolean skippedEmptyBatch) {
    }

    private record BackfillLimit(Duration maxRange, int maxChannels, String maxRangeText) {
    }

    private record CascadeTarget(CollectorAggregateType type, List<CollectorChannelDO> channels) {
    }

}
