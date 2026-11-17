package cn.iocoder.yudao.module.collector.service.aggregate;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.collector.dal.dataobject.aggregate.CollectorAggregateBucketDO;
import cn.iocoder.yudao.module.collector.enums.CollectorAggregateType;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * 聚合数学、质量码、幂等、迟到重算、per-profile 过滤、水位与 raw 对账。
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
        assertEquals(Integer.valueOf(1), mixed.getBadCount());
        assertEquals(10D, mixed.getValueAvg(), 0D);
        assertEquals(10D, mixed.getValueMin(), 0D);
        assertEquals(20D, mixed.getValueMax(), 0D);
        assertEquals(30D, mixed.getValueSum(), 0D);
        assertEquals(10D, mixed.getValueFirst(), 0D);
        assertEquals(20D, mixed.getValueLast(), 0D);

        CollectorAggregateBucketDO allBad = buckets.get(1);
        assertEquals(BASE_TIME.plusSeconds(1), allBad.getBucketTime());
        assertEquals(Integer.valueOf(2), allBad.getSampleCount());
        assertEquals(Integer.valueOf(0), allBad.getGoodCount());
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
        assertEquals(Integer.valueOf(1), bucket.getBadCount());
        assertEquals(40D / 3D, bucket.getValueAvg(), 1e-9);
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
    public void backfill_rejectsRangeOverSevenDaysAndMoreThanFiftyChannels() throws Exception {
        setChannels(channel(1, 7, "fast"));
        ServiceException tooLong = assertThrows(ServiceException.class, () -> aggregateService.backfill(
                CollectorAggregateType.DAY, BASE_TIME, BASE_TIME.plusDays(8), null, false));
        assertEquals(1_023_006_000, tooLong.getCode().intValue());

        List<Long> channelIds = new ArrayList<>();
        for (long id = 1; id <= 51; id++) {
            channelIds.add(id);
        }
        ServiceException tooMany = assertThrows(ServiceException.class, () -> aggregateService.backfill(
                CollectorAggregateType.MINUTE, BASE_TIME, BASE_TIME.plusDays(1), channelIds, false));
        assertEquals(1_023_006_000, tooMany.getCode().intValue());
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
