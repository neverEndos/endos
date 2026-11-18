package cn.iocoder.yudao.module.collector.service.trend;

import cn.iocoder.yudao.module.collector.dal.dataobject.channel.CollectorChannelDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.trend.CollectorTrendBucketRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.trend.CollectorTrendRawPointRow;
import cn.iocoder.yudao.module.collector.dal.mysql.channel.CollectorChannelMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.device.CollectorDeviceMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.trend.CollectorTrendMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;

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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 真实临时 PostgreSQL 测试基座；未显式提供测试库时跳过。 */
abstract class CollectorTrendPgTestSupport {

    protected static final String URL = System.getenv("COLLECTOR_TEST_PG_URL");
    protected static final String USER = System.getenv("COLLECTOR_TEST_PG_USER");
    protected static final String PASSWORD = System.getenv("COLLECTOR_TEST_PG_PASSWORD");
    private static final String ALLOW_DESTRUCTIVE = System.getenv("COLLECTOR_TEST_PG_ALLOW_DESTRUCTIVE");
    protected static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 1, 1, 0, 0);
    protected static final long BASE_MILLIS = 1767196800000L; // 2026-01-01 00:00 Asia/Shanghai

    protected CollectorTrendServiceImpl trendService;
    protected CollectorTrendMapper trendMapper;
    protected CollectorChannelMapper channelMapper;
    protected CollectorDeviceMapper deviceMapper;
    protected UnpooledDataSource dataSource;
    private SqlSession sqlSession;

    @Before
    public void setUpTrendPg() throws Exception {
        Assume.assumeTrue("未设置 COLLECTOR_TEST_PG_URL，跳过临时 PostgreSQL 趋势测试",
                URL != null && !URL.isBlank());
        Assume.assumeTrue("未设置 COLLECTOR_TEST_PG_ALLOW_DESTRUCTIVE=true，跳过会清表的趋势测试",
                "true".equalsIgnoreCase(ALLOW_DESTRUCTIVE));
        Assume.assumeTrue("数据库名必须以 collector_aggregate_ 开头，拒绝执行破坏性测试",
                safeTestDatabase(URL));
        Class.forName("org.postgresql.Driver");
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    DROP TABLE IF EXISTS collector_sample_raw, collector_channel,
                        collector_aggregate_second, collector_aggregate_minute,
                        collector_aggregate_hour, collector_aggregate_day CASCADE
                    """);
            statement.execute("""
                    CREATE TABLE collector_channel (
                        id bigint PRIMARY KEY, device_id bigint NOT NULL,
                        channel_name varchar(100), unit varchar(32),
                        data_profile varchar(16) DEFAULT 'normal' NOT NULL,
                        deleted smallint DEFAULT 0 NOT NULL
                    )
                    """);
            // 与生产一致的分区表，验证趋势 raw SQL 在分区上可直接执行。
            statement.execute("""
                    CREATE TABLE collector_sample_raw (
                        channel_id bigint NOT NULL, device_id bigint NOT NULL,
                        collect_time timestamp(3) NOT NULL,
                        value double precision, quality smallint NOT NULL,
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
            createAggregateTable(statement, "collector_aggregate_second", "bucket_time", false);
            createAggregateTable(statement, "collector_aggregate_minute", "bucket_time", false);
            createAggregateTable(statement, "collector_aggregate_hour", "bucket_time", false);
            createAggregateTable(statement, "collector_aggregate_day", "bucket_date", true);
        }

        dataSource = new UnpooledDataSource("org.postgresql.Driver", URL, USER, PASSWORD);
        Environment environment = new Environment("trend-test",
                new SpringManagedTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(CollectorTrendMapper.class);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        // 使用单连接会话：叠加查询的 SET LOCAL statement_timeout 与抽样 SQL 必须落在同一连接上，
        // 与生产环境 @Transactional 绑定连接的行为一致。
        sqlSession = factory.openSession(false);
        // SpringManagedTransaction 不会应用 openSession(false) 的 autoCommit 设置，
        // 需要显式关闭自动提交：让 SET LOCAL statement_timeout 与叠加查询处于同一事务，
        // 与生产环境 @Transactional 绑定连接的行为一致（否则 SET LOCAL 在事务外不生效）。
        sqlSession.getConnection().setAutoCommit(false);
        trendMapper = sqlSession.getMapper(CollectorTrendMapper.class);
        channelMapper = mock(CollectorChannelMapper.class);
        deviceMapper = mock(CollectorDeviceMapper.class);
        trendService = newCollectorTrendService(10_000);
    }

    @After
    public void tearDownTrendPg() {
        if (sqlSession != null) {
            sqlSession.close();
            sqlSession = null;
        }
    }

    /** 构造被测服务；子类/用例可注入更短的叠加超时以真实触发 statement_timeout 降级。 */
    protected CollectorTrendServiceImpl newCollectorTrendService(int rawOverlayTimeoutMillis) {
        return new CollectorTrendServiceImpl(channelMapper, trendMapper, deviceMapper,
                rawOverlayTimeoutMillis);
    }

    /** 当前测试会话持有的连接，用于验证 statement_timeout 机制真实生效。 */
    protected Connection sessionConnection() {
        return sqlSession.getConnection();
    }

    private void createAggregateTable(Statement statement, String table, String bucketColumn,
                                      boolean dateBucket) throws SQLException {
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
                """.formatted(table, bucketColumn, dateBucket ? "date" : "timestamp(3)", bucketColumn));
    }

    private static boolean safeTestDatabase(String jdbcUrl) {
        String withoutParams = jdbcUrl.split("\\?", 2)[0];
        int slash = withoutParams.lastIndexOf('/');
        return slash >= 0 && withoutParams.substring(slash + 1).startsWith("collector_aggregate_");
    }

    protected void stubChannels(CollectorChannelDO... channels) {
        when(channelMapper.selectBatchIds(any())).thenReturn(Arrays.asList(channels));
    }

    protected CollectorChannelDO channel(long id, long deviceId, String dataProfile) {
        CollectorChannelDO channel = new CollectorChannelDO();
        channel.setId(id);
        channel.setDeviceId(deviceId);
        channel.setChannelName("通道" + id);
        channel.setUnit("u" + id);
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

    /** 批量插入等间隔 raw 行，用于验证原始点抽样上限。 */
    protected void insertRawSeries(long channelId, long deviceId, LocalDateTime start,
                                   int count, double startValue) throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO collector_sample_raw (channel_id, device_id, collect_time, value, quality)
                     VALUES (?, ?, ?, ?, 0)
                     """)) {
            for (int i = 0; i < count; i++) {
                statement.setLong(1, channelId);
                statement.setLong(2, deviceId);
                statement.setTimestamp(3, Timestamp.valueOf(start.plusSeconds(i)));
                statement.setDouble(4, startValue + i);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /** 批量插入等间隔聚合桶，值随序号递增，用于验证非桶边界对齐下的点数上限。 */
    protected void insertAggregateSeries(String table, long channelId, long deviceId,
                                         LocalDateTime start, int count, int stepSeconds,
                                         double startValue) throws SQLException {
        boolean day = "collector_aggregate_day".equals(table);
        String bucketColumn = day ? "bucket_date" : "bucket_time";
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO %s (channel_id, device_id, %s, sample_count, good_count,
                         valid_count, bad_count, value_avg, value_min, value_max, value_sum)
                     VALUES (?, ?, ?, 1, 1, 1, 0, ?, ?, ?, ?)
                     """.formatted(table, bucketColumn))) {
            for (int i = 0; i < count; i++) {
                LocalDateTime bucket = start.plusSeconds((long) i * stepSeconds);
                statement.setLong(1, channelId);
                statement.setLong(2, deviceId);
                if (day) {
                    statement.setObject(3, bucket.toLocalDate());
                } else {
                    statement.setTimestamp(3, Timestamp.valueOf(bucket));
                }
                statement.setDouble(4, startValue + i);
                statement.setDouble(5, startValue + i);
                statement.setDouble(6, startValue + i);
                statement.setDouble(7, startValue + i);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /** 写入一行聚合桶；avg 由 value_sum / valid_count 派生，source_bucket_count 不参与趋势查询。 */
    protected void insertAggregate(String table, long channelId, long deviceId, LocalDateTime bucketTime,
                                   int sampleCount, int validCount, Double valueSum,
                                   Double valueMin, Double valueMax) throws SQLException {
        boolean day = "collector_aggregate_day".equals(table);
        String bucketColumn = day ? "bucket_date" : "bucket_time";
        Double valueAvg = valueSum == null || validCount == 0 ? null : valueSum / validCount;
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO %s (channel_id, device_id, %s, sample_count, good_count,
                         valid_count, bad_count, value_avg, value_min, value_max, value_sum)
                     VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)
                     """.formatted(table, bucketColumn))) {
            statement.setLong(1, channelId);
            statement.setLong(2, deviceId);
            if (day) {
                statement.setObject(3, bucketTime.toLocalDate());
            } else {
                statement.setTimestamp(3, Timestamp.valueOf(bucketTime));
            }
            statement.setInt(4, sampleCount);
            statement.setInt(5, validCount);
            statement.setInt(6, validCount);
            if (valueAvg == null) {
                statement.setNull(7, java.sql.Types.DOUBLE);
                statement.setNull(8, java.sql.Types.DOUBLE);
                statement.setNull(9, java.sql.Types.DOUBLE);
                statement.setNull(10, java.sql.Types.DOUBLE);
            } else {
                statement.setDouble(7, valueAvg);
                statement.setDouble(8, valueMin);
                statement.setDouble(9, valueMax);
                statement.setDouble(10, valueSum);
            }
            statement.executeUpdate();
        }
    }

    protected List<CollectorTrendBucketRow> selectBucketsBySql(String sql) throws SQLException {
        List<CollectorTrendBucketRow> rows = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                CollectorTrendBucketRow row = new CollectorTrendBucketRow();
                row.setChannelId(rs.getLong("channel_id"));
                row.setBucketTime(rs.getObject("bucket_time", LocalDateTime.class));
                row.setAvg((Double) rs.getObject("avg"));
                row.setMin((Double) rs.getObject("min"));
                row.setMax((Double) rs.getObject("max"));
                row.setCount(rs.getInt("count"));
                rows.add(row);
            }
        }
        return rows;
    }

    protected List<CollectorTrendRawPointRow> selectRawPointsBySql(String sql) throws SQLException {
        List<CollectorTrendRawPointRow> rows = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                CollectorTrendRawPointRow row = new CollectorTrendRawPointRow();
                row.setChannelId(rs.getLong("channel_id"));
                row.setBucketTime(rs.getObject("bucket_time", LocalDateTime.class));
                row.setValue((Double) rs.getObject("value"));
                rows.add(row);
            }
        }
        return rows;
    }

    protected int countRows(String table) throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    protected void executeSql(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

}
