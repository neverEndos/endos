package cn.iocoder.yudao.module.collector.service.trend;

import cn.iocoder.yudao.module.collector.controller.admin.trend.vo.CollectorTrendSeriesReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.trend.vo.CollectorTrendSeriesRespVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.trend.CollectorTrendBucketRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.trend.CollectorTrendRawPointRow;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 在临时 PostgreSQL 上验证趋势 raw/聚合各层 SQL 与直接统计一致，
 * 并验证 Service 的自动选源分组查询。需要 COLLECTOR_TEST_PG_URL / USER / PASSWORD。
 */
public class CollectorTrendPgTest extends CollectorTrendPgTestSupport {

    @Test
    public void rawBuckets_onlyCountGoodNonNullSamples() throws Exception {
        // 主键为 (channel_id, collect_time)，同一秒内用毫秒偏移写入多条样本
        insertRaw(1, 7, BASE_TIME, 10D, 0);
        insertRaw(1, 7, BASE_TIME.plusNanos(1_000_000), 20D, 0);
        insertRaw(1, 7, BASE_TIME.plusNanos(2_000_000), null, 0);
        insertRaw(1, 7, BASE_TIME.plusNanos(3_000_000), 500D, 1);
        insertRaw(1, 7, BASE_TIME.plusNanos(4_000_000), 999D, 2);
        insertRaw(1, 7, BASE_TIME.plusSeconds(1), 30D, 0);
        insertRaw(1, 7, BASE_TIME.plusSeconds(1).plusNanos(1_000_000), 40D, 0);
        insertRaw(1, 7, BASE_TIME.plusSeconds(2), 50D, 0);
        // 其他通道的数据不能串入结果
        insertRaw(2, 7, BASE_TIME, 88D, 0);

        List<CollectorTrendBucketRow> actual = trendMapper.selectRawBuckets(
                1, List.of(1L), BASE_TIME, BASE_TIME.plusSeconds(3));

        assertEquals(3, actual.size());
        assertBucket(actual.get(0), 1L, BASE_TIME, 15D, 10D, 20D, 2);
        assertBucket(actual.get(1), 1L, BASE_TIME.plusSeconds(1), 35D, 30D, 40D, 2);
        assertBucket(actual.get(2), 1L, BASE_TIME.plusSeconds(2), 50D, 50D, 50D, 1);

        List<CollectorTrendBucketRow> direct = selectBucketsBySql("""
                SELECT channel_id,
                    date_bin(INTERVAL '1 second' * 1, collect_time, TIMESTAMP '2000-01-01 00:00:00') AS bucket_time,
                    AVG(value) AS avg, MIN(value) AS min, MAX(value) AS max, COUNT(*)::integer AS count
                FROM collector_sample_raw
                WHERE channel_id IN (1) AND collect_time >= '2026-01-01 00:00:00'
                  AND collect_time < '2026-01-01 00:00:03' AND quality = 0 AND value IS NOT NULL
                GROUP BY 1, 2 ORDER BY 1, 2
                """);
        assertBucketsEqual(direct, actual);
    }

    @Test
    public void aggregateBuckets_rebucketAverageWeightedByValidCount() throws Exception {
        insertAggregate("collector_aggregate_minute", 1, 7, BASE_TIME, 10, 4, 40D, 5D, 15D);
        insertAggregate("collector_aggregate_minute", 1, 7, BASE_TIME.plusMinutes(1), 12, 6, 90D, 12D, 20D);
        insertAggregate("collector_aggregate_minute", 1, 7, BASE_TIME.plusMinutes(2), 5, 0, null, null, null);

        List<CollectorTrendBucketRow> actual = trendMapper.selectAggregateBuckets(
                "collector_aggregate_minute", 180, List.of(1L),
                BASE_TIME, BASE_TIME.plusMinutes(4));

        assertEquals(1, actual.size());
        assertBucket(actual.get(0), 1L, BASE_TIME, 13D, 5D, 20D, 10);

        List<CollectorTrendBucketRow> direct = selectBucketsBySql("""
                SELECT channel_id,
                    date_bin(INTERVAL '1 second' * 180, bucket_time, TIMESTAMP '2000-01-01 00:00:00') AS bucket_time,
                    SUM(value_sum) / NULLIF(SUM(valid_count), 0) AS avg,
                    MIN(value_min) AS min, MAX(value_max) AS max, SUM(valid_count)::integer AS count
                FROM collector_aggregate_minute
                WHERE channel_id IN (1) AND bucket_time >= '2026-01-01 00:00:00'
                  AND bucket_time < '2026-01-01 00:04:00'
                GROUP BY 1, 2 ORDER BY 1, 2
                """);
        assertBucketsEqual(direct, actual);
    }

