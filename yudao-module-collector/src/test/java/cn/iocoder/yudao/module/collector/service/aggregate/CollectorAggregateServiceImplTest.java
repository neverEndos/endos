package cn.iocoder.yudao.module.collector.service.aggregate;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.collector.dal.dataobject.aggregate.CollectorAggregateBucketDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.channel.CollectorChannelDO;
import cn.iocoder.yudao.module.collector.enums.CollectorAggregateType;
import org.junit.Test;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.AGGREGATE_BACKFILL_INVALID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * 聚合数学、质量码、valid_count 口径、幂等、迟到重算、覆盖度回退、显式回填清桶/级联与水位。
 * 需要 COLLECTOR_TEST_PG_URL / USER / PASSWORD 指向可清空的临时库。
 */
public class CollectorAggregateServiceImplTest extends CollectorAggregatePgTestSupport {

    @Test
    public void aggregateSecond_handlesQualityNullEmptyAndSingleValueBuckets() throws Exception {
        setChannels(channel(1, 7, "fast"), channel(2, 7, "normal"));
        insertRaw(1, 7, BASE_TIME, 10D, 0);
        insertRaw(1, 7, BASE_TIME.plusNanos(20_000_000), 20D, 0);
        insertRaw(1, 7, BASE_TIME.plusNanos(40_000_000), null, 0);
        insertRaw(1, 7, BASE_TIME.plusNanos(60_000_000), 999D, 1);
        insertRaw(1, 7, BASE_TIME.plusNanos(80_000_000), 1000D, 2);
        insertRaw(1, 7, BASE_TIME.plusSeconds(1), 100D, 2);
        insertRaw(1, 7, BASE_TIME.plusSeconds(1).plusNanos(10_000_000), 200D, 1);
        insertRaw(1, 7, BASE_TIME.plusSeconds(2), 7D, 0);
        // normal 通道参与过滤验证，但不能写入 second 表。
        insertRaw(2, 7, BASE_TIME, 11D, 0);
        insertRaw(2, 7, BASE_TIME.plusSeconds(1), 12D, 0);

        CollectorAggregateService.AggregateResult result = aggregateService.aggregateSecond(
                BASE_TIME, BASE_TIME.plusSeconds(4), null);
        assertEquals("second 只处理 fast 通道", 1, result.channelCount());
        assertEquals(3, result.bucketCount());
        assertEquals(8, result.rowCount());
        assertEquals(3, countRows("collector_aggregate_second"));

        List<CollectorAggregateBucketDO> buckets = selectBuckets("collector_aggregate_second", false);

        CollectorAggregateBucketDO mixed = buckets.get(0);
        assertEquals(BASE_TIME, mixed.getBucketTime());
        assertEquals(Integer.valueOf(5), mixed.getSampleCount());
        assertEquals("good_count 统计所有 quality=0，包含 value 为 NULL 的行", Integer.valueOf(3), mixed.getGoodCount());
        assertEquals("valid_count 只统计 quality=0 且 value 非 NULL 的行", Integer.valueOf(2),
                mixed.getValidCount());
        assertEquals(Integer.valueOf(1), mixed.getBadCount());
        assertEquals("avg 分母必须是 valid_count，而不是包含 NULL 的 good_count", 15D,
                mixed.getValueAvg(), 0D);
        assertEquals(10D, mixed.getValueMin(), 0D);
        assertEquals(20D, mixed.getValueMax(), 0D);
        assertEquals(30D, mixed.getValueSum(), 0D);
        assertEquals(10D, mixed.getValueFirst(), 0D);
        assertEquals(20D, mixed.getValueLast(), 0D);

        CollectorAggregateBucketDO allBad = buckets.get(1);
        assertEquals(BASE_TIME.plusSeconds(1), allBad.getBucketTime());
        assertEquals(Integer.valueOf(2), allBad.getSampleCount());
        assertEquals(Integer.valueOf(0), allBad.getGoodCount());
        assertEquals(Integer.valueOf(0), allBad.getValidCount());
        assertEquals(Integer.valueOf(1), allBad.getBadCount());
        assertNull(allBad.getValueAvg());
        assertNull(allBad.getValueMin());
        assertNull(allBad.getValueMax());
        assertNull(allBad.getValueSum());
        assertNull(allBad.getValueFirst());
        assertNull(allBad.getValueLast());

        CollectorAggregateBucketDO single = buckets.get(2);
        assertEquals(BASE_TIME.plusSeconds(2), single.getBucketTime());
        assertEquals(Integer.valueOf(1), single.getSampleCount());
        assertEquals(Integer.valueOf(1), single.getValidCount());
        assertEquals(7D, single.getValueAvg(), 0D);
        assertEquals(7D, single.getValueFirst(), 0D);
        assertEquals(7D, single.getValueLast(), 0D);

        assertWatermark("second", 1, BASE_TIME.plusSeconds(4), BASE_TIME, BASE_TIME.plusSeconds(4));
        assertWatermarkCount(1);
    }

