package cn.iocoder.yudao.module.collector.service.health;

import cn.iocoder.yudao.module.collector.dal.dataobject.health.CollectorHealthAggregateAbnormalRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.health.CollectorHealthAggregateSummaryRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.health.CollectorHealthAlarmRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.health.CollectorHealthAlarmSummaryRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.health.CollectorHealthDeviceRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.health.CollectorHealthHeartbeatRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.health.CollectorHealthPartitionRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.health.CollectorHealthRawPolicyRow;
import cn.iocoder.yudao.module.collector.dal.mysql.health.CollectorHealthMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 采集运行详情 Mapper 的真实 PostgreSQL 校验。
 *
 * <p>未设置 {@code COLLECTOR_TEST_PG_URL} 时整体跳过；建表属于破坏性操作，还要求
 * {@code COLLECTOR_TEST_PG_ALLOW_DESTRUCTIVE=true} 且库名以 {@code collector_health_} 开头，
 * 避免误连正式库。这里不依赖任何现有业务表，全部按 01_schema.sql 的列名自建最小结构。</p>
 */
public class CollectorHealthMapperPgTest {

    private static final String URL = System.getenv("COLLECTOR_TEST_PG_URL");
    private static final String USER = System.getenv("COLLECTOR_TEST_PG_USER");
    private static final String PASSWORD = System.getenv("COLLECTOR_TEST_PG_PASSWORD");
    private static final String ALLOW_DESTRUCTIVE = System.getenv("COLLECTOR_TEST_PG_ALLOW_DESTRUCTIVE");
    private static final String TEST_DATABASE_PREFIX = "collector_health_";

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 11, 23, 10, 0);

    private CollectorHealthMapper mapper;
    private UnpooledDataSource dataSource;
    private SqlSession sqlSession;

    @Before
    public void setUpHealthPg() throws Exception {
        Assume.assumeTrue("未设置 COLLECTOR_TEST_PG_URL，跳过临时 PostgreSQL 运行详情测试",
                URL != null && !URL.isBlank());
        Assume.assumeTrue("未设置 COLLECTOR_TEST_PG_ALLOW_DESTRUCTIVE=true，跳过会重建表的运行详情测试",
                "true".equalsIgnoreCase(ALLOW_DESTRUCTIVE));
        Assume.assumeTrue("数据库名必须以 " + TEST_DATABASE_PREFIX + " 开头，拒绝在正式库执行破坏性测试",
                safeTestDatabase(URL));
        Class.forName("org.postgresql.Driver");
        recreateSchema();

        dataSource = new UnpooledDataSource("org.postgresql.Driver", URL, USER, PASSWORD);
        Environment environment = new Environment("health-test", new SpringManagedTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(CollectorHealthMapper.class);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        sqlSession = factory.openSession(true);
        mapper = sqlSession.getMapper(CollectorHealthMapper.class);
    }

    @Test
    public void device_withoutStatusRow_keepsNullsInsteadOfFakeOffline() throws SQLException {
        execute("INSERT INTO collector_device (id, device_name, poll_interval_ms, deleted) "
                + "VALUES (7, '测试设备', 5000, 0)");

        CollectorHealthDeviceRow row = mapper.selectDevice(7L);

        assertEquals("测试设备", row.getName());
        assertEquals(Integer.valueOf(5000), row.getPollIntervalMs());
        assertNull("无状态行时 online 必须为 null", row.getOnline());
        assertNull(row.getConsecutiveFailures());
        assertNull(row.getLastLatencyMs());
        assertNull("无状态行时 lastPollAt 必须为 null，不得回落到设备 update_time", row.getLastPollAt());
        assertNull("已删除设备不可见", mapper.selectDevice(8L));
    }

    @Test
    public void device_withStatusRow_mapsOnlineAndTimestamps() throws SQLException {
        execute("INSERT INTO collector_device (id, device_name, poll_interval_ms, deleted) "
                + "VALUES (7, '测试设备', 5000, 0)");
        execute("INSERT INTO collector_device_status (device_id, online, last_success_at, last_error, "
                + "consecutive_failures, last_latency_ms, update_time) "
                + "VALUES (7, 1, TIMESTAMP '2026-11-23 09:58:00', 'timeout', 2, 120, "
                + "TIMESTAMP '2026-11-23 09:59:00')");

        CollectorHealthDeviceRow row = mapper.selectDevice(7L);

        assertEquals(Integer.valueOf(1), row.getOnline());
        assertEquals(LocalDateTime.of(2026, 11, 23, 9, 58), row.getLastSuccessAt());
        assertEquals("timeout", row.getLastError());
        assertEquals(Integer.valueOf(2), row.getConsecutiveFailures());
        assertEquals(Integer.valueOf(120), row.getLastLatencyMs());
        assertEquals(LocalDateTime.of(2026, 11, 23, 9, 59), row.getLastPollAt());
    }

    @Test
    public void latestHeartbeat_prefersNewestByTime() throws SQLException {
        assertNull("空表时无心跳必须返回 null", mapper.selectLatestHeartbeat());
        execute("INSERT INTO collector_heartbeat (id, instance_id, heartbeat_time, status, write_lag_ms, "
                + "device_total, device_online, collector_version) VALUES "
                + "(1, 'old', TIMESTAMP '2026-11-23 09:00:00', 'running', 5, 2, 2, '0.9.0'),"
                + "(2, 'new', TIMESTAMP '2026-11-23 09:59:00', 'degraded', 12, 2, 1, '1.0.0')");

        CollectorHealthHeartbeatRow row = mapper.selectLatestHeartbeat();

        assertEquals("new", row.getInstanceId());
        assertEquals("degraded", row.getStatus());
        assertEquals(Integer.valueOf(12), row.getWriteLagMs());
        assertEquals(Integer.valueOf(2), row.getDeviceTotal());
        assertEquals(Integer.valueOf(1), row.getDeviceOnline());
        assertEquals("1.0.0", row.getCollectorVersion());
    }

    @Test
    public void alarmSummary_excludesLevelZero() throws SQLException {
        CollectorHealthAlarmSummaryRow empty = mapper.selectAlarmSummary(7L);
        assertEquals(Integer.valueOf(0), empty.getActiveCount());
        assertEquals(Integer.valueOf(0), empty.getHighestLevel());

        execute("INSERT INTO collector_alarm_state (channel_id, device_id, sensor_id, level, threshold_type, "
                + "since, last_value, last_threshold, last_sample_time, updated_time) VALUES "
                + "(101, 7, 10, 0, NULL, NULL, 20, NULL, TIMESTAMP '2026-11-23 09:00:00', "
                + "TIMESTAMP '2026-11-23 09:00:00'),"
                + "(102, 7, 10, 2, 'threshold_high_warning', TIMESTAMP '2026-11-23 08:00:00', 85, 80, "
                + "TIMESTAMP '2026-11-23 09:59:00', TIMESTAMP '2026-11-23 09:59:00'),"
                + "(103, 7, 10, 3, 'threshold_high_error', TIMESTAMP '2026-11-23 07:00:00', 95, 90, "
                + "TIMESTAMP '2026-11-23 09:59:30', TIMESTAMP '2026-11-23 09:59:30'),"
                + "(104, 8, 11, 3, 'threshold_high_error', TIMESTAMP '2026-11-23 07:00:00', 95, 90, "
                + "TIMESTAMP '2026-11-23 09:59:30', TIMESTAMP '2026-11-23 09:59:30')");

        CollectorHealthAlarmSummaryRow summary = mapper.selectAlarmSummary(7L);

        assertEquals("level = 0 不算活动报警", Integer.valueOf(2), summary.getActiveCount());
        assertEquals(Integer.valueOf(3), summary.getHighestLevel());
    }

    @Test
    public void activeAlarms_joinNamesWithoutPerChannelQuery() throws SQLException {
        execute("INSERT INTO collector_device (id, device_name, poll_interval_ms, deleted) "
                + "VALUES (7, '测试设备', 5000, 0)");
        execute("INSERT INTO collector_sensor (id, sensor_name, deleted) VALUES (10, 'CS1000', 0)");
        execute("INSERT INTO collector_channel (id, channel_name, deleted) VALUES "
                + "(101, '温度', 0), (102, '流量', 0)");
        execute("INSERT INTO collector_alarm_state (channel_id, device_id, sensor_id, level, threshold_type, "
                + "since, last_value, last_threshold, last_sample_time, updated_time) VALUES "
                + "(101, 7, 10, 2, 'threshold_high_warning', TIMESTAMP '2026-11-23 08:00:00', 85, 80, "
                + "TIMESTAMP '2026-11-23 09:00:00', TIMESTAMP '2026-11-23 09:10:00'),"
                + "(102, 7, 10, 3, 'threshold_high_error', TIMESTAMP '2026-11-23 07:00:00', 95, 90, "
                + "TIMESTAMP '2026-11-23 09:30:00', TIMESTAMP '2026-11-23 09:30:00'),"
                + "(103, 7, 10, 0, NULL, NULL, 20, NULL, TIMESTAMP '2026-11-23 09:40:00', "
                + "TIMESTAMP '2026-11-23 09:40:00')");

        List<CollectorHealthAlarmRow> rows = mapper.selectActiveAlarms(7L);

        assertEquals(2, rows.size());
        assertEquals("级别降序，level=0 不出现", Integer.valueOf(3), rows.get(0).getLevel());
        assertEquals("流量", rows.get(0).getChannelName());
        assertEquals("CS1000", rows.get(0).getSensorName());
        assertEquals("测试设备", rows.get(0).getDeviceName());
        assertEquals(Integer.valueOf(2), rows.get(1).getLevel());
        assertEquals("温度", rows.get(1).getChannelName());
        assertEquals(80D, rows.get(1).getLastThreshold(), 0D);
    }

    @Test
    public void aggregateSummary_groupsByTypeInSingleQuery() throws SQLException {
        assertTrue("无水位行时分组结果为空", mapper.selectAggregateSummary(7L).isEmpty());
        execute("INSERT INTO collector_aggregate_watermark (agg_type, channel_id, device_id, watermark_time, "
                + "status, retry_count, last_error, last_success_time, update_time) VALUES "
                + "('second', 11, 7, TIMESTAMP '2026-11-23 09:59:59', 0, 0, NULL, "
                + "TIMESTAMP '2026-11-23 10:00:00', TIMESTAMP '2026-11-23 10:00:00'),"
                + "('second', 12, 7, TIMESTAMP '2026-11-23 09:58:00', 2, 0, NULL, "
                + "TIMESTAMP '2026-11-23 09:59:00', TIMESTAMP '2026-11-23 09:59:00'),"
                + "('minute', 11, 7, NULL, 0, 0, NULL, NULL, TIMESTAMP '2026-11-23 09:00:00'),"
                + "('hour', 99, 8, TIMESTAMP '2026-11-23 09:00:00', 2, 0, NULL, NULL, "
                + "TIMESTAMP '2026-11-23 09:00:00')");

        List<CollectorHealthAggregateSummaryRow> rows = mapper.selectAggregateSummary(7L);

        assertEquals("只统计当前设备，按 agg_type 分组", 2, rows.size());
        CollectorHealthAggregateSummaryRow second = rows.get(0).getAggType().equals("second")
                ? rows.get(0) : rows.get(1);
        assertEquals(Integer.valueOf(2), second.getRowCount());
        assertEquals(Integer.valueOf(1), second.getAbnormalCount());
        assertEquals(Integer.valueOf(0), second.getMaxRetryCount());
        assertEquals(LocalDateTime.of(2026, 11, 23, 9, 59, 59), second.getMaxWatermarkTime());
        assertEquals(LocalDateTime.of(2026, 11, 23, 9, 58), second.getMinWatermarkTime());
        assertEquals(LocalDateTime.of(2026, 11, 23, 10, 0), second.getLastSuccessTime());
    }

    @Test
    public void aggregateAbnormal_onlyNonZeroStatus() throws SQLException {
        assertTrue("无异常行时列表为空", mapper.selectAggregateAbnormal(7L).isEmpty());
        execute("INSERT INTO collector_channel (id, channel_name, deleted) VALUES (11, '温度', 0)");
        execute("INSERT INTO collector_aggregate_watermark (agg_type, channel_id, device_id, watermark_time, "
                + "status, retry_count, last_error, last_success_time, update_time) VALUES "
                + "('second', 11, 7, TIMESTAMP '2026-11-23 09:59:59', 0, 0, NULL, "
                + "TIMESTAMP '2026-11-23 10:00:00', TIMESTAMP '2026-11-23 10:00:00'),"
                + "('hour', 11, 7, TIMESTAMP '2026-11-23 09:00:00', 2, 0, NULL, "
                + "TIMESTAMP '2026-11-23 09:30:00', TIMESTAMP '2026-11-23 09:30:00')");

        List<CollectorHealthAggregateAbnormalRow> rows = mapper.selectAggregateAbnormal(7L);

        assertEquals("status = 0 不进入异常列表", 1, rows.size());
        assertEquals("hour", rows.get(0).getAggType());
        assertEquals("温度", rows.get(0).getChannelName());
        assertEquals("预留的 status 原样返回，不推断作业状态", Integer.valueOf(2), rows.get(0).getStatus());
        assertEquals("retry_count 为预留字段，原样返回 0", Integer.valueOf(0), rows.get(0).getRetryCount());
        assertNull("last_error 未写入时必须为 null，不得补文案", rows.get(0).getLastError());
    }

    @Test
    public void rawPolicyRows_readsOnlyRawPolicy() throws SQLException {
        assertTrue("策略缺失时返回空列表，由 Service 判定异常状态", mapper.selectRawPolicyRows().isEmpty());
        execute("INSERT INTO collector_retention_policy (id, data_class, partition_lead_days, deleted) VALUES "
                + "(1, 'sample_raw', 3, 0), (2, 'alarm', 30, 0), (3, 'sample_raw', 9, 1)");

        List<CollectorHealthRawPolicyRow> rows = mapper.selectRawPolicyRows();

        assertEquals("只读未删除的 sample_raw 策略", 1, rows.size());
        assertEquals(Long.valueOf(1L), rows.get(0).getId());
        assertEquals(Integer.valueOf(3), rows.get(0).getPartitionLeadDays());
    }

    @Test
    public void rawPartitions_listsOnlyDayPartitionsFromCatalog() {
        List<CollectorHealthPartitionRow> rows = mapper.selectRawPartitions();

        assertEquals("DEFAULT 分区不匹配日分区命名，不应出现", 1, rows.size());
        CollectorHealthPartitionRow row = rows.get(0);
        assertEquals("collector_sample_raw_p20261123", row.getName());
        assertTrue(row.getPartitionFlag());
        assertEquals("FOR VALUES FROM ('2026-11-23 00:00:00') TO ('2026-11-24 00:00:00')", row.getBound());
    }

    private void recreateSchema() throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    DO $$
                    DECLARE r record;
                    BEGIN
                        FOR r IN
                            SELECT c.relname FROM pg_class c
                            JOIN pg_namespace n ON n.oid = c.relnamespace
                            WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'v')
                              AND (c.relname LIKE 'collector_health%' OR c.relname LIKE 'collector_sample_raw%'
                                   OR c.relname IN ('collector_device', 'collector_device_status',
                                       'collector_heartbeat', 'collector_alarm_state', 'collector_sensor',
                                       'collector_channel', 'collector_aggregate_watermark',
                                       'collector_retention_policy'))
                        LOOP
                            EXECUTE format('DROP TABLE IF EXISTS public.%I CASCADE', r.relname);
                        END LOOP;
                    END $$
                    """);
            statement.execute("""
                    CREATE TABLE public.collector_device (
                        id bigint PRIMARY KEY,
                        device_name varchar(100) NOT NULL,
                        poll_interval_ms integer,
                        deleted smallint DEFAULT 0 NOT NULL,
                        update_time timestamp(3) DEFAULT CURRENT_TIMESTAMP NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE public.collector_device_status (
                        device_id bigint PRIMARY KEY,
                        online smallint DEFAULT 0 NOT NULL,
                        last_online_at timestamp(3),
                        last_offline_at timestamp(3),
                        last_success_at timestamp(3),
                        last_error varchar(500),
                        consecutive_failures integer DEFAULT 0 NOT NULL,
                        last_latency_ms integer,
                        update_time timestamp(3) DEFAULT CURRENT_TIMESTAMP NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE public.collector_heartbeat (
                        id bigint PRIMARY KEY,
                        instance_id varchar(64) NOT NULL,
                        heartbeat_time timestamp(3) NOT NULL,
                        status varchar(16) DEFAULT 'running' NOT NULL,
                        write_lag_ms integer,
                        device_total integer DEFAULT 0 NOT NULL,
                        device_online integer DEFAULT 0 NOT NULL,
                        collector_version varchar(32)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE public.collector_sensor (
                        id bigint PRIMARY KEY,
                        sensor_name varchar(100) NOT NULL,
                        deleted smallint DEFAULT 0 NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE public.collector_channel (
                        id bigint PRIMARY KEY,
                        channel_name varchar(100) NOT NULL,
                        deleted smallint DEFAULT 0 NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE public.collector_alarm_state (
                        channel_id bigint PRIMARY KEY,
                        device_id bigint NOT NULL,
                        sensor_id bigint NOT NULL,
                        level smallint DEFAULT 0 NOT NULL,
                        threshold_type varchar(32),
                        since timestamp(3),
                        last_value double precision,
                        last_threshold double precision,
                        last_sample_time timestamp(3),
                        updated_time timestamp(3) DEFAULT CURRENT_TIMESTAMP NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE public.collector_aggregate_watermark (
                        agg_type varchar(8) NOT NULL,
                        channel_id bigint NOT NULL,
                        device_id bigint NOT NULL,
                        watermark_time timestamp(3),
                        backfill_from timestamp(3),
                        backfill_to timestamp(3),
                        status smallint DEFAULT 0 NOT NULL,
                        retry_count integer DEFAULT 0 NOT NULL,
                        last_error varchar(500),
                        locked_until timestamp(3),
                        last_success_time timestamp(3),
                        update_time timestamp(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        PRIMARY KEY (agg_type, channel_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE public.collector_retention_policy (
                        id bigint PRIMARY KEY,
                        data_class varchar(32) NOT NULL,
                        partition_lead_days integer DEFAULT 3 NOT NULL,
                        deleted smallint DEFAULT 0 NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE public.collector_sample_raw (
                        sample_id bigserial NOT NULL,
                        channel_id bigint NOT NULL,
                        device_id bigint NOT NULL,
                        collect_time timestamp(3) NOT NULL,
                        value double precision,
                        quality smallint DEFAULT 0 NOT NULL,
                        PRIMARY KEY (channel_id, collect_time)
                    ) PARTITION BY RANGE (collect_time)
                    """);
            statement.execute("""
                    CREATE TABLE public.collector_sample_raw_p20261123
                        PARTITION OF public.collector_sample_raw
                        FOR VALUES FROM ('2026-11-23 00:00:00') TO ('2026-11-24 00:00:00')
                    """);
            statement.execute("""
                    CREATE TABLE public.collector_sample_raw_default
                        PARTITION OF public.collector_sample_raw DEFAULT
                    """);
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static boolean safeTestDatabase(String jdbcUrl) {
        String withoutParams = jdbcUrl.split("\\?", 2)[0];
        int slash = withoutParams.lastIndexOf('/');
        return slash >= 0 && withoutParams.substring(slash + 1).startsWith(TEST_DATABASE_PREFIX);
    }

}
