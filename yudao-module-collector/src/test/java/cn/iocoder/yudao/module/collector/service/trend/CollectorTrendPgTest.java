package cn.iocoder.yudao.module.collector.service.trend;

import cn.iocoder.yudao.module.collector.controller.admin.trend.vo.CollectorTrendSeriesReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.trend.vo.CollectorTrendSeriesRespVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.trend.CollectorTrendBucketRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.trend.CollectorTrendRawPointRow;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
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

    @Test
    public void aggregateBuckets_havingSkipsBucketsWithZeroValidCount() throws Exception {
        // 同一重分桶窗口内全是 valid_count=0 的桶必须被 HAVING 过滤，不产生空白点
        insertAggregate("collector_aggregate_minute", 1, 7, BASE_TIME, 5, 0, null, null, null);
        insertAggregate("collector_aggregate_minute", 1, 7, BASE_TIME.plusMinutes(1), 8, 0,
                null, null, null);
        List<CollectorTrendBucketRow> onlyInvalid = trendMapper.selectAggregateBuckets(
                "collector_aggregate_minute", 180, List.of(1L),
                BASE_TIME, BASE_TIME.plusMinutes(3));
        assertEquals(0, onlyInvalid.size());

        // 混合窗口：有效桶正常返回，无效桶被剔除
        insertAggregate("collector_aggregate_minute", 2, 7, BASE_TIME, 4, 4, 40D, 5D, 15D);
        insertAggregate("collector_aggregate_minute", 2, 7, BASE_TIME.plusMinutes(1), 8, 0,
                null, null, null);
        List<CollectorTrendBucketRow> mixed = trendMapper.selectAggregateBuckets(
                "collector_aggregate_minute", 180, List.of(2L),
                BASE_TIME, BASE_TIME.plusMinutes(3));
        assertEquals(1, mixed.size());
        assertBucket(mixed.get(0), 2L, BASE_TIME, 10D, 5D, 15D, 4);
    }

    @Test
    public void dayBuckets_skipRowsWithZeroValidCount() throws Exception {
        insertAggregate("collector_aggregate_day", 1, 7, BASE_TIME, 10, 0, null, null, null);
        insertAggregate("collector_aggregate_day", 1, 7, BASE_TIME.plusDays(1), 10, 10,
                50D, 3D, 7D);

        List<CollectorTrendBucketRow> rows = trendMapper.selectDayBuckets(
                List.of(1L), BASE_TIME, BASE_TIME.plusDays(2));
        assertEquals(1, rows.size());
        assertEquals(BASE_TIME.plusDays(1), rows.get(0).getBucketTime());
    }

    @Test
    public void service_fastFiveThousandSecondsMisalignedCapsAtOneThousandPoints() throws Exception {
        // 复现 Blocker 1 的 fast 场景：00:00:01~01:23:21 共 5000 秒，from 不在 5 秒桶边界上。
        // 旧公式会选 5 秒桶，SQL 实际返回 1001 点；修复后必须递增到 10 秒桶。
        stubChannels(channel(1, 7, "fast"));
        insertAggregateSeries("collector_aggregate_second", 1, 7,
                BASE_TIME.plusSeconds(1), 5000, 1, 1000D);

        CollectorTrendSeriesRespVO resp = trendService.getSeries(request(
                List.of(1L), BASE_MILLIS + 1000, BASE_MILLIS + 5001 * 1000, "1s", false));

        assertEquals("second", resp.getSourceSummary());
        assertEquals(Integer.valueOf(10), resp.getSeries().get(0).getBucketSeconds());
        // 起点在 BASE+1s：10 秒桶最坏 501 点，严格不超过 1000
        assertEquals(501, resp.getSeries().get(0).getPoints().size());
        assertEquals(Long.valueOf(BASE_MILLIS), resp.getSeries().get(0).getPoints().get(0).getT());
        assertEquals(Long.valueOf(BASE_MILLIS + 5000 * 1000),
                resp.getSeries().get(0).getPoints().get(500).getT());
    }

    @Test
    public void service_rawOverlayStepCapsMisalignedRangeUnderLimit() throws Exception {
        // 2000 秒范围 from 偏移 1 秒：旧步长 ceil(2000/1000)=2 实际可产生 1001 个抽样桶；
        // 新步长按最坏桶数 ceil(2000/999)=3 选择，实际 667 点。
        stubChannels(channel(1, 7, "normal"));
        insertRawSeries(1, 7, BASE_TIME.plusSeconds(1), 2000, 2000D);

        CollectorTrendSeriesRespVO resp = trendService.getSeries(request(
                List.of(1L), BASE_MILLIS + 1000, BASE_MILLIS + 2001 * 1000, "auto", true));

        assertEquals("minute", resp.getSeries().get(0).getSource());
        assertEquals("raw", resp.getSeries().get(1).getKind());
        assertEquals(Integer.valueOf(3), resp.getSeries().get(1).getBucketSeconds());
        assertEquals(667, resp.getSeries().get(1).getPoints().size());
        assertTrue(resp.getSeries().get(1).getPoints().size() <= 1000);
    }

    @Test
    public void statementTimeout_setLocalCancelsSlowQueryWithQueryCanceled() throws Exception {
        // 机制验证：同一连接上 SET LOCAL statement_timeout 后，慢查询被 57014 取消。
        // 与 Service 叠加查询使用完全相同的机制（Mapper 的 setRawOverlayTimeout）。
        Connection connection = sessionConnection();
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET LOCAL statement_timeout = 50");
            SQLException error = assertThrows(SQLException.class,
                    () -> statement.executeQuery("SELECT pg_sleep(0.5)"));
            assertEquals("57014", error.getSQLState());
        } finally {
            connection.rollback();
            connection.setAutoCommit(autoCommit);
        }
        // 还原后同一连接恢复正常查询
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT 1")) {
            assertTrue(rs.next());
        }
    }

    @Test
    public void service_includeRawOverlayTimeoutDegradesToMainSeries() throws Exception {
        // 真实触发 statement_timeout：3M 条 raw 样本的叠加扫描在 50ms 超时内无法完成，
        // 主聚合序列必须保留，raw 序列降级为空并带 rawDegraded 警告。
        stubChannels(channel(1, 7, "normal"));
        for (int minute = 0; minute < 10; minute++) {
            insertAggregate("collector_aggregate_minute", 1, 7, BASE_TIME.plusMinutes(minute), 1, 1,
                    10D + minute, 10D + minute, 10D + minute);
        }
        executeSql("""
                INSERT INTO collector_sample_raw (channel_id, device_id, collect_time, value, quality)
                SELECT 1, 7, TIMESTAMP '2026-01-01 00:00:00' + (i || ' milliseconds')::interval,
                    25.0, 0
                FROM generate_series(0, 2999999) AS g(i)
                """);
        trendService = newCollectorTrendService(50);

        CollectorTrendSeriesRespVO resp = trendService.getSeries(request(
                List.of(1L), BASE_MILLIS, BASE_MILLIS + 1200 * 1000, "auto", true));

        assertEquals(Boolean.TRUE, resp.getRawDegraded());
        assertEquals("原始点加载超时，已仅展示聚合趋势", resp.getRawWarning());
        assertEquals(2, resp.getSeries().size());
        assertEquals("aggregate", resp.getSeries().get(0).getKind());
        assertEquals(10, resp.getSeries().get(0).getPoints().size());
        assertEquals(Double.valueOf(10D), resp.getSeries().get(0).getPoints().get(0).getAvg());
        assertEquals("raw", resp.getSeries().get(1).getKind());
        assertTrue(resp.getSeries().get(1).getPoints().isEmpty());
    }

    @Test
    public void service_emptyRangeWithOnlyInvalidBucketsReturnsEmptyPoints() throws Exception {
        // Major 3：范围内只有 valid_count=0 的聚合桶时，返回空序列而不是空白点
        stubChannels(channel(1, 7, "normal"));
        insertAggregate("collector_aggregate_minute", 1, 7, BASE_TIME, 10, 0, null, null, null);
        insertAggregate("collector_aggregate_minute", 1, 7, BASE_TIME.plusMinutes(1), 10, 0,
                null, null, null);

        CollectorTrendSeriesRespVO resp = trendService.getSeries(request(
                List.of(1L), BASE_MILLIS, BASE_MILLIS + 24 * 60 * 60 * 1000, "auto", false));

        assertEquals(1, resp.getSeries().size());
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