    @Test
    public void aggregateSecond_isIdempotentAndRecalculatesLateDataAndWatermark() throws Exception {
        setChannels(channel(1, 7, "fast"));
        insertRaw(1, 7, BASE_TIME, 10D, 0);
        insertRaw(1, 7, BASE_TIME.plusNanos(40_000_000), null, 0);
        insertRaw(1, 7, BASE_TIME.plusNanos(60_000_000), 100D, 2);

        aggregateService.aggregateSecond(BASE_TIME, BASE_TIME.plusSeconds(2), null);
        assertEquals(1, countRows("collector_aggregate_second"));
        // 同一范围重跑必须 upsert 覆盖，不增加行数。
        aggregateService.aggregateSecond(BASE_TIME, BASE_TIME.plusSeconds(2), null);
        assertEquals(1, countRows("collector_aggregate_second"));

        insertRaw(1, 7, BASE_TIME.plusNanos(80_000_000), 30D, 0);
        CollectorAggregateService.AggregateResult late = aggregateService.aggregateSecond(
                BASE_TIME, BASE_TIME.plusSeconds(2), null);
        assertEquals(1, late.bucketCount());
        assertEquals(4, late.rowCount());
        assertEquals(1, countRows("collector_aggregate_second"));
        CollectorAggregateBucketDO bucket = selectBuckets("collector_aggregate_second", false).get(0);
        assertEquals(Integer.valueOf(4), bucket.getSampleCount());
        assertEquals(Integer.valueOf(3), bucket.getGoodCount());
        assertEquals(Integer.valueOf(2), bucket.getValidCount());
        assertEquals(Integer.valueOf(1), bucket.getBadCount());
        assertEquals(20D, bucket.getValueAvg(), 1e-9);
        assertEquals(10D, bucket.getValueFirst(), 0D);
        assertEquals(30D, bucket.getValueLast(), 0D);

        // 重算较老范围时水位不能回退，但仍记录本次回填范围。
        aggregateService.aggregateSecond(BASE_TIME, BASE_TIME.plusSeconds(1), null);
        assertWatermark("second", 1, BASE_TIME.plusSeconds(2), BASE_TIME, BASE_TIME.plusSeconds(1));
    }

