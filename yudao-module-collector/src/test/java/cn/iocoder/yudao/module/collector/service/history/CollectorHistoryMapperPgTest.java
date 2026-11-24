package cn.iocoder.yudao.module.collector.service.history;

import cn.iocoder.yudao.module.collector.dal.dataobject.history.CollectorHistoryAggregateRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.history.CollectorHistoryRawRow;
import cn.iocoder.yudao.module.collector.dal.mysql.history.CollectorHistoryMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 历史查询 Mapper 的真实 PostgreSQL 校验。
 *
 * <p>沿用仓库既有三件套：{@code COLLECTOR_TEST_PG_URL} 未设置时整体跳过；
 * {@code COLLECTOR_TEST_PG_ALLOW_DESTRUCTIVE=true} 才允许建表；
 * 库名必须以 {@code collector_history_} 开头，避免误连正式库。</p>
 */
public class CollectorHistoryMapperPgTest {

    private static final String URL = System.getenv("COLLECTOR_TEST_PG_URL");
    private static final String USER = System.getenv("COLLECTOR_TEST_PG_USER");
    private static final String PASSWORD = System.getenv("COLLECTOR_TEST_PG_PASSWORD");
    private static final String ALLOW_DESTRUCTIVE = System.getenv("COLLECTOR_TEST_PG_ALLOW_DESTRUCTIVE");
    private static final String TEST_DATABASE_PREFIX = "collector_history_";

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final long CHANNEL_ID = 1L;
    private static final long DEVICE_ID = 7L;

    private CollectorHistoryMapper mapper;
    private SqlSession sqlSession;