    @Test
    public void aggregateBuckets_hourLayerComparesValidCountAverage() throws Exception {
        insertAggregate("collector_aggregate_hour", 1, 7, BASE_TIME, 4, 2, 40D, 10D, 30D);
        insertAggregate("collector_aggregate_hour", 1, 7, BASE_TIME.plusHours(1), 8, 4, 80D, 5D, 35D);

        List<CollectorTrendBucketRow> actual = trendMapper.selectAggregateBuckets(
                "collector_aggregate_hour", 3600, List.of(1L),
                BASE_TIME, BASE_TIME.plusHours(2));

        assertEquals(2, actual.size());
        assertBucket(actual.get(0), 1L, BASE_TIME, 20D, 10D, 30D, 2);
        assertBucket(actual.get(1), 1L, BASE_TIME.plusHours(1), 20D, 5D, 35D, 4);

        List<CollectorTrendBucketRow> direct = selectBucketsBySql("""
                SELECT channel_id,
                    date_bin(INTERVAL '1 second' * 3600, bucket_time, TIMESTAMP '2000-01-01 00:00:00') AS bucket_time,
                    SUM(value_sum) / NULLIF(SUM(valid_count), 0) AS avg,
                    MIN(value_min) AS min, MAX(value_max) AS max, SUM(valid_count)::integer AS count
                FROM collector_aggregate_hour
                WHERE channel_id IN (1) AND bucket_time >= '2026-01-01 00:00:00'
                  AND bucket_time < '2026-01-01 02:00:00'
                GROUP BY 1, 2 ORDER BY 1, 2
                """);
        assertBucketsEqual(direct, actual);
    }

    @Test
    public void dayBuckets_compareBucketStartWithExclusiveEndTimestamp() throws Exception {
        insertAggregate("collector_aggregate_day", 1, 7, BASE_TIME, 10, 10, 50D, 3D, 7D);
        insertAggregate("collector_aggregate_day", 1, 7, BASE_TIME.plusDays(1), 20, 20, 120D, 4D, 8D);
        insertAggregate("collector_aggregate_day", 1, 7, BASE_TIME.plusDays(2), 30, 30, 90D, 1D, 5D);

        List<CollectorTrendBucketRow> firstDayOnly = trendMapper.selectDayBuckets(
                List.of(1L), BASE_TIME.plusHours(12), BASE_TIME.plusDays(1));
        assertEquals(1, firstDayOnly.size());
        assertEquals(BASE_TIME, firstDayOnly.get(0).getBucketTime());
        assertEquals(Double.valueOf(5D), firstDayOnly.get(0).getAvg());

        List<CollectorTrendBucketRow> twoDays = trendMapper.selectDayBuckets(
                List.of(1L), BASE_TIME.plusHours(12), BASE_TIME.plusDays(1).plusHours(12));
        assertEquals(2, twoDays.size());

        List<CollectorTrendBucketRow> excludesMidnightEnd = trendMapper.selectDayBuckets(
                List.of(1L), BASE_TIME, BASE_TIME.plusDays(2));
        assertEquals(2, excludesMidnightEnd.size());
    }

    @Test
    public void rawPoints_sampleByStepAndKeepAtMostOnePointPerBin() throws Exception {
        insertRawSeries(1, 7, BASE_TIME, 1200, 1000D);
        insertRawSeries(2, 7, BASE_TIME, 1200, 2000D);

        List<CollectorTrendRawPointRow> points = trendMapper.selectRawPoints(
                10, List.of(1L, 2L), BASE_TIME, BASE_TIME.plusSeconds(1200));

        assertEquals(120 * 2, points.size());
        long channel1 = points.stream().filter(row -> row.getChannelId() == 1L).count();
        assertEquals(120, channel1);
        CollectorTrendRawPointRow first = points.stream()
                .filter(row -> row.getChannelId() == 1L).findFirst().orElseThrow();
        assertEquals(Double.valueOf(1000D), first.getValue());
        assertEquals(BASE_TIME, first.getBucketTime());
        // 每分钟桶保留最早一条，值随秒序号递增
        CollectorTrendRawPointRow tenth = points.stream()
                .filter(row -> row.getChannelId() == 1L).skip(10).findFirst().orElseThrow();
        assertEquals(Double.valueOf(1100D), tenth.getValue());
        assertEquals(BASE_TIME.plusSeconds(100), tenth.getBucketTime());
    }