    @Test
    public void aggregateChain_rollupMathEqualsRawAndWritesAllLevels() throws Exception {
        setChannels(channel(1, 7, "fast"), channel(2, 8, "normal"));
        insertRaw(1, 7, BASE_TIME, 1D, 0);
        insertRaw(1, 7, BASE_TIME.plusSeconds(1), 3D, 0);
        insertRaw(1, 7, BASE_TIME.plusSeconds(1).plusNanos(20_000_000), 100D, 1);
        insertRaw(1, 7, BASE_TIME.plusSeconds(2), 4D, 0);
        insertRaw(1, 7, BASE_TIME.plusSeconds(2).plusNanos(20_000_000), 200D, 2);
        insertRaw(1, 7, BASE_TIME.plusHours(1), 5D, 0);
        insertRaw(1, 7, BASE_TIME.plusDays(1).plusHours(3), null, 0);
        insertRaw(1, 7, BASE_TIME.plusDays(1).plusHours(3).plusSeconds(1), 6D, 0);
        insertRaw(1, 7, BASE_TIME.plusDays(1).plusHours(3).plusSeconds(2), 7D, 0);

        insertRaw(2, 8, BASE_TIME.plusSeconds(10), 10D, 0);
        insertRaw(2, 8, BASE_TIME.plusMinutes(59).plusSeconds(59), 20D, 0);
        insertRaw(2, 8, BASE_TIME.plusHours(1).plusMinutes(30), 40D, 2);
        insertRaw(2, 8, BASE_TIME.plusDays(1).plusHours(2), 5D, 0);
        insertRaw(2, 8, BASE_TIME.plusDays(1).plusHours(2).plusSeconds(10), 15D, 0);

        LocalDateTime from = BASE_TIME;
        LocalDateTime to = BASE_TIME.plusDays(2);
        aggregateService.aggregateSecond(from, to, null);
        aggregateService.aggregateMinute(from, to, null);
        aggregateService.aggregateHour(from, to, null);
        aggregateService.aggregateDay(from, to, null);

        // 先保存上卷结果，再切 raw 直算，逐列比对加权 avg、min/max、sum、first/last 和计数。
        List<CollectorAggregateBucketDO> minuteChain = selectBuckets("collector_aggregate_minute", false);
        List<CollectorAggregateBucketDO> hourChain = selectBuckets("collector_aggregate_hour", false);
        List<CollectorAggregateBucketDO> dayChain = selectBuckets("collector_aggregate_day", true);
        assertEquals(7, minuteChain.size());
        assertEquals(6, hourChain.size());
        assertEquals(4, dayChain.size());

        aggregateService.aggregateMinute(from, to, null, true);
        assertBucketsEqual(selectRawExpected("minute", from, to, List.of(1L, 2L)),
                selectBuckets("collector_aggregate_minute", false));
        assertBucketsEqual(minuteChain, selectBuckets("collector_aggregate_minute", false));

        aggregateService.aggregateHour(from, to, null, true);
        assertBucketsEqual(selectRawExpected("hour", from, to, List.of(1L, 2L)),
                selectBuckets("collector_aggregate_hour", false));
        assertBucketsEqual(hourChain, selectBuckets("collector_aggregate_hour", false));

        aggregateService.aggregateDay(from, to, null, true);
        assertBucketsEqual(selectRawExpected("day", from, to, List.of(1L, 2L)),
                selectBuckets("collector_aggregate_day", true));
        assertBucketsEqual(dayChain, selectBuckets("collector_aggregate_day", true));

        // 秒表也应与 raw 直算完全一致。
        assertBucketsEqual(selectRawExpected("second", from, to, List.of(1L)),
                selectBuckets("collector_aggregate_second", false));
        assertEquals(7, countRows("collector_aggregate_second"));
        assertTrue("秒表不能包含 normal 通道", selectBuckets("collector_aggregate_second", false).stream()
                .allMatch(row -> row.getChannelId() == 1L));
    }

    @Test
    public void minuteFast_fallsBackToRawAcrossProfileSwitchAndPartialSecondCoverage() throws Exception {
        setChannels(channel(1, 7, "fast"));
        executeSql("""
                INSERT INTO collector_sample_raw (channel_id, device_id, collect_time, value, quality)
                SELECT 1, 7, TIMESTAMP '2026-01-01 00:00:00' + (i * interval '1 second'),
                    (i + 1)::double precision, 0
                FROM generate_series(0, 119) i
                """);

        // 模拟 normal -> fast 切换：第一分钟只有后半分钟才有秒桶，第二分钟完整 60 个。
        aggregateService.aggregateSecond(BASE_TIME.plusSeconds(30), BASE_TIME.plusMinutes(2), List.of(1L));
        assertEquals(90, countRows("collector_aggregate_second"));

        aggregateService.aggregateMinute(BASE_TIME, BASE_TIME.plusMinutes(2), List.of(1L));
        assertEquals("两个分钟桶都应存在", 2, countRows("collector_aggregate_minute"));
        assertBucketsEqual(selectRawExpected("minute", BASE_TIME, BASE_TIME.plusMinutes(2), List.of(1L)),
                selectBuckets("collector_aggregate_minute", false));
    }

