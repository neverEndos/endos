package cn.iocoder.yudao.module.collector.service.aggregate;

import cn.iocoder.yudao.module.collector.dal.dataobject.channel.CollectorChannelDO;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * 手工运行的大数据量性能与对账测试。
 * 运行条件：COLLECTOR_TEST_PG_URL 等指定 collector_aggregate_ 前缀临时库，
 * COLLECTOR_TEST_PG_ALLOW_DESTRUCTIVE=true 且 COLLECTOR_TEST_PERF=true。
 */
public class CollectorAggregatePerformanceTest extends CollectorAggregatePgTestSupport {

    private static final LocalDateTime FAST_FROM = LocalDateTime.of(2026, 11, 15, 0, 0);
    private static final LocalDateTime NORMAL_FROM = LocalDateTime.of(2026, 11, 14, 0, 0);

    @Before
    public void requirePerformanceFlag() {
        Assume.assumeTrue("未设置 COLLECTOR_TEST_PERF=true，跳过性能实测",
                "true".equalsIgnoreCase(System.getenv("COLLECTOR_TEST_PERF")));
    }

    @Test
    public void measureAndReconcileRawSecondMinuteHourDay() throws Exception {
        List<Long> fastIds = ids(1, 5);
        List<Long> normalIds = ids(101, 116);
        setChannels(channels(fastIds, 7, "fast").toArray(CollectorChannelDO[]::new));

        long insertBegin = System.currentTimeMillis();
        execute("""
                INSERT INTO collector_sample_raw (channel_id, device_id, collect_time, value, quality)
                SELECT c, 7, TIMESTAMP '2026-11-15 00:00:00' + (i * interval '20 milliseconds'),
                    ((i % 1000)::double precision) / 100, 0
                FROM generate_series(1, 5) c CROSS JOIN generate_series(0, 179999) i
                """);
        long insertFastMs = System.currentTimeMillis() - insertBegin;
        System.out.printf("[perf] 生成 5 个 fast 通道 1 小时 50Hz raw：900000 行，%d ms%n", insertFastMs);

        CollectorAggregateService.AggregateResult second = aggregateService.aggregateSecond(
                FAST_FROM, FAST_FROM.plusHours(1), fastIds);
        System.out.printf("[perf] aggregateSecond(raw) 5 通道 1 小时：通道=%d，桶=%d，源行=%d，耗时=%d ms%n",
                second.channelCount(), second.bucketCount(), second.rowCount(), second.durationMillis());
        assertEquals(900_000, second.rowCount());
        assertEquals(18_000, second.bucketCount());

        CollectorAggregateService.AggregateResult fastMinute = aggregateService.aggregateMinute(
                FAST_FROM, FAST_FROM.plusHours(1), fastIds);
        System.out.printf("[perf] aggregateMinute(second) 5 通道 1 小时：通道=%d，桶=%d，源行=%d，耗时=%d ms%n",
                fastMinute.channelCount(), fastMinute.bucketCount(), fastMinute.rowCount(), fastMinute.durationMillis());
        assertEquals(300, fastMinute.bucketCount());

        // 真实查询会按 channelIds 过滤；mock 忽略 Wrapper，这里切换通道集合以保持计数准确。
        setChannels(channels(normalIds, 8, "normal").toArray(CollectorChannelDO[]::new));
        long insertNormalBegin = System.currentTimeMillis();
        execute("""
                INSERT INTO collector_sample_raw (channel_id, device_id, collect_time, value, quality)
                SELECT c, 8, TIMESTAMP '2026-11-14 00:00:00' + (i * interval '1 second'),
                    ((i % 1000)::double precision) / 100, 0
                FROM generate_series(101, 116) c CROSS JOIN generate_series(0, 86399) i
                """);
        long insertNormalMs = System.currentTimeMillis() - insertNormalBegin;
        System.out.printf("[perf] 生成 16 个 normal 通道 1 天 1Hz raw：1382400 行，%d ms%n", insertNormalMs);

        CollectorAggregateService.AggregateResult normalMinute = aggregateService.aggregateMinute(
                NORMAL_FROM, NORMAL_FROM.plusDays(1), normalIds);
        System.out.printf("[perf] aggregateMinute(raw) 16 通道 1 天：通道=%d，桶=%d，源行=%d，耗时=%d ms%n",
                normalMinute.channelCount(), normalMinute.bucketCount(), normalMinute.rowCount(),
                normalMinute.durationMillis());
        assertEquals(1_382_400, normalMinute.rowCount());
        assertEquals(23_040, normalMinute.bucketCount());

        CollectorAggregateService.AggregateResult normalHour = aggregateService.aggregateHour(
                NORMAL_FROM, NORMAL_FROM.plusDays(1), normalIds);
        System.out.printf("[perf] aggregateHour(minute) 16 通道 1 天：通道=%d，桶=%d，源行=%d，耗时=%d ms%n",
                normalHour.channelCount(), normalHour.bucketCount(), normalHour.rowCount(), normalHour.durationMillis());
        assertEquals(384, normalHour.bucketCount());

        CollectorAggregateService.AggregateResult normalDay = aggregateService.aggregateDay(
                NORMAL_FROM, NORMAL_FROM.plusDays(1), normalIds);
        System.out.printf("[perf] aggregateDay(hour) 16 通道 1 天：通道=%d，桶=%d，源行=%d，耗时=%d ms%n",
                normalDay.channelCount(), normalDay.bucketCount(), normalDay.rowCount(), normalDay.durationMillis());
        assertEquals(16, normalDay.bucketCount());

        // 逐列对账：原始秒表；分钟覆盖 fast+normal；小时/天覆盖本次实测的 normal 通道。
        assertBucketsEqual(selectRawExpected("second", FAST_FROM, FAST_FROM.plusHours(1), fastIds),
                selectBuckets("collector_aggregate_second", false));
        assertBucketsEqual(selectRawExpected("minute", NORMAL_FROM, FAST_FROM.plusHours(1),
                        concatIds(fastIds, normalIds)),
                selectBuckets("collector_aggregate_minute", false));
        assertBucketsEqual(selectRawExpected("hour", NORMAL_FROM, NORMAL_FROM.plusDays(1), normalIds),
                selectBuckets("collector_aggregate_hour", false));
        assertBucketsEqual(selectRawExpected("day", NORMAL_FROM, NORMAL_FROM.plusDays(1), normalIds),
                selectBuckets("collector_aggregate_day", true));
        System.out.println("[perf] raw 独立 SQL 对账：second/minute/hour/day 全列一致，浮点容差 1e-9");
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private List<Long> ids(long from, long to) {
        List<Long> ids = new ArrayList<>();
        for (long id = from; id <= to; id++) {
            ids.add(id);
        }
        return ids;
    }

    private List<CollectorChannelDO> channels(List<Long> ids, long deviceId, String profile) {
        List<CollectorChannelDO> channels = new ArrayList<>();
        ids.forEach(id -> channels.add(channel(id, deviceId, profile)));
        return channels;
    }

    private List<Long> concatIds(List<Long> left, List<Long> right) {
        List<Long> all = new ArrayList<>(left);
        all.addAll(right);
        return all;
    }

}