    @Test
    public void service_autoSelectionQueriesEachLayerOnceAndBuildsSeries() throws Exception {
        stubChannels(channel(1, 7, "normal"), channel(2, 7, "fast"));
        for (int minute = 0; minute < 30; minute++) {
            insertAggregate("collector_aggregate_minute", 1, 7, BASE_TIME.plusMinutes(minute), 1, 1,
                    10D + minute, 10D + minute, 10D + minute);
        }
        for (int i = 0; i < 6; i++) {
            insertAggregate("collector_aggregate_second", 2, 7, BASE_TIME.plusSeconds(i * 5), 3, 3,
                    20D + i, 20D + i, 20D + i);
        }

        CollectorTrendSeriesRespVO resp = trendService.getSeries(request(
                List.of(1L, 2L), BASE_MILLIS, BASE_MILLIS + 30 * 60 * 1000, "auto", false));

        assertEquals("mixed", resp.getSourceSummary());
        assertEquals(2, resp.getSeries().size());
        assertEquals("minute", resp.getSeries().get(0).getSource());
        assertEquals(Integer.valueOf(60), resp.getSeries().get(0).getBucketSeconds());
        assertEquals(30, resp.getSeries().get(0).getPoints().size());
        assertEquals("second", resp.getSeries().get(1).getSource());
        assertEquals(Integer.valueOf(5), resp.getSeries().get(1).getBucketSeconds());
        assertEquals(6, resp.getSeries().get(1).getPoints().size());
        assertEquals(Double.valueOf(10D), resp.getSeries().get(0).getPoints().get(0).getAvg());
        assertEquals(Integer.valueOf(1), resp.getSeries().get(0).getPoints().get(0).getCount());
    }

    @Test
    public void service_rawGranularityUsesRawSqlAndIncludeRawAddsOverlay() throws Exception {
        stubChannels(channel(1, 7, "normal"));
        for (int i = 0; i < 60; i++) {
            insertRaw(1, 7, BASE_TIME.plusSeconds(i), 10D + i, 0);
        }
        // 坏质量和空值必须从主序列与叠加序列中排除；同秒内偏移毫秒避免主键冲突
        insertRaw(1, 7, BASE_TIME.plusSeconds(30).plusNanos(1_000_000), 999D, 2);
        insertRaw(1, 7, BASE_TIME.plusSeconds(31).plusNanos(1_000_000), null, 0);

        CollectorTrendSeriesRespVO resp = trendService.getSeries(request(
                List.of(1L), BASE_MILLIS, BASE_MILLIS + 60_000, "raw", true));

        assertEquals("raw", resp.getSourceSummary());
        assertEquals(2, resp.getSeries().size());
        assertEquals("aggregate", resp.getSeries().get(0).getKind());
        assertEquals(60, resp.getSeries().get(0).getPoints().size());
        assertEquals("raw", resp.getSeries().get(1).getKind());
        assertEquals(60, resp.getSeries().get(1).getPoints().size());
        assertEquals(Double.valueOf(10D), resp.getSeries().get(0).getPoints().get(0).getAvg());
        assertEquals(Integer.valueOf(1), resp.getSeries().get(0).getPoints().get(0).getCount());
        assertNotNull(resp.getSeries().get(1).getPoints().get(0).getValue());
    }

    @Test
    public void service_emptyRangeReturnsEmptySeries() throws Exception {
        stubChannels(channel(1, 7, "normal"));
        CollectorTrendSeriesRespVO resp = trendService.getSeries(request(
                List.of(1L), BASE_MILLIS, BASE_MILLIS + 24 * 60 * 60 * 1000, "auto", false));

        assertEquals(1, resp.getSeries().size());
        assertEquals("minute", resp.getSeries().get(0).getSource());
        assertTrue(resp.getSeries().get(0).getPoints().isEmpty());
    }

    private CollectorTrendSeriesReqVO request(List<Long> channelIds, long from, long to,
                                              String granularity, boolean includeRaw) {
        CollectorTrendSeriesReqVO reqVO = new CollectorTrendSeriesReqVO();
        reqVO.setDeviceId(7L);
        reqVO.setChannelIds(channelIds);
        reqVO.setFrom(from);
        reqVO.setTo(to);
        reqVO.setGranularity(granularity);
        reqVO.setIncludeRaw(includeRaw);
        return reqVO;
    }

    private void assertBucket(CollectorTrendBucketRow row, Long channelId, LocalDateTime bucketTime,
                              Double avg, Double min, Double max, Integer count) {
        assertEquals(channelId, row.getChannelId());
        assertEquals(bucketTime, row.getBucketTime());
        assertEquals(avg, row.getAvg());
        assertEquals(min, row.getMin());
        assertEquals(max, row.getMax());
        assertEquals(count, row.getCount());
    }

    private void assertBucketsEqual(List<CollectorTrendBucketRow> expected,
                                    List<CollectorTrendBucketRow> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).getChannelId(), actual.get(i).getChannelId());
            assertEquals(expected.get(i).getBucketTime(), actual.get(i).getBucketTime());
            assertEquals(expected.get(i).getAvg(), actual.get(i).getAvg());
            assertEquals(expected.get(i).getMin(), actual.get(i).getMin());
            assertEquals(expected.get(i).getMax(), actual.get(i).getMax());
            assertEquals(expected.get(i).getCount(), actual.get(i).getCount());
        }
    }

}