    @Test
    public void hourAndDay_fallBackToRawWhenChildCoverageMissing() throws Exception {
        setChannels(channel(1, 8, "normal"));
        // 每分钟 2 条样本，quality/value 有差异，上卷时必须按 valid_count 加权。
        executeSql("""
                INSERT INTO collector_sample_raw (channel_id, device_id, collect_time, value, quality)
                SELECT 1, 8,
                    TIMESTAMP '2026-01-01 00:00:00' + (i * interval '1 minute') + (j * interval '30 seconds'),
                    CASE WHEN (i * 2 + j) % 11 = 0 THEN NULL ELSE (i + j + 1)::double precision END,
                    CASE WHEN (i * 2 + j) % 13 = 0 THEN 2 ELSE 0 END
                FROM generate_series(0, 1439) i CROSS JOIN generate_series(0, 1) j
                """);
        LocalDateTime dayEnd = BASE_TIME.plusDays(1);

        aggregateService.aggregateMinute(BASE_TIME, dayEnd, List.of(1L));
        assertEquals(1_440, countRows("collector_aggregate_minute"));

        // 删掉整个 02 点的分钟桶，小时层该桶必须回退 raw，其余 60 个分钟桶完整上卷。
        executeSql("""
                DELETE FROM collector_aggregate_minute
                WHERE bucket_time >= TIMESTAMP '2026-01-01 02:00:00'
                  AND bucket_time < TIMESTAMP '2026-01-01 03:00:00'
                """);
        aggregateService.aggregateHour(BASE_TIME, dayEnd, List.of(1L));
        assertEquals(24, countRows("collector_aggregate_hour"));
        assertBucketsEqual(selectRawExpected("hour", BASE_TIME, dayEnd, List.of(1L)),
                selectBuckets("collector_aggregate_hour", false));

        // 小时层少一个桶（23/24），day 唯一目标桶必须整桶回退 raw。
        executeSql("""
                DELETE FROM collector_aggregate_hour
                WHERE bucket_time >= TIMESTAMP '2026-01-01 05:00:00'
                  AND bucket_time < TIMESTAMP '2026-01-01 06:00:00'
                """);
        aggregateService.aggregateDay(BASE_TIME, dayEnd, List.of(1L));
        assertEquals(1, countRows("collector_aggregate_day"));
        assertBucketsEqual(selectRawExpected("day", BASE_TIME, dayEnd, List.of(1L)),
                selectBuckets("collector_aggregate_day", true));
    }

