package cn.iocoder.yudao.module.collector.service.aggregate;

import cn.iocoder.yudao.module.collector.dal.dataobject.aggregate.CollectorAggregateBucketDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.channel.CollectorChannelDO;
import cn.iocoder.yudao.module.collector.dal.mysql.aggregate.CollectorAggregateMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.channel.CollectorChannelMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.session.SqlSession;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.Assume;
import org.junit.Before;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 真实临时 PostgreSQL 测试基座；未显式提供测试库时跳过。 */
abstract class CollectorAggregatePgTestSupport {

    protected static final String URL = System.getenv("COLLECTOR_TEST_PG_URL");
    protected static final String USER = System.getenv("COLLECTOR_TEST_PG_USER");
    protected static final String PASSWORD = System.getenv("COLLECTOR_TEST_PG_PASSWORD");
    protected static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 1, 1, 0, 0);

    protected CollectorAggregateServiceImpl aggregateService;
    protected CollectorAggregateMapper aggregateMapper;
    protected CollectorChannelMapper channelMapper;
    protected UnpooledDataSource dataSource;
    private SqlSession sqlSession;

    @Before
    public void setUpAggregatePg() throws Exception {
        Assume.assumeTrue("未设置 COLLECTOR_TEST_PG_URL，跳过临时 PostgreSQL 聚合测试",
                URL != null && !URL.isBlank());
        Class.forName("org.postgresql.Driver");
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    DROP TABLE IF EXISTS collector_sample_raw, collector_channel,
                        collector_aggregate_second, collector_aggregate_minute,
                        collector_aggregate_hour, collector_aggregate_day,
                        collector_aggregate_watermark CASCADE
                    """);
            statement.execute("""
                    CREATE TABLE collector_channel (
                        id bigint PRIMARY KEY, device_id bigint NOT NULL,
                        data_profile varchar(16) DEFAULT 'normal' NOT NULL,
                        deleted smallint DEFAULT 0 NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE collector_sample_raw (
                        sample_id bigserial PRIMARY KEY, channel_id bigint NOT NULL,
                        device_id bigint NOT NULL, collect_time timestamp(3) NOT NULL,
                        value double precision, raw_value bigint, quality smallint NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX idx_test_raw_channel_time ON collector_sample_raw (channel_id, collect_time)");
            createAggregateTable(statement, "collector_aggregate_second", "bucket_time");
            createAggregateTable(statement, "collector_aggregate_minute", "bucket_time");
            createAggregateTable(statement, "collector_aggregate_hour", "bucket_time");
            createAggregateTable(statement, "collector_aggregate_day", "bucket_date");
            statement.execute("""
                    CREATE TABLE collector_aggregate_watermark (
                        agg_type varchar(8) NOT NULL, channel_id bigint NOT NULL,
                        device_id bigint NOT NULL, watermark_time timestamp(3),
                        backfill_from timestamp(3), backfill_to timestamp(3),
                        status smallint DEFAULT 0 NOT NULL, retry_count integer DEFAULT 0 NOT NULL,
                        last_error varchar(500), locked_until timestamp(3),
                        last_success_time timestamp(3), update_time timestamp(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        PRIMARY KEY (agg_type, channel_id)
                    )
                    """);
        }

        // Service 使用 LambdaQueryWrapperX；脱离 Spring 启动时需手动初始化 MP 的字段缓存。
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                CollectorChannelDO.class);
        dataSource = new UnpooledDataSource("org.postgresql.Driver", URL, USER, PASSWORD);
        Environment environment = new Environment("aggregate-test", new SpringManagedTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(CollectorAggregateMapper.class);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        sqlSession = new SqlSessionTemplate(factory);
        aggregateMapper = sqlSession.getMapper(CollectorAggregateMapper.class);
        channelMapper = mock(CollectorChannelMapper.class);
        aggregateService = new CollectorAggregateServiceImpl(channelMapper, aggregateMapper,
                new DataSourceTransactionManager(dataSource));
    }

    private void createAggregateTable(Statement statement, String table, String bucketColumn) throws SQLException {
        statement.execute("""
                CREATE TABLE %s (
                    channel_id bigint NOT NULL, device_id bigint NOT NULL,
                    %s timestamp(3) NOT NULL,
                    sample_count integer DEFAULT 0 NOT NULL,
                    good_count integer DEFAULT 0 NOT NULL, bad_count integer DEFAULT 0 NOT NULL,
                    value_avg double precision, value_min double precision, value_max double precision,
                    value_sum double precision, value_first double precision, value_last double precision,
                    update_time timestamp(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
                    PRIMARY KEY (channel_id, %s)
                )
                """.formatted(table, bucketColumn, bucketColumn));
    }

    protected void setChannels(CollectorChannelDO... channels) throws SQLException {
        when(channelMapper.selectList(any())).thenReturn(Arrays.asList(channels));
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement delete = connection.prepareStatement("DELETE FROM collector_channel");
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO collector_channel (id, device_id, data_profile) VALUES (?, ?, ?)")) {
            delete.executeUpdate();
            for (CollectorChannelDO channel : channels) {
                insert.setLong(1, channel.getId());
                insert.setLong(2, channel.getDeviceId());
                insert.setString(3, channel.getDataProfile());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    protected CollectorChannelDO channel(long id, long deviceId, String dataProfile) {
        CollectorChannelDO channel = new CollectorChannelDO();
        channel.setId(id);
        channel.setDeviceId(deviceId);
        channel.setDataProfile(dataProfile);
        return channel;
    }

    protected void insertRaw(long channelId, long deviceId, LocalDateTime time, Double value, int quality)
            throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO collector_sample_raw (channel_id, device_id, collect_time, value, quality)
                     VALUES (?, ?, ?, ?, ?)
                     """)) {
            statement.setLong(1, channelId);
            statement.setLong(2, deviceId);
            statement.setTimestamp(3, Timestamp.valueOf(time));
            if (value == null) {
                statement.setNull(4, java.sql.Types.DOUBLE);
            } else {
                statement.setDouble(4, value);
            }
            statement.setInt(5, quality);
            statement.executeUpdate();
        }
    }

    protected List<CollectorAggregateBucketDO> selectBuckets(String table, boolean day) throws SQLException {
        String bucketColumn = day ? "bucket_date::timestamp" : "bucket_time";
        List<CollectorAggregateBucketDO> rows = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT channel_id, device_id, %s AS bucket_time, sample_count, good_count, bad_count,
                         value_avg, value_min, value_max, value_sum, value_first, value_last
                     FROM %s ORDER BY channel_id, bucket_time
                     """.formatted(bucketColumn, table))) {
            while (rs.next()) {
                rows.add(readBucket(rs));
            }
        }
        return rows;
    }

    /** 独立从 raw 直算，用于和聚合表逐列对账，而非复用上层聚合结果。 */
    protected List<CollectorAggregateBucketDO> selectRawExpected(String truncateUnit, LocalDateTime from,
                                                                 LocalDateTime to, Collection<Long> channelIds)
            throws SQLException {
        StringJoiner ids = new StringJoiner(",");
        channelIds.forEach(id -> ids.add(String.valueOf(id)));
        List<CollectorAggregateBucketDO> rows = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT channel_id, device_id, date_trunc('%s', collect_time) AS bucket_time,
                         COUNT(*)::integer AS sample_count,
                         COUNT(*) FILTER (WHERE quality = 0)::integer AS good_count,
                         COUNT(*) FILTER (WHERE quality = 2)::integer AS bad_count,
                         SUM(value) FILTER (WHERE quality = 0 AND value IS NOT NULL)
                             / NULLIF(COUNT(*) FILTER (WHERE quality = 0), 0) AS value_avg,
                         MIN(value) FILTER (WHERE quality = 0 AND value IS NOT NULL) AS value_min,
                         MAX(value) FILTER (WHERE quality = 0 AND value IS NOT NULL) AS value_max,
                         SUM(value) FILTER (WHERE quality = 0 AND value IS NOT NULL) AS value_sum,
                         (array_agg(value ORDER BY collect_time, sample_id)
                             FILTER (WHERE quality = 0 AND value IS NOT NULL))[1] AS value_first,
                         (array_agg(value ORDER BY collect_time DESC, sample_id DESC)
                             FILTER (WHERE quality = 0 AND value IS NOT NULL))[1] AS value_last
                     FROM collector_sample_raw
                     WHERE collect_time >= ? AND collect_time < ? AND channel_id IN (%s)
                     GROUP BY channel_id, device_id, bucket_time
                     ORDER BY channel_id, bucket_time
                     """.formatted(truncateUnit, ids))) {
            statement.setTimestamp(1, Timestamp.valueOf(from));
            statement.setTimestamp(2, Timestamp.valueOf(to));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(readBucket(rs));
                }
            }
        }
        return rows;
    }

    private CollectorAggregateBucketDO readBucket(ResultSet rs) throws SQLException {
        CollectorAggregateBucketDO row = new CollectorAggregateBucketDO();
        row.setChannelId(rs.getLong("channel_id"));
        row.setDeviceId(rs.getLong("device_id"));
        row.setBucketTime(rs.getObject("bucket_time", LocalDateTime.class));
        row.setSampleCount(rs.getInt("sample_count"));
        row.setGoodCount(rs.getInt("good_count"));
        row.setBadCount(rs.getInt("bad_count"));
        row.setValueAvg((Double) rs.getObject("value_avg"));
        row.setValueMin((Double) rs.getObject("value_min"));
        row.setValueMax((Double) rs.getObject("value_max"));
        row.setValueSum((Double) rs.getObject("value_sum"));
        row.setValueFirst((Double) rs.getObject("value_first"));
        row.setValueLast((Double) rs.getObject("value_last"));
        return row;
    }

    /** double 累加顺序可能不同，浮点列使用 1e-9 容差；时间、通道和计数列必须完全相等。 */
    protected void assertBucketsEqual(List<CollectorAggregateBucketDO> expected,
                                      List<CollectorAggregateBucketDO> actual) {
        assertEquals("聚合桶数量不一致", expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            CollectorAggregateBucketDO e = expected.get(i);
            CollectorAggregateBucketDO a = actual.get(i);
            String position = "第 " + i + " 个桶，channel=" + e.getChannelId() + "，bucket=" + e.getBucketTime();
            assertEquals(position, e.getChannelId(), a.getChannelId());
            assertEquals(position, e.getDeviceId(), a.getDeviceId());
            assertEquals(position, e.getBucketTime(), a.getBucketTime());
            assertEquals(position, e.getSampleCount(), a.getSampleCount());
            assertEquals(position, e.getGoodCount(), a.getGoodCount());
            assertEquals(position, e.getBadCount(), a.getBadCount());
            assertDoubleEquals(position + " avg", e.getValueAvg(), a.getValueAvg());
            assertDoubleEquals(position + " min", e.getValueMin(), a.getValueMin());
            assertDoubleEquals(position + " max", e.getValueMax(), a.getValueMax());
            assertDoubleEquals(position + " sum", e.getValueSum(), a.getValueSum());
            assertDoubleEquals(position + " first", e.getValueFirst(), a.getValueFirst());
            assertDoubleEquals(position + " last", e.getValueLast(), a.getValueLast());
        }
    }

    private void assertDoubleEquals(String message, Double expected, Double actual) {
        if (expected == null || actual == null) {
            assertTrue(message + "，期望与实际都应为 NULL", expected == null && actual == null);
            return;
        }
        assertEquals(message, expected, actual, 1e-9);
    }

    protected int countRows(String table) throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

}