    @Before
    public void setUpHistoryPg() throws Exception {
        Assume.assumeTrue("未设置 COLLECTOR_TEST_PG_URL，跳过临时 PostgreSQL 历史查询测试",
                URL != null && !URL.isBlank());
        Assume.assumeTrue("未设置 COLLECTOR_TEST_PG_ALLOW_DESTRUCTIVE=true，跳过会重建表的历史查询测试",
                "true".equalsIgnoreCase(ALLOW_DESTRUCTIVE));
        Assume.assumeTrue("数据库名必须以 " + TEST_DATABASE_PREFIX + " 开头，拒绝在正式库执行破坏性测试",
                safeTestDatabase(URL));
        Class.forName("org.postgresql.Driver");
        recreateSchema();

        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.postgresql.Driver", URL, USER, PASSWORD);
        Environment environment = new Environment("history-test",
                new SpringManagedTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(CollectorHistoryMapper.class);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        sqlSession = factory.openSession(true);
        mapper = sqlSession.getMapper(CollectorHistoryMapper.class);
    }

    @After
    public void tearDownHistoryPg() {
        if (sqlSession != null) {
            sqlSession.close();
            sqlSession = null;
        }
    }

    @Test
    public void rawPage_returnsAscendingRowsWithLimitAndOffset() throws SQLException {
        insertRaw(1);
        insertRaw(2);
        insertRaw(3);

        List<CollectorHistoryRawRow> page = mapper.selectRawPage(CHANNEL_ID, BASE_TIME,
                BASE_TIME.plusDays(1), 1, 2);

        assertEquals(2, page.size());
        assertEquals(BASE_TIME.plusSeconds(2), page.get(0).getCollectTime());
        assertEquals(BASE_TIME.plusSeconds(3), page.get(1).getCollectTime());
        assertEquals(Long.valueOf(235L), page.get(0).getRawValue());
        assertEquals(Integer.valueOf(0), page.get(0).getQuality());
        assertEquals(3L, mapper.countRaw(CHANNEL_ID, BASE_TIME, BASE_TIME.plusDays(1)));
    }

    @Test
    public void rawBatch_usesKeysetCursorAndStopsAtLimit() throws SQLException {
        for (int i = 1; i <= 5; i++) {
            insertRaw(i);
        }

        List<CollectorHistoryRawRow> first = mapper.selectRawBatch(CHANNEL_ID, BASE_TIME,
                BASE_TIME.plusDays(1), null, 2);
        assertEquals(2, first.size());

        List<CollectorHistoryRawRow> second = mapper.selectRawBatch(CHANNEL_ID, BASE_TIME,
                BASE_TIME.plusDays(1), first.get(first.size() - 1).getCollectTime(), 2);
        assertEquals(2, second.size());
        assertEquals(BASE_TIME.plusSeconds(3), second.get(0).getCollectTime());

        List<CollectorHistoryRawRow> third = mapper.selectRawBatch(CHANNEL_ID, BASE_TIME,
                BASE_TIME.plusDays(1), second.get(second.size() - 1).getCollectTime(), 2);
        assertEquals(1, third.size());
        assertEquals(BASE_TIME.plusSeconds(5), third.get(0).getCollectTime());
    }

    @Test
    public void rawQuery_outsideRangeReturnsEmpty() throws SQLException {
        insertRaw(1);

        List<CollectorHistoryRawRow> rows = mapper.selectRawPage(CHANNEL_ID,
                BASE_TIME.plusDays(2), BASE_TIME.plusDays(3), 0, 20);

        assertTrue(rows.isEmpty());
        assertEquals(0L, mapper.countRaw(CHANNEL_ID, BASE_TIME.plusDays(2), BASE_TIME.plusDays(3)));
    }

    @Test
    public void aggregatePage_readsMinuteTableAscending() throws SQLException {
        for (int i = 1; i <= 3; i++) {
            insertAggregate("collector_aggregate_minute", BASE_TIME.plusMinutes(i), 10D + i);
        }

        List<CollectorHistoryAggregateRow> rows = mapper.selectAggregatePage(
                "collector_aggregate_minute", CHANNEL_ID, BASE_TIME, BASE_TIME.plusHours(1), 0, 20);

        assertEquals(3, rows.size());
        assertEquals(BASE_TIME.plusMinutes(1), rows.get(0).getBucketTime());
        assertEquals(Integer.valueOf(10), rows.get(0).getSampleCount());
        assertEquals(Integer.valueOf(1), rows.get(0).getGoodCount());
        assertEquals(Integer.valueOf(0), rows.get(0).getBadCount());
        assertEquals(Double.valueOf(11D), rows.get(0).getValueAvg());
        assertEquals(3L, mapper.countAggregate("collector_aggregate_minute", CHANNEL_ID,
                BASE_TIME, BASE_TIME.plusHours(1)));
    }

    @Test
    public void dayPage_projectsBucketDateAsMidnightTimestamp() throws SQLException {
        insertAggregate("collector_aggregate_day", BASE_TIME, 20D);
        insertAggregate("collector_aggregate_day", BASE_TIME.plusDays(1), 21D);

        List<CollectorHistoryAggregateRow> rows = mapper.selectDayPage(CHANNEL_ID,
                BASE_TIME, BASE_TIME.plusDays(2), 0, 20);

        assertEquals(2, rows.size());
        assertEquals(BASE_TIME, rows.get(0).getBucketTime());
        assertEquals(BASE_TIME.plusDays(1), rows.get(1).getBucketTime());
        assertEquals(2L, mapper.countDay(CHANNEL_ID, BASE_TIME, BASE_TIME.plusDays(2)));
    }

    @Test
    public void dayBatch_filtersOutEarlierBucketDates() throws SQLException {
        insertAggregate("collector_aggregate_day", BASE_TIME, 20D);
        insertAggregate("collector_aggregate_day", BASE_TIME.plusDays(1), 21D);
        insertAggregate("collector_aggregate_day", BASE_TIME.plusDays(2), 22D);

        List<CollectorHistoryAggregateRow> rows = mapper.selectDayBatch(CHANNEL_ID,
                BASE_TIME, BASE_TIME.plusDays(3), BASE_TIME, 10);

        assertEquals(2, rows.size());
        assertEquals(BASE_TIME.plusDays(1), rows.get(0).getBucketTime());
    }

    @Test
    public void aggregateDaysBoundary_excludesMidnightUpperBound() throws SQLException {
        // bucket_date=2026-01-02 的行在 to=2026-01-02 00:00 时不应出现（区间 [from, to)）
        insertAggregate("collector_aggregate_day", BASE_TIME, 20D);
        insertAggregate("collector_aggregate_day", BASE_TIME.plusDays(1), 21D);

        List<CollectorHistoryAggregateRow> rows = mapper.selectDayPage(CHANNEL_ID,
                BASE_TIME, BASE_TIME.plusDays(1), 0, 20);

        assertEquals(1, rows.size());
        assertEquals(BASE_TIME, rows.get(0).getBucketTime());
        assertNull(rows.get(0).getValueFirst());
    }

    // ==================== 建表与造数 ====================

    private void recreateSchema() throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    DROP TABLE IF EXISTS collector_sample_raw, collector_aggregate_second,
                        collector_aggregate_minute, collector_aggregate_hour,
                        collector_aggregate_day CASCADE
                    """);
            // 与生产一致：raw 按 collect_time 做 RANGE 分区，验证 SQL 可在分区表上直接执行。
            statement.execute("""
                    CREATE TABLE collector_sample_raw (
                        channel_id bigint NOT NULL, device_id bigint NOT NULL,
                        collect_time timestamp(3) NOT NULL,
                        value double precision, raw_value bigint,
                        quality smallint DEFAULT 0 NOT NULL,
                        PRIMARY KEY (channel_id, collect_time)
                    ) PARTITION BY RANGE (collect_time)
                    """);
            statement.execute("""
                    CREATE TABLE collector_sample_raw_p20260101
                        PARTITION OF collector_sample_raw
                        FOR VALUES FROM ('2026-01-01 00:00:00') TO ('2026-01-02 00:00:00')
                    """);
            statement.execute("""
                    CREATE TABLE collector_sample_raw_default
                        PARTITION OF collector_sample_raw DEFAULT
                    """);
            createAggregateTable(statement, "collector_aggregate_second", "bucket_time");
            createAggregateTable(statement, "collector_aggregate_minute", "bucket_time");
            createAggregateTable(statement, "collector_aggregate_hour", "bucket_time");
            createAggregateTable(statement, "collector_aggregate_day", "bucket_date");
        }
    }

    private void createAggregateTable(Statement statement, String table, String bucketColumn)
            throws SQLException {
        String bucketType = "bucket_date".equals(bucketColumn) ? "date" : "timestamp(3)";
        statement.execute("""
                CREATE TABLE %s (
                    channel_id bigint NOT NULL, device_id bigint NOT NULL,
                    %s %s NOT NULL,
                    sample_count integer DEFAULT 0 NOT NULL,
                    good_count integer DEFAULT 0 NOT NULL, valid_count integer DEFAULT 0 NOT NULL,
                    bad_count integer DEFAULT 0 NOT NULL,
                    value_avg double precision, value_min double precision, value_max double precision,
                    value_sum double precision, value_first double precision, value_last double precision,
                    update_time timestamp(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
                    PRIMARY KEY (channel_id, %s)
                )
                """.formatted(table, bucketColumn, bucketType, bucketColumn));
    }

    private void insertRaw(int offsetSeconds) throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO collector_sample_raw
                         (channel_id, device_id, collect_time, value, raw_value, quality)
                     VALUES (?, ?, ?, ?, ?, ?)
                     """)) {
            statement.setLong(1, CHANNEL_ID);
            statement.setLong(2, DEVICE_ID);
            statement.setTimestamp(3, Timestamp.valueOf(BASE_TIME.plusSeconds(offsetSeconds)));
            statement.setDouble(4, 23.5D);
            statement.setLong(5, 235L);
            statement.setInt(6, 0);
            statement.executeUpdate();
        }
    }

    private void insertAggregate(String table, LocalDateTime bucket, double value) throws SQLException {
        boolean day = "collector_aggregate_day".equals(table);
        String bucketColumn = day ? "bucket_date" : "bucket_time";
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO %s (channel_id, device_id, %s, sample_count, good_count,
                         valid_count, bad_count, value_avg, value_min, value_max, value_sum)
                     VALUES (?, ?, ?, 10, 1, 1, 0, ?, ?, ?, ?)
                     """.formatted(table, bucketColumn))) {
            statement.setLong(1, CHANNEL_ID);
            statement.setLong(2, DEVICE_ID);
            if (day) {
                statement.setObject(3, bucket.toLocalDate());
            } else {
                statement.setTimestamp(3, Timestamp.valueOf(bucket));
            }
            statement.setDouble(4, value);
            statement.setDouble(5, value - 1);
            statement.setDouble(6, value + 1);
            statement.setDouble(7, value);
            statement.executeUpdate();
        }
    }

    private static boolean safeTestDatabase(String jdbcUrl) {
        String withoutParams = jdbcUrl.split("\\?", 2)[0];
        int slash = withoutParams.lastIndexOf('/');
        return slash >= 0 && withoutParams.substring(slash + 1).startsWith(TEST_DATABASE_PREFIX);
    }

}