    @Test
    public void backfill_clearsStaleBucketsAndCascadesAllLevels() throws Exception {
        setChannels(channel(1, 7, "fast"));
        executeSql("""
                INSERT INTO collector_sample_raw (channel_id, device_id, collect_time, value, quality)
                SELECT 1, 7, TIMESTAMP '2026-01-01 00:00:00' + (i * interval '1 second'),
                    (i + 1)::double precision, 0
                FROM generate_series(0, 59) i
                """);

        // 目标区间内、但没有任何 raw 的重叠桶：显式回填必须先清掉，而不是 upsert 保留。
        insertStaleBucket("collector_aggregate_second", false);
        insertStaleBucket("collector_aggregate_minute", false);
        insertStaleBucket("collector_aggregate_hour", false);
        insertStaleBucket("collector_aggregate_day", true);

        LocalDateTime to = BASE_TIME.plusMinutes(2);
        aggregateService.backfill(CollectorAggregateType.SECOND, BASE_TIME, to, List.of(1L), false, true);

        List<Long> ids = List.of(1L);
        assertBucketsEqual(selectRawExpected("second", BASE_TIME, to, ids),
                selectBuckets("collector_aggregate_second", false));
        assertBucketsEqual(selectRawExpected("minute", BASE_TIME, to, ids),
                selectBuckets("collector_aggregate_minute", false));
        assertBucketsEqual(selectRawExpected("hour", BASE_TIME, to, ids),
                selectBuckets("collector_aggregate_hour", false));
        assertBucketsEqual(selectRawExpected("day", BASE_TIME, to, ids),
                selectBuckets("collector_aggregate_day", true));
        assertEquals("无 raw 的 90 秒陈旧秒桶必须被清掉", 60, countRows("collector_aggregate_second"));
        assertEquals("无下层数据的分钟桶必须被清掉", 1, countRows("collector_aggregate_minute"));

        // 给第二分钟补 raw 并级联重建，再删除第二分钟 raw；显式分钟回填必须移除已经失效的分钟桶。
        executeSql("""
                INSERT INTO collector_sample_raw (channel_id, device_id, collect_time, value, quality)
                SELECT 1, 7, TIMESTAMP '2026-01-01 00:01:00' + (i * interval '1 second'),
                    (i + 1)::double precision, 0
                FROM generate_series(0, 59) i
                """);
        aggregateService.backfill(CollectorAggregateType.SECOND, BASE_TIME, to, List.of(1L), false, true);
        assertEquals(2, countRows("collector_aggregate_minute"));

        executeSql("""
                DELETE FROM collector_sample_raw
                WHERE collect_time >= TIMESTAMP '2026-01-01 00:01:00'
                """);
        aggregateService.backfill(CollectorAggregateType.MINUTE, BASE_TIME, to, List.of(1L), true, false);
        assertEquals("源数据删除后目标分钟桶必须消失", 1, countRows("collector_aggregate_minute"));
        assertBucketsEqual(selectRawExpected("minute", BASE_TIME, to, ids),
                selectBuckets("collector_aggregate_minute", false));
    }

    @Test
    public void backfill_rejectsExpiredRawRangeAndPerTypeLimits() throws Exception {
        setChannels(channel(1, 7, "fast"));

        ServiceException nullFrom = assertThrows(ServiceException.class, () -> aggregateService.backfill(
                CollectorAggregateType.MINUTE, null, BASE_TIME.plusMinutes(1), null, false));
        assertEquals(AGGREGATE_BACKFILL_INVALID.getCode(), nullFrom.getCode());
        ServiceException nullTo = assertThrows(ServiceException.class, () -> aggregateService.backfill(
                CollectorAggregateType.MINUTE, BASE_TIME, null, null, false));
        assertEquals(AGGREGATE_BACKFILL_INVALID.getCode(), nullTo.getCode());

        // second：最长 6 小时、最多 5 通道；错误信息必须给出现行上限且不暴露格式化占位符。
        ServiceException durationLimit = assertThrows(ServiceException.class, () -> aggregateService.backfill(
                CollectorAggregateType.SECOND, BASE_TIME, BASE_TIME.plusHours(7), null, false));
        assertEquals(AGGREGATE_BACKFILL_INVALID.getCode(), durationLimit.getCode());
        assertTrue(durationLimit.getMessage().contains("6 小时"));
        assertFalse(durationLimit.getMessage().contains("%s"));
        assertFalse(durationLimit.getMessage().contains("%d"));
        assertBackfillInvalid(() -> aggregateService.backfill(
                CollectorAggregateType.SECOND, BASE_TIME, BASE_TIME.plusMinutes(2), ids(1, 6), false));

        // minute：最长 2 天、最多 20 通道。
        assertBackfillInvalid(() -> aggregateService.backfill(
                CollectorAggregateType.MINUTE, BASE_TIME, BASE_TIME.plusDays(3), null, false));
        assertBackfillInvalid(() -> aggregateService.backfill(
                CollectorAggregateType.MINUTE, BASE_TIME, BASE_TIME.plusMinutes(2), ids(1, 21), false));

        // hour / day：最长 30 天、最多 50 通道。
        assertBackfillInvalid(() -> aggregateService.backfill(
                CollectorAggregateType.HOUR, BASE_TIME, BASE_TIME.plusDays(31), null, false));
        assertBackfillInvalid(() -> aggregateService.backfill(
                CollectorAggregateType.DAY, BASE_TIME, BASE_TIME.plusDays(31), null, false));

        // 缩短 raw 保留期后，fromRaw 的 2 天范围虽满足 minute 上限，仍必须因 raw 过期被拒绝。
        setRawRetentionDays(1);
        ServiceException rawExpired = assertThrows(ServiceException.class, () -> aggregateService.backfill(
                CollectorAggregateType.MINUTE, BASE_TIME, BASE_TIME.plusDays(2), null, true, false));
        assertEquals(AGGREGATE_BACKFILL_INVALID.getCode(), rawExpired.getCode());
        assertTrue(rawExpired.getMessage().contains("raw 已过期"));
        assertFalse(rawExpired.getMessage().contains("%s"));
        assertFalse(rawExpired.getMessage().contains("%d"));
    }

    @Test
    public void backfill_skipsEmptySourceUnlessForceClean() throws Exception {
        setChannels(channel(1, 7, "fast"));
        executeSql("""
                INSERT INTO collector_sample_raw (channel_id, device_id, collect_time, value, quality)
                SELECT 1, 7, TIMESTAMP '2026-01-01 00:00:00' + (i * interval '1 second'),
                    (i + 1)::double precision, 0
                FROM generate_series(0, 59) i
                """);
        LocalDateTime to = BASE_TIME.plusMinutes(2);
        List<Long> ids = List.of(1L);

        CollectorAggregateService.AggregateResult seeded = aggregateService.backfill(
                CollectorAggregateType.SECOND, BASE_TIME, to, ids, false, false);
        assertEquals(60, seeded.bucketCount());
        assertEquals(0, seeded.skippedEmptyBatches());
        assertEquals(60, countRows("collector_aggregate_second"));

        executeSql("DELETE FROM collector_sample_raw WHERE channel_id = 1");
        CollectorAggregateService.AggregateResult skipped = aggregateService.backfill(
                CollectorAggregateType.SECOND, BASE_TIME, to, ids, true, false, false);
        assertEquals("源为空时不得写入空批次", 0, skipped.bucketCount());
        assertEquals("跳过的批次数必须返回", 1, skipped.skippedEmptyBatches());
        assertEquals("源为空时必须保留旧聚合", 60, countRows("collector_aggregate_second"));

        CollectorAggregateService.AggregateResult cleaned = aggregateService.backfill(
                CollectorAggregateType.SECOND, BASE_TIME, to, ids, true, false, true);
        assertEquals("forceClean 不计入跳过批次", 0, cleaned.skippedEmptyBatches());
        assertEquals("forceClean 才允许以空结果清空目标区间", 0, countRows("collector_aggregate_second"));
    }

    @Test
    public void backfill_upperLayerKeepsOldRowsWhenSourceGap() throws Exception {
        setChannels(channel(1, 7, "normal"));
        executeSql("""
                INSERT INTO collector_aggregate_hour
                    (channel_id, device_id, bucket_time, sample_count, good_count, valid_count,
                     bad_count, value_avg, value_min, value_max, value_sum, value_first, value_last)
                VALUES (1, 7, TIMESTAMP '2026-01-01 00:00:00', 999, 999, 999, 0, 999, 999, 999, 999999, 999, 999)
                """);

        LocalDateTime to = BASE_TIME.plusHours(1);
        CollectorAggregateService.AggregateResult result = aggregateService.backfill(
                CollectorAggregateType.HOUR, BASE_TIME, to, List.of(1L), false, false, false);
        assertEquals("小时层无分钟源且 raw 也为空，必须跳过该批", 1, result.skippedEmptyBatches());
        assertEquals("旧小时聚合不能因下层缺口被删除", 1, countRows("collector_aggregate_hour"));
        assertEquals(Integer.valueOf(999), selectBuckets("collector_aggregate_hour", false)
                .get(0).getSampleCount());
    }

    @Test
    public void cascade_preValidatesAllLevelChannelsBeforeWriting() throws Exception {
        List<CollectorChannelDO> channels = new ArrayList<>();
        for (long id = 1; id <= 5; id++) {
            channels.add(channel(id, 7, "fast"));
        }
        for (long id = 6; id <= 55; id++) {
            channels.add(channel(id, 7, "normal"));
        }
        setChannels(channels.toArray(CollectorChannelDO[]::new));
        insertRaw(1, 7, BASE_TIME, 1D, 0);

        // second 根层只有 5 个 fast 通道，满足上限；minute 级联层会选中 55 个通道，必须预校验失败。
        ServiceException exception = assertThrows(ServiceException.class, () -> aggregateService.backfill(
                CollectorAggregateType.SECOND, BASE_TIME, BASE_TIME.plusMinutes(1), null, false, true));
        assertEquals(AGGREGATE_BACKFILL_INVALID.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("minute"));
        assertTrue(exception.getMessage().contains("20"));
        assertFalse(exception.getMessage().contains("%d"));
        assertEquals("级联预校验失败前不能写入根层", 0, countRows("collector_aggregate_second"));
        assertEquals(0, countRows("collector_aggregate_minute"));
    }

    private void insertStaleBucket(String table, boolean day) throws Exception {
        executeSql("""
                INSERT INTO %s (channel_id, device_id, %s, sample_count, good_count, valid_count,
                    bad_count, value_avg, value_min, value_max, value_sum, value_first, value_last)
                VALUES (1, 7, %s, 999, 999, 999, 0, 999, 999, 999, 999999, 999, 999)
                """.formatted(table, day ? "bucket_date" : "bucket_time",
                day ? "DATE '2026-01-01'" : "TIMESTAMP '2026-01-01 00:01:30'"));
    }

    private void assertBackfillInvalid(org.junit.function.ThrowingRunnable runnable) {
        ServiceException exception = assertThrows(ServiceException.class, runnable);
        assertEquals(AGGREGATE_BACKFILL_INVALID.getCode(), exception.getCode());
    }

    private void setRawRetentionDays(long days) throws Exception {
        Field field = CollectorAggregateServiceImpl.class.getDeclaredField("rawRetentionDays");
        field.setAccessible(true);
        field.setLong(aggregateService, days);
    }

    private List<Long> ids(long from, long to) {
        List<Long> ids = new ArrayList<>();
        for (long id = from; id <= to; id++) {
            ids.add(id);
        }
        return ids;
    }

    private void assertWatermark(String aggType, long channelId, LocalDateTime expectedWatermark,
                                  LocalDateTime expectedFrom, LocalDateTime expectedTo) throws Exception {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT watermark_time, backfill_from, backfill_to, status, retry_count, last_error
                     FROM collector_aggregate_watermark
                     WHERE agg_type = '%s' AND channel_id = %d
                     """.formatted(aggType, channelId))) {
            rs.next();
            assertEquals(expectedWatermark, rs.getObject("watermark_time", LocalDateTime.class));
            assertEquals(expectedFrom, rs.getObject("backfill_from", LocalDateTime.class));
            assertEquals(expectedTo, rs.getObject("backfill_to", LocalDateTime.class));
            assertEquals(0, rs.getInt("status"));
            assertEquals(0, rs.getInt("retry_count"));
            assertNull(rs.getString("last_error"));
        }
    }

    private void assertWatermarkCount(int expected) throws Exception {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM collector_aggregate_watermark")) {
            rs.next();
            assertEquals(expected, rs.getInt(1));
        }
    }

}
